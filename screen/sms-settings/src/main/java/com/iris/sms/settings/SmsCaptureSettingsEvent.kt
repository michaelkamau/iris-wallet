package com.iris.sms.settings

/**
 * Everything the user can do on the settings screen.
 *
 * The enable path is deliberately three events rather than one. [OnEnableRequested] shows the
 * explanation, [OnRationaleAccepted] is the moment the user consents to being asked, and
 * [OnPermissionResult] is the operating system's answer. Collapsing them would put the system
 * dialog in front of the user before anyone had told them what would be read (FR-002).
 */
sealed interface SmsCaptureSettingsEvent {

    /** Tapping the master switch on. Shows the rationale; never prompts (FR-002). */
    data object OnEnableRequested : SmsCaptureSettingsEvent

    /** The user has read the explanation and is willing to be asked by the system. */
    data object OnRationaleAccepted : SmsCaptureSettingsEvent

    /** The user closed the explanation without accepting. Nothing is enabled, nothing is asked. */
    data object OnRationaleDismissed : SmsCaptureSettingsEvent

    /**
     * The system's answer.
     *
     * [permanentlyDenied] is the screen's reading of `shouldShowRequestPermissionRationale`,
     * which only the Activity can answer, so it arrives as part of the event rather than being
     * looked up by the view model.
     */
    data class OnPermissionResult(
        val granted: Boolean,
        val permanentlyDenied: Boolean,
    ) : SmsCaptureSettingsEvent

    /**
     * Turning the feature off (FR-003). Flips the preference and nothing else: captured items and
     * committed transactions both survive (Acceptance 4.4).
     */
    data object OnDisable : SmsCaptureSettingsEvent

    data class OnSenderEnabledChanged(
        val senderId: String,
        val enabled: Boolean,
    ) : SmsCaptureSettingsEvent

    /** Mapping one sender to one account (FR-027). */
    data class OnSenderAccountSelected(
        val senderId: String,
        val accountId: String,
    ) : SmsCaptureSettingsEvent

    data class OnAddSender(
        val rawSender: String,
        val displayName: String,
    ) : SmsCaptureSettingsEvent

    data class OnRemoveSender(val senderId: String) : SmsCaptureSettingsEvent

    /** The opt-in 30-day import (FR-030). Wired in User Story 5. */
    data object OnStartHistoricalImport : SmsCaptureSettingsEvent

    /** The user opted into the inbox import and should be asked for `READ_SMS`, not `RECEIVE_SMS`. */
    data object OnRequestHistoricalImportPermission : SmsCaptureSettingsEvent

    /** Re-reads the gate after Android answers the one-off `READ_SMS` request. */
    data object OnHistoricalImportPermissionResult : SmsCaptureSettingsEvent

    data object OnOpenReview : SmsCaptureSettingsEvent

    data object OnClose : SmsCaptureSettingsEvent
}
