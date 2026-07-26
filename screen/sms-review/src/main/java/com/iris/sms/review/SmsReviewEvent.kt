package com.iris.sms.review

/**
 * What the user can do to the review inbox.
 *
 * Every enrichment event carries the item id rather than relying on whichever row happens to be
 * expanded. A list that reloads underneath a half-finished edit is normal here — a second message
 * can arrive at any moment — and an event addressed to "the open one" would land on whatever moved
 * into that position.
 */
sealed interface SmsReviewEvent {

    /** null collapses whatever is open. */
    data class OnItemExpanded(val id: String?) : SmsReviewEvent

    /** Files the item under a category, overriding any suggestion (FR-022, FR-024). */
    data class OnCategorySelected(val id: String, val categoryId: String) : SmsReviewEvent

    /** Replaces the description without touching anything the parser extracted (FR-022). */
    data class OnDescriptionChanged(val id: String, val description: String) : SmsReviewEvent

    /** Chooses where the money posts, which is what clears `NeedsAccount` (FR-027a). */
    data class OnAccountSelected(val id: String, val accountId: String) : SmsReviewEvent

    /**
     * Corrects a misread figure (FR-023).
     *
     * Raw text rather than a number, because the field is mid-edit: `"1,5"` is a legitimate
     * keystroke on the way to `"1,530"`, and rejecting it would fight the user's typing.
     */
    data class OnAmountEdited(val id: String, val amount: String) : SmsReviewEvent

    data class OnCounterpartyEdited(val id: String, val counterparty: String) : SmsReviewEvent

    data class OnTimeEdited(val id: String, val epochMillis: Long) : SmsReviewEvent

    /** Commits the item to the ledger. Refused while the item still needs an account (FR-027a). */
    data class OnConfirm(val id: String) : SmsReviewEvent

    /**
     * Throws the item away for good (FR-025).
     *
     * @param alsoRemoveFee removes the linked transaction cost with it, so the two cannot drift
     *   apart (FR-018). `false` keeps the charge, detached, as a standalone cost to review.
     */
    data class OnDismiss(val id: String, val alsoRemoveFee: Boolean) : SmsReviewEvent

    /**
     * Dismisses the duplicate warning without committing anything (FR-029).
     *
     * Separate from [OnConfirm] on purpose: acknowledging "yes, I know it looks like the other
     * one" and actually filing it are two decisions, and merging them would turn the warning into
     * something to click through rather than read.
     */
    data class OnKeepDespiteDuplicate(val id: String) : SmsReviewEvent

    data object OnClose : SmsReviewEvent
}
