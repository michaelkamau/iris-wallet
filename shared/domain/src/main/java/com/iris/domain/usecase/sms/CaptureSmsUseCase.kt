package com.iris.domain.usecase.sms

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.iris.base.time.TimeProvider
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.ProcessedMessageRepository
import com.iris.sms.parser.MessageFingerprints
import com.iris.sms.parser.ParsedAmountLine
import com.iris.sms.parser.ParsedFee
import com.iris.sms.parser.ParsedMessage
import com.iris.sms.parser.RawSmsMessage
import com.iris.sms.parser.RequiredField
import com.iris.sms.parser.SmsParseError
import com.iris.sms.parser.SmsParser
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every way capture can decline to produce a pending item.
 *
 * All of these are ordinary values: the capture path runs inside a broadcast receiver, where a
 * thrown exception is an app crash the user did not ask for (FR-013).
 */
sealed interface SmsCaptureError {
    /** The master switch is off — the message was never eligible (FR-001). */
    data object CaptureDisabled : SmsCaptureError

    /** The switch is on but `RECEIVE_SMS` is not granted (FR-005). */
    data object PermissionMissing : SmsCaptureError

    /** The sender is not one the user marked as financial (FR-004). */
    data class SenderNotConfigured(val sender: SenderId) : SmsCaptureError

    /** The sender is known but individually switched off (FR-003). */
    data class SenderDisabled(val sender: SenderId) : SmsCaptureError

    /** This exact message was already handled — captured, ignored or dismissed (FR-025, FR-028). */
    data class AlreadyProcessed(val fingerprint: MessageFingerprint) : SmsCaptureError

    /** The message is not a transaction, or a rule matched and then could not be trusted. */
    data class ParseFailure(val cause: SmsParseError) : SmsCaptureError

    /** Something below us failed to write. Carries no message content (FR-006). */
    data class Persistence(val cause: String) : SmsCaptureError
}

/**
 * Turns one delivered message into one pending item awaiting review.
 *
 * This is the single funnel for both live broadcasts and the historical import (FR-030), which is
 * what makes "an imported message that already arrived live is not captured twice" true by
 * construction rather than by a second, parallel dedupe (SC-003).
 *
 * Nothing here touches the ledger. A captured item is inert until [ConfirmCapturedTransactionUseCase]
 * commits it (FR-021, FR-021a).
 */
