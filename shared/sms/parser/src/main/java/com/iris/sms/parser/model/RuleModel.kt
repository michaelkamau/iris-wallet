package com.iris.sms.parser.model

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.iris.data.model.exact.Exact
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.RuleSetId
import com.iris.sms.parser.ExclusionReason
import com.iris.sms.parser.primitive.SmsDateTimeFormat
import kotlinx.collections.immutable.ImmutableList

/**
 * Everything the engine knows about one provider. Rules are **data, not code**: adding a provider
 * or a new wording is a new [SenderRuleSet] value plus one `@Provides @IntoSet` line, and the
 * engine is never edited.
 *
 * The shape stays serialisable-friendly on purpose (a `Regex` is constructed from a `String`), so
 * a future asset-backed catalog can load these values from JSON without changing the engine.
 */
data class SenderRuleSet(
    val id: RuleSetId,
    val displayName: NotBlankTrimmedString,
    /** Any match makes this rule set responsible for the sender. */
    val senderPatterns: ImmutableList<Regex>,
    /** Truncation points for promotional/instructional tails (FR-011). */
    val promoMarkers: ImmutableList<Regex>,
    val exclusions: ImmutableList<ExclusionRule>,
    val messageRules: ImmutableList<MessageRule>,
    val feeRules: ImmutableList<FeeRule>,
)

data class ExclusionRule(
    val id: RuleId,
    val pattern: Regex,
    val reason: ExclusionReason,
)

data class MessageRule(
    val id: RuleId,
    val direction: MoneyDirection,
    /** Must use named groups matching the names declared in [fields]. */
    val pattern: Regex,
    val fields: FieldBindings,
)

data class FieldBindings(
    val amount: GroupName,
    val dateTime: GroupName,
    val timeOfDay: GroupName? = null,
    val counterparty: GroupName? = null,
    val reference: GroupName? = null,
    /** Formats tried in order against the concatenated date + time text. */
    val dateTimeFormats: ImmutableList<SmsDateTimeFormat>,
)

data class FeeRule(
    val id: RuleId,
    val pattern: Regex,
    val amount: GroupName,
    val tax: GroupName? = null,
)

/** The name of a regex capture group, e.g. `amount`. Java allows only `[a-zA-Z][a-zA-Z0-9]*`. */
@JvmInline
value class GroupName private constructor(val value: String) {
    companion object : Exact<String, GroupName> {
        override val exactName = "GroupName"

        override fun Raise<String>.spec(raw: String): GroupName {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value
            ensure(trimmed.matches(GROUP)) { "'$trimmed' is not a valid regex group name" }
            return GroupName(trimmed)
        }

        private val GROUP = Regex("[a-zA-Z][a-zA-Z0-9]*")
    }
}

/** Parser-internal rule identifier: `"<ruleSetId>/<slug>"`, e.g. `"mpesa/paid-to"`. */
@JvmInline
value class RuleId private constructor(val value: String) {
    companion object : Exact<String, RuleId> {
        override val exactName = "RuleId"

        override fun Raise<String>.spec(raw: String): RuleId {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value.lowercase()
            ensure(trimmed.matches(QUALIFIED)) { "'$trimmed' is not '<ruleSetId>/<slug>'" }
            return RuleId(trimmed)
        }

        private val QUALIFIED = Regex("[a-z0-9-]+/[a-z0-9-]+")
    }
}
