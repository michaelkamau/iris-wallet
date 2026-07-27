package com.iris.sms.settings

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * Everything the settings screen renders, and nothing else.
 *
 * Only primitives and immutable lists cross this boundary, so the screen is a pure function of
 * this value and a screenshot of it cannot drift because a repository, a clock or a permission
 * checker was consulted mid-composition.
 */
@Immutable
data class SmsCaptureSettingsState(
    /**
     * The master switch (FR-001, FR-003). False on a fresh install *and* on upgrade — the
     * preference behind it is absent by default and absent reads false.
     */
    val captureEnabled: Boolean,
    val permissionState: SmsPermissionUi,
    val senders: ImmutableList<SenderMappingUi>,
    val accounts: ImmutableList<AccountPickUi>,
    /** How many captured items are waiting to be reviewed, for the "Review N" action (FR-021a). */
    val pendingCount: Int,
    val importState: HistoricalImportUi,
    /** The import has not run, but the user has not yet opted into `READ_SMS`. */
    val historicalImportPermissionRequired: Boolean = false,
    /**
     * The pre-permission explanation (FR-002). Visible strictly *before* the system dialog, never
     * alongside it: the user is told what will be read while they still have the option to say no
     * without any dialog having appeared.
     */
    val rationaleVisible: Boolean,
)

/**
 * What the operating system currently says about `RECEIVE_SMS`.
 *
 * [Denied] and [PermanentlyDenied] are separated because they need different offers: the first
 * can simply be asked again, the second cannot be asked at all and has to send the user to the
 * system app-settings page (Acceptance 4.2).
 */
@Immutable
sealed interface SmsPermissionUi {
    data object Granted : SmsPermissionUi
    data object NotRequested : SmsPermissionUi
    data object Denied : SmsPermissionUi
    data object PermanentlyDenied : SmsPermissionUi
}

/**
 * One configured sender and where its money lands.
 *
 * [accountName] being null is not an error state to be cleaned up later; it is the honest answer
 * "nobody has said yet", and it is what makes the captures from this sender wait to be told
 * rather than be filed somewhere plausible (FR-027a).
 */
@Immutable
data class SenderMappingUi(
    val senderId: String,
    val displayName: String,
    val accountName: String?,
    /** The per-sender switch, independent of every other sender and of the master one (FR-003). */
    val enabled: Boolean,
)

/** An account the user can map a sender to. */
@Immutable
data class AccountPickUi(
    val id: String,
    val name: String,
)

/**
 * The one-off import of the last 30 days (FR-030, FR-031).
 *
 * User Story 5 builds the import itself. The state lives here from User Story 4 so the settings
 * screen has one shape rather than two, and so that the "already run" case is representable
 * before anything can set it.
 */
@Immutable
sealed interface HistoricalImportUi {
    /** Offered only when `READ_SMS` is granted and the import has never completed. */
    data object Available : HistoricalImportUi
    data class Running(val processed: Int) : HistoricalImportUi
    data class Finished(val captured: Int) : HistoricalImportUi
    data object AlreadyRun : HistoricalImportUi
}
