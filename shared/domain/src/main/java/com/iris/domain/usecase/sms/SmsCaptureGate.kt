package com.iris.domain.usecase.sms

/**
 * Whether the app is allowed to look at messages at all (FR-001, FR-003, FR-032).
 *
 * Declared here and implemented in `:shared:sms:capture`, because both answers come from Android —
 * a DataStore preference and a runtime permission — while the decision that depends on them is
 * business logic that must be testable without a device.
 *
 * The two questions are kept apart deliberately: "switched off" and "not permitted" look identical
 * to the capture pipeline but not to the user, and [CaptureSmsUseCase] reports them as different
 * errors so the settings screen can say something true about which one it is.
 */
interface SmsCaptureGate {

    /**
     * The master switch. An absent preference reads `false`, which is the whole of SC-010: an
     * existing user who upgrades has capture off until they ask for it.
     */
    suspend fun isEnabled(): Boolean

    /** `RECEIVE_SMS`, without which no broadcast can be read (FR-005). */
    fun hasReceivePermission(): Boolean

    /** `READ_SMS`, needed only by the opt-in historical import (FR-030). */
    fun canReadInbox(): Boolean

    /** Both halves at once: the only question the receiver path needs to ask. */
    suspend fun isCapturing(): Boolean = isEnabled() && hasReceivePermission()
}
