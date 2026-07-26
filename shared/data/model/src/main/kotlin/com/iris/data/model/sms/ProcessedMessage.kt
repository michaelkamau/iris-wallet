package com.iris.data.model.sms

import java.time.Instant

enum class ProcessedOutcome {
    /** Produced one or two captured rows. */
    Captured,

    /** Matched an exclusion rule, or no rule matched — deliberately not a transaction. */
    Ignored,

    /** The user threw it away; it must never reappear (FR-025). */
    Dismissed,
}

/** Minimal proof a message was already handled. Holds NO message content (FR-006, FR-028). */
data class ProcessedMessage(
    val fingerprint: MessageFingerprint,
    val sender: SenderId,
    val outcome: ProcessedOutcome,
    val processedAt: Instant,
)
