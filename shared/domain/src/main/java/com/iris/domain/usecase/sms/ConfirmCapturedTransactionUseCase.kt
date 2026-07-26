package com.iris.domain.usecase.sms

import androidx.room.withTransaction
import arrow.core.Either
import arrow.core.flatten
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.iris.base.time.TimeProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.Expense
import com.iris.data.model.Income
import com.iris.data.model.PositiveValue
import com.iris.data.model.Transaction
import com.iris.data.model.TransactionId
import com.iris.data.model.TransactionMetadata
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.CounterpartyCategory
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.data.repository.TransactionRepository
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Every way a confirm can decline to reach the ledger. */
sealed interface ConfirmCaptureError {
    /** No account chosen, and there is no default to fall back on (FR-027a). */
    data class MissingAccount(val id: CapturedTransactionId) : ConfirmCaptureError

    /** Already confirmed or dismissed on another surface. */
    data class CapturedItemGone(val id: CapturedTransactionId) : ConfirmCaptureError

    /** The dedicated transaction-cost category could not be read or created (FR-017). */
    data class CategoryCreationFailed(val cause: String) : ConfirmCaptureError

    /** The whole block rolled back; nothing was written. */
    data class Persistence(val cause: String) : ConfirmCaptureError
}

/**
 * Commits a reviewed item to the ledger, which is the only moment SMS capture affects balances,
 * budgets, reports or search (FR-021).
 *
 * The insert and the removal of the pending row happen in **one** database transaction, so the
 * failure mode where a user sees the item disappear from review without a matching ledger entry —
 * or worse, the reverse — is not representable.
 *
 * What is *not* written matters as much as what is: no source column, no marker tag, no metadata
 * flag. A confirmed item is a plain transaction, indistinguishable from one typed by hand
 * (FR-033).
 */