@Singleton
class CaptureSmsUseCase @Inject constructor(
    private val gate: SmsCaptureGate,
    private val senderRepository: FinancialSenderRepository,
    private val parser: SmsParser,
    private val processedMessageRepository: ProcessedMessageRepository,
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val detectDuplicate: DetectDuplicateTransactionUseCase,
    private val timeProvider: TimeProvider,
) {

    suspend fun capture(
        message: RawSmsMessage,
        origin: CaptureOrigin = CaptureOrigin.LiveBroadcast,
    ): Either<SmsCaptureError, CapturedEntry> = either {
        ensure(gate.isEnabled()) { SmsCaptureError.CaptureDisabled }
        ensure(gate.hasReceivePermission()) { SmsCaptureError.PermissionMissing }

        val sender = resolveSender(message.sender).bind()
        val parsed = parse(message).bind()

        val fingerprint = MessageFingerprints.of(message, parsed)
        ensure(!processedMessageRepository.isProcessed(fingerprint)) {
            SmsCaptureError.AlreadyProcessed(fingerprint)
        }

        val entry = entryOf(sender, parsed, origin).bind()
        persist(entry, sender, fingerprint).bind()
        entry
    }

    /**
     * A sender the user never configured and a sender they switched off are told apart here
     * rather than in the repository, because `findEnabled` answering `null` for both is exactly
     * the right shape for the capture path and the wrong shape for a diagnostic.
     */
    private suspend fun resolveSender(
        sender: SenderId,
    ): Either<SmsCaptureError, FinancialSender> = either {
        val enabled = senderRepository.findEnabled(sender)
        if (enabled != null) return@either enabled

        val known = senderRepository.findAll().any { it.id == sender }
        raise(
            if (known) {
                SmsCaptureError.SenderDisabled(sender)
            } else {
                SmsCaptureError.SenderNotConfigured(sender)
            },
        )
    }

    /**
     * A message that is not a transaction is remembered as `Ignored` before the failure is
     * returned, so the identical text arriving again — over the broadcast and then out of the
     * inbox — costs one regex pass rather than a second full attempt (FR-028).
     */
    private suspend fun parse(
        message: RawSmsMessage,
    ): Either<SmsCaptureError, ParsedMessage> = either {
        parser.parse(message)
            .onLeft { remember(message, ProcessedOutcome.Ignored) }
            .mapLeft(SmsCaptureError::ParseFailure)
            .bind()
    }

    /** Records the reference-free fingerprint of a message we will never capture. */
    private suspend fun remember(message: RawSmsMessage, outcome: ProcessedOutcome) {
        val fingerprint = MessageFingerprints.of(message, parsed = null)
        if (processedMessageRepository.isProcessed(fingerprint)) return
        processedMessageRepository.record(
            ProcessedMessage(
                fingerprint = fingerprint,
                sender = message.sender,
                outcome = outcome,
                processedAt = timeProvider.utcNow(),
            ),
        )
    }

    /**
     * The account is copied straight from the sender mapping, including when it is absent.
     * Substituting a default here is precisely the silent mis-filing FR-027a forbids: an item with
     * no account surfaces as [com.iris.data.model.sms.ReviewStatus.NeedsAccount] instead.
     *
     * A reported transaction cost becomes a **second** captured row rather than a field on the
     * first (FR-016). It carries the same time, payee and reference as the principal, so the two
     * read as one event, and it points at the principal through [CapturedKind.Fee.parent] so
     * discarding one can take the other with it (FR-018). The tax component rides along on that
     * same `kind`, which is the only place it is representable — it can never become a third
     * transaction (FR-019).
     *
     * A message that reported only a charge has no principal to point at, so the fee is the head
     * of the entry with `parent = null` (FR-020). A cost of `0.00` reaches none of this: the
     * parser could not build a `PositiveDouble` from it, so `parsed.fee` is simply null
     * (Acceptance 2.3).
     */
    private suspend fun entryOf(
        sender: FinancialSender,
        parsed: ParsedMessage,
        origin: CaptureOrigin,
    ): Either<SmsCaptureError, CapturedEntry> = either {
        val principal = parsed.principal
        val fee = parsed.fee
        when {
            principal != null -> withPrincipal(sender, parsed, principal, fee, origin)
            fee != null -> standaloneFee(sender, parsed, fee, origin)
            else -> raise(
                SmsCaptureError.ParseFailure(
                    SmsParseError.MissingField(parsed.rule, RequiredField.Amount),
                ),
            )
        }
    }

    private suspend fun withPrincipal(
        sender: FinancialSender,
        parsed: ParsedMessage,
        principal: ParsedAmountLine,
        fee: ParsedFee?,
        origin: CaptureOrigin,
    ): CapturedEntry {
        val captured = row(
            sender = sender,
            parsed = parsed,
            amount = principal.amount,
            asset = principal.asset,
            kind = CapturedKind.Principal(principal.direction),
            origin = origin,
            duplicateOf = duplicateOf(sender, principal.amount, principal.asset, parsed.time),
        )
        return CapturedEntry(
            principal = captured,
            fee = fee?.let {
                row(
                    sender = sender,
                    parsed = parsed,
                    amount = it.amount,
                    asset = it.asset,
                    // A fee is never deduplicated on its own: it is only ever as duplicated as
                    // the principal it belongs to, which is already flagged above.
                    kind = CapturedKind.Fee(parent = captured.id, tax = it.tax),
                    origin = origin,
                )
            },
        )
    }

    /**
     * Nothing but a charge in the message: it becomes the reviewable row itself, unattached.
     *
     * It is checked against the ledger like any other head row — a bank charge the user already
     * entered by hand is exactly the kind of thing they should be warned about (FR-024).
     */
    private suspend fun standaloneFee(
        sender: FinancialSender,
        parsed: ParsedMessage,
        fee: ParsedFee,
        origin: CaptureOrigin,
    ) = CapturedEntry(
        principal = row(
            sender = sender,
            parsed = parsed,
            amount = fee.amount,
            asset = fee.asset,
            kind = CapturedKind.Fee(parent = null, tax = fee.tax),
            origin = origin,
            duplicateOf = duplicateOf(sender, fee.amount, fee.asset, parsed.time),
        ),
        fee = null,
    )

    /**
     * A sender with no account mapping has nothing to compare against, so the item goes to review
     * unflagged rather than compared against every account the user has.
     */
    private suspend fun duplicateOf(
        sender: FinancialSender,
        amount: PositiveDouble,
        asset: AssetCode,
        time: Instant,
    ): TransactionId? = sender.account?.let { account ->
        detectDuplicate.detect(account = account, amount = amount, asset = asset, time = time)
    }

    /** The fields every captured row takes from the same message, whatever it represents. */
    private fun row(
        sender: FinancialSender,
        parsed: ParsedMessage,
        amount: PositiveDouble,
        asset: AssetCode,
        kind: CapturedKind,
        origin: CaptureOrigin,
        duplicateOf: TransactionId? = null,
    ) = CapturedTransaction(
        id = CapturedTransactionId(UUID.randomUUID()),
        sender = sender.id,
        kind = kind,
        amount = amount,
        asset = asset,
        time = parsed.time,
        counterparty = parsed.counterparty,
        reference = parsed.reference,
        account = sender.account,
        category = null,
        description = null,
        duplicateOf = duplicateOf,
        capturedAt = timeProvider.utcNow(),
        origin = origin,
    )

    /**
     * The captured row and its proof-of-handling are written together. The proof is written last
     * so a failure part-way leaves the message re-processable rather than silently lost.
     */
    private suspend fun persist(
        entry: CapturedEntry,
        sender: FinancialSender,
        fingerprint: MessageFingerprint,
    ): Either<SmsCaptureError, Unit> = Either
        .catch {
            capturedTransactionRepository.saveEntry(entry)
            processedMessageRepository.record(
                ProcessedMessage(
                    fingerprint = fingerprint,
                    sender = sender.id,
                    outcome = ProcessedOutcome.Captured,
                    processedAt = timeProvider.utcNow(),
                ),
            )
        }
        .mapLeft { SmsCaptureError.Persistence(it::class.simpleName ?: "unknown") }
}
