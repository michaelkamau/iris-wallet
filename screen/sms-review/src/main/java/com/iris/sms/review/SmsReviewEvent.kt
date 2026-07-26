package com.iris.sms.review

/**
 * What the user can do to the review inbox in this release.
 *
 * The enrichment events — editing the amount, the counterparty, the time, picking a category or an
 * account — arrive with User Stories 3 and 4. Adding them here before the screen can honour them
 * would be a promise the view model cannot keep.
 */
sealed interface SmsReviewEvent {

    /** null collapses whatever is open. */
    data class OnItemExpanded(val id: String?) : SmsReviewEvent

    /** Commits the item to the ledger. Refused while the item still needs an account (FR-027a). */
    data class OnConfirm(val id: String) : SmsReviewEvent

    /**
     * Throws the item away for good (FR-025).
     *
     * @param alsoRemoveFee removes the linked transaction cost with it, so the two cannot drift
     *   apart (FR-018). `false` keeps the charge, detached, as a standalone cost to review.
     */
    data class OnDismiss(val id: String, val alsoRemoveFee: Boolean) : SmsReviewEvent

    data object OnClose : SmsReviewEvent
}
