package com.iris.sms.settings

import kotlinx.collections.immutable.persistentListOf

/**
 * Static view-state for tests.
 *
 * Every value is hard-coded. Nothing here reads a clock, a locale or a permission, so a snapshot
 * that changes is a change in the code rather than in the machine it ran on.
 */
internal object SmsSettingsFixtures {

    val Accounts = persistentListOf(
        AccountPickUi(id = "cccccccc-0000-0000-0000-000000000001", name = "DTB Current"),
        AccountPickUi(id = "cccccccc-0000-0000-0000-000000000002", name = "KCB Savings"),
    )

    val Mpesa = SenderMappingUi(
        senderId = "MPESA",
        displayName = "M-PESA",
        accountName = "DTB Current",
        enabled = true,
    )

    /** The state every seeded sender starts in, and the visible half of FR-027a. */
    val UnmappedKcb = SenderMappingUi(
        senderId = "KCB",
        displayName = "KCB Bank",
        accountName = null,
        enabled = true,
    )

    val DisabledDtb = SenderMappingUi(
        senderId = "DTB-KENYA",
        displayName = "Diamond Trust Bank",
        accountName = "DTB Current",
        enabled = false,
    )

    /** What every user who has not opted in sees, which is the whole feature switched off. */
    val Off = SmsCaptureSettingsState(
        captureEnabled = false,
        permissionState = SmsPermissionUi.NotRequested,
        senders = persistentListOf(),
        accounts = Accounts,
        pendingCount = 0,
        importState = HistoricalImportUi.AlreadyRun,
        rationaleVisible = false,
    )

    val On = Off.copy(
        captureEnabled = true,
        permissionState = SmsPermissionUi.Granted,
        senders = persistentListOf(Mpesa, UnmappedKcb, DisabledDtb),
        pendingCount = 2,
    )
}
