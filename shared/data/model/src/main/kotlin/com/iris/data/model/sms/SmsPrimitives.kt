package com.iris.data.model.sms

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.iris.data.model.exact.Exact
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class CapturedTransactionId(override val value: UUID) : UniqueId

/**
 * Normalised SMS originating address: trimmed, uppercased, whitespace-collapsed.
 *
 * Making the type impossible to construct un-normalised is what stops `"MPESA"` and `"M-PESA "`
 * from becoming two different senders.
 */
@JvmInline
value class SenderId private constructor(val value: String) {
    companion object : Exact<String, SenderId> {
        override val exactName = "SenderId"

        override fun Raise<String>.spec(raw: String): SenderId {
            val trimmed = NotBlankTrimmedString.from(raw).bind()
            return SenderId(trimmed.value.uppercase().replace(WHITESPACE, " "))
        }

        private val WHITESPACE = Regex("\\s+")
    }
}

/** Provider reference code, e.g. UGP7B0ITE4, AD3EA389C13A7. Uppercased, alphanumeric only. */
@JvmInline
value class ProviderReference private constructor(val value: String) {
    companion object : Exact<String, ProviderReference> {
        override val exactName = "ProviderReference"

        override fun Raise<String>.spec(raw: String): ProviderReference {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value.uppercase()
            ensure(trimmed.all(Char::isLetterOrDigit)) { "'$trimmed' is not alphanumeric" }
            ensure(trimmed.length in REF_LENGTH) { "'$trimmed' length not in $REF_LENGTH" }
            return ProviderReference(trimmed)
        }

        private val REF_LENGTH = 4..32
    }
}

/**
 * Dedupe key. Either `"ruleSetId:REFERENCE"` or `"h:<32 hex chars of sha256>"`.
 * Never the message body — only a digest of it (FR-006).
 */
@JvmInline
value class MessageFingerprint private constructor(val value: String) {
    companion object : Exact<String, MessageFingerprint> {
        override val exactName = "MessageFingerprint"

        override fun Raise<String>.spec(raw: String): MessageFingerprint {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value
            ensure(trimmed.length <= MAX_LENGTH) { "'$trimmed' is longer than $MAX_LENGTH" }
            return MessageFingerprint(trimmed)
        }

        private const val MAX_LENGTH = 64
    }
}

/**
 * Lookup key for the counterparty→category memory: uppercased, non-alphanumerics removed,
 * so `"Frank Inn Kikuyu"` and `"FRANK INN KIKUYU"` share one row (FR-024).
 */
@JvmInline
value class CounterpartyKey private constructor(val value: String) {
    companion object : Exact<String, CounterpartyKey> {
        override val exactName = "CounterpartyKey"

        override fun Raise<String>.spec(raw: String): CounterpartyKey {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value
            val key = trimmed.uppercase().filter(Char::isLetterOrDigit)
            ensure(key.isNotBlank()) { "'$trimmed' has no alphanumeric characters" }
            return CounterpartyKey(key)
        }
    }
}

/**
 * Identifies which parser rule set handles a sender, e.g. `"mpesa"`, `"dtb"`, `"kcb"`.
 *
 * Declared here rather than in `:shared:sms:parser` because `FinancialSender` stores it and
 * `:shared:data:model` must not depend on the parser. The parser consumes this type.
 */
@JvmInline
value class RuleSetId private constructor(val value: String) {
    companion object : Exact<String, RuleSetId> {
        override val exactName = "RuleSetId"

        override fun Raise<String>.spec(raw: String): RuleSetId {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value.lowercase()
            ensure(trimmed.matches(SLUG)) { "'$trimmed' is not a lowercase slug" }
            return RuleSetId(trimmed)
        }

        private val SLUG = Regex("[a-z0-9-]+")
    }
}
