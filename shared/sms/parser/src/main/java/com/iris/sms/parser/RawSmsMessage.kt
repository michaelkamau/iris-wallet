package com.iris.sms.parser

import com.iris.data.model.sms.SenderId
import java.time.Instant

/** What the platform layer hands to the parser. Pure data — no Android types. */
data class RawSmsMessage(
    val sender: SenderId,
    val body: String,
    val receivedAt: Instant,
)
