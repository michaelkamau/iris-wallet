package com.iris.sms.parser

import arrow.core.getOrElse
import com.iris.data.model.sms.MessageFingerprint
import java.security.MessageDigest
import java.util.Locale

/**
 * The dedupe key for a message (FR-028, SC-003).
 *
 * A provider reference is unique per provider, so it makes the strongest key. Without one the key
 * is a digest of the normalised sender and the message body — and **never** the receipt time,
 * because the same message arrives with one timestamp over the broadcast and another out of the
 * inbox, and both must collapse onto one fingerprint (Edge case "Duplicate delivery").
 *
 * The body itself never appears in the output, only its digest (FR-006). Callers pass the
 * promo-stripped body, which is what the capture pipeline already holds by the time it needs a
 * fingerprint.
 */
object MessageFingerprints {

    private const val ALGORITHM = "SHA-256"
    private const val DIGEST_CHARS = 32

    fun of(message: RawSmsMessage, parsed: ParsedMessage?): MessageFingerprint {
        val reference = parsed?.reference
        val referenceKey = if (parsed != null && reference != null) {
            MessageFingerprint.from("${parsed.ruleSet.value}:${reference.value}")
        } else {
            null
        }
        // A reference key can only fail validation if a rule set id is absurdly long; the digest
        // form is always well-formed, so it doubles as the fallback and this function is total.
        return referenceKey?.getOrElse { digestKey(message) } ?: digestKey(message)
    }

    private fun digestKey(message: RawSmsMessage): MessageFingerprint =
        MessageFingerprint.unsafe("h:" + digest(message))

    private fun digest(message: RawSmsMessage): String {
        val preimage = message.sender.value + '\u0000' + message.body.trim()
        val bytes = MessageDigest.getInstance(ALGORITHM).digest(preimage.toByteArray())
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }.take(DIGEST_CHARS)
    }
}