@Singleton
class ConfirmCapturedTransactionUseCase @Inject constructor(
    private val db: IrisRoomDatabase,
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val counterpartyCategoryRepository: CounterpartyCategoryRepository,
    private val counterpartyNormalizer: CounterpartyNormalizer,
    private val ensureTransactionCostCategory: EnsureTransactionCostCategoryUseCase,
    private val timeProvider: TimeProvider,
) {

    /**
     * @param account overrides the captured mapping, which is how an item that needs an account
     *   is confirmed at all.
     * @param category the user's choice for this counterparty, remembered on the way through so
     *   the next payment to the same payee is suggested it (FR-024).
     * @param description replaces the captured description; the reference is still appended.
     * @param amount corrects a misread figure (FR-023).
     * @param counterparty corrects a misread payee; the memory is keyed off this corrected name,
     *   not the one the parser produced, or the correction would be forgotten immediately.
     * @param time corrects a misread date (FR-023).
     */
    @Suppress("LongParameterList")
    suspend fun confirm(
        id: CapturedTransactionId,
        account: AccountId? = null,
        category: CategoryId? = null,
        description: NotBlankTrimmedString? = null,
        amount: PositiveDouble? = null,
        counterparty: NotBlankTrimmedString? = null,
        time: Instant? = null,
    ): Either<ConfirmCaptureError, TransactionId> = Either
        .catch {
            db.withTransaction {
                commit(
                    id = id,
                    account = account,
                    category = category,
                    edits = Edits(
                        description = description,
                        amount = amount,
                        counterparty = counterparty,
                        time = time,
                    ),
                )
            }
        }
        .mapLeft<ConfirmCaptureError> {
            ConfirmCaptureError.Persistence(it::class.simpleName ?: "unknown")
        }
        .flatten()

    private suspend fun commit(
        id: CapturedTransactionId,
        account: AccountId?,
        category: CategoryId?,
        edits: Edits,
    ): Either<ConfirmCaptureError, TransactionId> = either {
        val entry = capturedTransactionRepository.findById(id)
        ensureNotNull(entry) { ConfirmCaptureError.CapturedItemGone(id) }

        val captured = entry.principal.applying(edits)
        val resolvedAccount = account ?: captured.account
        ensureNotNull(resolvedAccount) { ConfirmCaptureError.MissingAccount(id) }

        // Read once per confirm, and only when a fee is actually about to be written, so a
        // category the user never needed is never conjured into their list (FR-017).
        val needsCostCategory = entry.fee != null || captured.kind is CapturedKind.Fee
        val costCategory = if (needsCostCategory) {
            ensureTransactionCostCategory.ensure()
                .mapLeft<ConfirmCaptureError>(ConfirmCaptureError::CategoryCreationFailed)
                .bind()
        } else {
            null
        }

        // A message that reported nothing but a charge is itself a transaction cost, so it files
        // under the same category rather than under whatever was picked at review time (FR-020).
        val principalCategory = when (captured.kind) {
            is CapturedKind.Fee -> costCategory
            is CapturedKind.Principal -> category ?: captured.category
        }

        val transaction = captured.toLedger(
            account = resolvedAccount,
            category = principalCategory,
            description = edits.description ?: captured.description,
        )
        transactionRepository.save(transaction)

        // The fee is a second, ordinary `Expense` — distinct from the principal, never a field on
        // it (FR-016) — written inside the same block, so the pair reaches the ledger together or
        // not at all (SC-003).
        entry.fee?.let { fee ->
            transactionRepository.save(
                fee.toLedger(
                    account = resolvedAccount,
                    category = costCategory,
                    description = null,
                ),
            )
        }

        // Written inside the same block as the ledger row, so the app can never end up suggesting
        // a category for a payment that was rolled back (FR-024).
        rememberCategory(captured, principalCategory)

        // The `processed_messages` row was written with outcome CAPTURED when the message was
        // first seen, so confirming needs no second write to keep re-delivery blocked (FR-025).
        // `deleteEntry` removes the principal and its fee together (FR-018).
        capturedTransactionRepository.deleteEntry(id)
        transaction.id
    }

    /**
     * Upserts the counterparty→category memory, so the *latest* choice is the one suggested next
     * time and correcting a suggestion actually sticks (FR-024, Acceptance 3.3).
     *
     * Deliberately silent about three cases:
     *
     * - a **fee**, whose category is forced to "Transaction costs" and was never the user's
     *   choice about that payee;
     * - a confirm with **no category**, which is an absence rather than a decision — writing it
     *   would need a row meaning "nothing", and reading it back would suppress a real suggestion;
     * - a payment naming **nobody**, which has no key to remember against.
     */
    private suspend fun rememberCategory(
        captured: CapturedTransaction,
        category: CategoryId?,
    ) {
        val chosen = category.takeIf { captured.kind is CapturedKind.Principal } ?: return
        val key = captured.counterparty
            ?.let { counterpartyNormalizer.key(it).getOrNull() }
            ?: return
        counterpartyCategoryRepository.remember(
            CounterpartyCategory(
                counterparty = key,
                category = chosen,
                updatedAt = timeProvider.utcNow(),
            ),
        )
    }

    /**
     * The user's corrections, applied before anything is read off the captured row, so every
     * later step — the ledger write, the category memory — sees the corrected values rather than
     * the parsed ones (FR-023).
     */
    private fun CapturedTransaction.applying(edits: Edits): CapturedTransaction = copy(
        amount = edits.amount ?: amount,
        counterparty = edits.counterparty ?: counterparty,
        time = edits.time ?: time,
    )

    /** Grouped so [commit] keeps a readable signature as FR-023 adds correctable fields. */
    private data class Edits(
        val description: NotBlankTrimmedString?,
        val amount: PositiveDouble?,
        val counterparty: NotBlankTrimmedString?,
        val time: Instant?,
    )

    /**
     * The reference is appended to the description rather than stored beside it, because the
     * ledger has nowhere to put it that a manual entry would not also have — and adding one would
     * be the marker FR-033 rules out. `Ref: UGP7B0ITE4` is a convention the user can search
     * (FR-015).
     */
    private fun CapturedTransaction.toLedger(
        account: AccountId,
        category: CategoryId?,
        description: NotBlankTrimmedString?,
    ): Transaction {
        val value = PositiveValue(amount = amount, asset = asset)
        val shape = kind
        val common = LedgerFields(
            id = TransactionId(UUID.randomUUID()),
            title = counterparty,
            description = describe(description, taxDetail(shape), reference),
            category = category,
            value = value,
            account = account,
        )
        return when (shape) {
            is CapturedKind.Principal -> when (shape.direction) {
                MoneyDirection.MoneyOut -> common.toExpense(time)
                MoneyDirection.MoneyIn -> common.toIncome(time)
            }
            // A fee is always money out, and always files under the transaction-cost category.
            is CapturedKind.Fee -> common.toExpense(time)
        }
    }

    /**
     * The reported tax is written as a line on the fee's own description and nowhere else. It is
     * detail about a cost already recorded in full, so promoting it to a transaction of its own
     * would double-count the money the user actually paid (FR-019).
     */
    private fun CapturedTransaction.taxDetail(kind: CapturedKind): String? = when (kind) {
        is CapturedKind.Principal -> null
        is CapturedKind.Fee -> kind.tax?.let {
            "Incl. tax ${asset.code} ${MONEY.format(it.value)}"
        }
    }

    private fun describe(
        description: NotBlankTrimmedString?,
        taxDetail: String?,
        reference: ProviderReference?,
    ): NotBlankTrimmedString? {
        val ref = reference?.let { "Ref: ${it.value}" }
        val text = listOfNotNull(description?.value, taxDetail, ref)
            .joinToString(separator = "\n")
        return NotBlankTrimmedString.from(text).getOrNull()
    }

    /** Just the fields `Expense` and `Income` share, so neither branch can drift from the other. */
    private data class LedgerFields(
        val id: TransactionId,
        val title: NotBlankTrimmedString?,
        val description: NotBlankTrimmedString?,
        val category: CategoryId?,
        val value: PositiveValue,
        val account: AccountId,
    ) {
        fun toExpense(time: Instant) = Expense(
            id = id,
            title = title,
            description = description,
            category = category,
            time = time,
            settled = true,
            metadata = EMPTY_METADATA,
            tags = emptyList(),
            value = value,
            account = account,
        )

        fun toIncome(time: Instant) = Income(
            id = id,
            title = title,
            description = description,
            category = category,
            time = time,
            settled = true,
            metadata = EMPTY_METADATA,
            tags = emptyList(),
            value = value,
            account = account,
        )
    }

    private companion object {
        /** Fixed locale: a fee's tax detail must read the same wherever the device is set. */
        private val MONEY = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

        /** No recurring rule, no paid-for date, no loan: exactly what a manual entry carries. */
        private val EMPTY_METADATA = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
    }
}
