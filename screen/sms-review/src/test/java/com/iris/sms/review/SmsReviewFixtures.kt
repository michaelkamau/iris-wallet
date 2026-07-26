package com.iris.sms.review

import kotlinx.collections.immutable.persistentListOf

/**
 * Static view-state for tests.
 *
 * Every value here is a hard-coded string. Nothing calls `Instant.now()`, a live formatter or a
 * locale-dependent helper — a screenshot that changes because the test ran after midnight, or in a
 * different timezone, is a screenshot that tells you nothing.
 */
internal object SmsReviewFixtures {

    /** The M-PESA payment from spec.md, already formatted for display. */
    val ReadyToConfirm = CapturedItemUi(
        id = "11111111-1111-1111-1111-111111111111",
        amountFormatted = "-KES 1,350.00",
        counterparty = "James Kinyua Mwangi",
        timeFormatted = "25 Jul 2026, 19:50",
        reference = "UGP7B0ITE4",
        accountName = "M-PESA",
        categoryName = null,
        description = "",
        feeFormatted = null,
        status = ReviewStatusUi.ReadyToConfirm,
    )

    /** The same shape with no account mapping, which is where a P1-only build lands (FR-027a). */
    val NeedsAccount = ReadyToConfirm.copy(
        id = "22222222-2222-2222-2222-222222222222",
        counterparty = "Frank Inn Kikuyu",
        amountFormatted = "-KES 3,050.00",
        reference = "UGO7B0DQ91",
        accountName = null,
        status = ReviewStatusUi.NeedsAccount,
    )

    /** The DTB transfer, carrying the charge it reported (rendered from User Story 2 onwards). */
    val WithFee = ReadyToConfirm.copy(
        id = "33333333-3333-3333-3333-333333333333",
        counterparty = "Michael Kamau Njuguna",
        amountFormatted = "-KES 6,000.00",
        timeFormatted = "25 Jul 2026, 17:41",
        reference = "AD3EA389C13A7",
        accountName = "DTB Current",
        description = "Rent transfer",
        feeFormatted = "KES 59.76",
    )

    val PossibleDuplicate = ReadyToConfirm.copy(
        id = "44444444-4444-4444-4444-444444444444",
        status = ReviewStatusUi.PossibleDuplicate(
            existingSummary = "A similar transaction is already recorded",
        ),
    )

    fun content(vararg items: CapturedItemUi, expandedItemId: String? = null) =
        SmsReviewState.Content(
            items = persistentListOf(*items),
            pendingCount = items.size,
            expandedItemId = expandedItemId,
        )
}
