package com.iris.domain.usecase.sms

import androidx.room.withTransaction
import arrow.core.Either
import arrow.core.flatten
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.iris.base.time.TimeProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.ProcessedMessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Every way a dismiss can fail. Throwing here would crash a swipe gesture. */
sealed interface DismissCaptureError {
    data class CapturedItemGone(val id: CapturedTransactionId) : DismissCaptureError
    data class Persistence(val cause: String) : DismissCaptureError
}

/**
 * Throws a captured item away and remembers that it was thrown away (FR-025).
 *
 * The remembering is the point. Deleting the row alone would let the identical message resurrect
 * the item the next time it is seen — over a re-delivered broadcast, or through the historical
 * import — and the user would have to dismiss the same thing twice.
 *
 * Nothing is inserted into the ledger. What happens to a linked charge is the user's call
 * (FR-018): discarding it with the payment is the default, and keeping it detaches it into a
 * standalone transaction cost rather than orphaning it behind a parent that no longer exists.
 */
@Singleton
class DismissCapturedTransactionUseCase @Inject constructor(
    private val db: IrisRoomDatabase,
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val processedMessageRepository: ProcessedMessageRepository,
    private val senderRepository: FinancialSenderRepository,
    private val timeProvider: TimeProvider,
) {

    /**
     * @param alsoRemoveFee discards the linked transaction cost together with the payment, which
     *   is what the review screen offers by default. `false` keeps the charge and detaches it, so
     *   the two can neither drift apart nor leave a fee pointing at nothing (FR-018).
     */
    suspend fun dismiss(
        id: CapturedTransactionId,
        alsoRemoveFee: Boolean = true,
    ): Either<DismissCaptureError, Unit> = Either
        .catch { db.withTransaction { discard(id, alsoRemoveFee) } }
        .mapLeft<DismissCaptureError> {
            DismissCaptureError.Persistence(it::class.simpleName ?: "unknown")
        }
        .flatten()

    private suspend fun discard(
        id: CapturedTransactionId,
        alsoRemoveFee: Boolean,
    ): Either<DismissCaptureError, Unit> = either {
        val entry = capturedTransactionRepository.findById(id)
        ensureNotNull(entry) { DismissCaptureError.CapturedItemGone(id) }

        val captured = entry.principal
        fingerprintOf(captured.sender, captured.reference?.value)?.let { fingerprint ->
            processedMessageRepository.record(
                ProcessedMessage(
                    fingerprint = fingerprint,
                    sender = captured.sender,
                    outcome = ProcessedOutcome.Dismissed,
                    processedAt = timeProvider.utcNow(),
                ),
            )
        }

        val fee = entry.fee
        if (alsoRemoveFee || fee == null) {
            capturedTransactionRepository.deleteEntry(id)
        } else {
            capturedTransactionRepository.save(fee.detached())
            capturedTransactionRepository.deleteById(id)
        }
    }

    /**
     * Cuts the link to the principal about to disappear. The tax detail travels with the charge,
     * because it describes the charge and not the payment (FR-019).
     */
    private fun CapturedTransaction.detached(): CapturedTransaction {
        val tax = (kind as? CapturedKind.Fee)?.tax
        return copy(kind = CapturedKind.Fee(parent = null, tax = tax))
    }

    /**
     * Rebuilds the reference-shaped fingerprint the parser produced, so the outcome can be
     * upgraded from `CAPTURED` to `DISMISSED` on the row already there.
     *
     * A message with no reference has a digest-shaped fingerprint that cannot be reconstructed
     * from the captured row — it needs the body, which is deliberately never stored (FR-006). That
     * is not a hole: the `CAPTURED` row written at capture time still blocks re-delivery, so
     * "dismissed items stay dismissed" holds either way. Only the recorded *reason* differs.
     */
    private suspend fun fingerprintOf(
        sender: SenderId,
        reference: String?,
    ): MessageFingerprint? {
        if (reference == null) return null
        val ruleSet = senderRepository.findAll().firstOrNull { it.id == sender }?.ruleSet
        return ruleSet?.let { MessageFingerprint.from("${it.value}:$reference").getOrNull() }
    }
}
