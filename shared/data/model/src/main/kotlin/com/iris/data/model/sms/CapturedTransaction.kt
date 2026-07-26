package com.iris.data.model.sms

import com.iris.data.model.AccountId
import com.iris.data.model.CategoryId
import com.iris.data.model.TransactionId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sync.Identifiable
import java.time.Instant

/** Direction of a principal amount (FR-008). */
enum class MoneyDirection { MoneyOut, MoneyIn }

/** Where a captured item came from. Used only for diagnostics and import reporting. */
enum class CaptureOrigin { LiveBroadcast, HistoricalImport }

/**
 * Discriminates a principal amount from a transaction cost.
 * A tax component (FR-019) is only representable on a [Fee] — never on a [Principal].
 */
sealed interface CapturedKind {
    data class Principal(val direction: MoneyDirection) : CapturedKind

    /**
     * @param parent the principal this fee belongs to; null for a standalone fee (FR-020).
     * @param tax the tax portion reported inside the cost; detail only, never its own transaction.
     */
    data class Fee(
        val parent: CapturedTransactionId?,
        val tax: PositiveDouble?,
    ) : CapturedKind
}

/**
 * A transaction extracted from one message, awaiting user review.
 *
 * Lives ONLY in `captured_transactions`; it has no effect on balances, budgets, reports or
 * search until confirmed (FR-021, FR-021a).
 */
data class CapturedTransaction(
    override val id: CapturedTransactionId,
    val sender: SenderId,
    val kind: CapturedKind,
    val amount: PositiveDouble,
    val asset: AssetCode,
    val time: Instant,
    val counterparty: NotBlankTrimmedString?,
    val reference: ProviderReference?,
    /** null => the sender has no account mapping yet (FR-027a). No default fallback exists. */
    val account: AccountId?,
    val category: CategoryId?,
    val description: NotBlankTrimmedString?,
    /** Set when an existing ledger transaction looks like the same event (FR-029). */
    val duplicateOf: TransactionId?,
    val capturedAt: Instant,
    val origin: CaptureOrigin,
) : Identifiable<CapturedTransactionId>

/** Derived, never stored — storing it would let the status and [CapturedTransaction.account] disagree. */
sealed interface ReviewStatus {
    data object NeedsAccount : ReviewStatus
    data class PossibleDuplicate(val existing: TransactionId) : ReviewStatus
    data object ReadyToConfirm : ReviewStatus
}

/**
 * A missing account outranks a suspected duplicate: without an account there is nothing to
 * confirm at all, so [ReviewStatus.NeedsAccount] is always the first thing the user is asked for.
 */
fun CapturedTransaction.reviewStatus(): ReviewStatus = when {
    account == null -> ReviewStatus.NeedsAccount
    duplicateOf != null -> ReviewStatus.PossibleDuplicate(duplicateOf)
    else -> ReviewStatus.ReadyToConfirm
}

/**
 * A principal together with the fee captured from the same message, for review and confirm.
 *
 * [principal] is the row the user acts on. For the one message shape that reports a charge and
 * nothing else (FR-020) that row *is* the charge — `kind` is [CapturedKind.Fee] with no parent —
 * and [fee] is null, because the entry has no second amount hanging off it. Confirming, dismissing
 * and reviewing then work unchanged; only the category the fee lands under differs.
 */
data class CapturedEntry(
    val principal: CapturedTransaction,
    val fee: CapturedTransaction?,
)
