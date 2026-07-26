package com.iris.sms.review

import kotlinx.collections.immutable.ImmutableList
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
        amountEditable = "1350",
        counterparty = "James Kinyua Mwangi",
        timeFormatted = "25 Jul 2026, 19:50",
        timeEpochMillis = 1_784_998_200_000L,
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
        amountEditable = "3050",
        reference = "UGO7B0DQ91",
        accountName = null,
        status = ReviewStatusUi.NeedsAccount,
    )

    /** The DTB transfer, carrying the charge it reported (rendered from User Story 2 onwards). */
    val WithFee = ReadyToConfirm.copy(
        id = "33333333-3333-3333-3333-333333333333",
        counterparty = "Michael Kamau Njuguna",
        amountFormatted = "-KES 6,000.00",
        amountEditable = "6000",
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

    /** A payee the user has filed before, so the suggestion is already on the row (FR-024). */
    val WithSuggestedCategory = ReadyToConfirm.copy(
        id = "55555555-5555-5555-5555-555555555555",
        counterparty = "Frank Inn Kikuyu",
        categoryName = "Food & Drinks",
    )

    /** What the pickers offer. Fixed ids and colours, so the chips render identically each run. */
    val Categories = persistentListOf(
        CategoryPickUi(
            id = "aaaaaaaa-0000-0000-0000-000000000001",
            name = "Food & Drinks",
            colorArgb = 0xFFE57373.toInt(),
        ),
        CategoryPickUi(
            id = "aaaaaaaa-0000-0000-0000-000000000002",
            name = "Groceries",
            colorArgb = 0xFF81C784.toInt(),
        ),
        CategoryPickUi(
            id = "aaaaaaaa-0000-0000-0000-000000000003",
            name = "Transport",
            colorArgb = 0xFF64B5F6.toInt(),
        ),
    )

    val Accounts = persistentListOf(
        AccountPickUi(id = "bbbbbbbb-0000-0000-0000-000000000001", name = "M-PESA"),
        AccountPickUi(id = "bbbbbbbb-0000-0000-0000-000000000002", name = "DTB Current"),
    )

    fun content(
        vararg items: CapturedItemUi,
        expandedItemId: String? = null,
        categories: ImmutableList<CategoryPickUi> = Categories,
        accounts: ImmutableList<AccountPickUi> = Accounts,
    ) = SmsReviewState.Content(
        items = persistentListOf(*items),
        pendingCount = items.size,
        expandedItemId = expandedItemId,
        categories = categories,
        accounts = accounts,
    )
}
