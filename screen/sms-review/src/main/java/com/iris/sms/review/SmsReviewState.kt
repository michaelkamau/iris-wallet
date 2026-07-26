package com.iris.sms.review

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Everything the review inbox renders.
 *
 * Only primitives, `@Immutable` classes and `ImmutableList` appear here. No `CapturedTransaction`,
 * no `Instant`, no `PositiveDouble`, no `CategoryId`: keeping domain types out is what makes the
 * Compose-stability gate pass and, more usefully, what makes a screenshot of this screen depend on
 * nothing but the values written in the test.
 */
@Immutable
sealed interface SmsReviewState {

    /** Before the first read completes. Distinct from [Empty], which is a real answer. */
    data object Loading : SmsReviewState

    /** Nothing is waiting. */
    data object Empty : SmsReviewState

    data class Content(
        val items: ImmutableList<CapturedItemUi>,
        val pendingCount: Int,
        /** Stable string id of the expanded row; null means the whole list is collapsed. */
        val expandedItemId: String?,
    ) : SmsReviewState
}

/**
 * One pending item, already formatted.
 *
 * The amount and the time arrive as strings because formatting them needs a locale, a device
 * preference and a feature flag — three things a snapshot test must not have to stand up, and
 * three things that would otherwise make the rendered pixels depend on when the test ran.
 */
@Immutable
data class CapturedItemUi(
    /** `CapturedTransactionId.value.toString()`. */
    val id: String,
    val amountFormatted: String,
    val counterparty: String,
    val timeFormatted: String,
    val reference: String?,
    /** null renders the "choose an account" affordance rather than a name. */
    val accountName: String?,
    val categoryName: String?,
    val description: String,
    /** The linked transaction cost, once User Story 2 lands. */
    val feeFormatted: String?,
    val status: ReviewStatusUi,
)

/** The view's echo of `com.iris.data.model.sms.ReviewStatus`, carrying strings rather than ids. */
@Immutable
sealed interface ReviewStatusUi {
    /** No account mapped and none chosen: confirming is refused until one is (FR-027a). */
    data object NeedsAccount : ReviewStatusUi

    /** The ledger already holds something that looks like this (FR-029). */
    data class PossibleDuplicate(val existingSummary: String) : ReviewStatusUi

    data object ReadyToConfirm : ReviewStatusUi
}
