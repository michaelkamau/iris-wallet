package com.iris.sms.parser

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.ProviderReference
import com.iris.sms.parser.model.FeeRule
import com.iris.sms.parser.model.GroupName
import com.iris.sms.parser.model.MessageRule
import com.iris.sms.parser.model.SenderRuleSet
import com.iris.sms.parser.primitive.AmountParser
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import com.iris.sms.parser.primitive.KenyanShilling
import com.iris.sms.parser.primitive.PromoTailStripper
import com.iris.sms.parser.primitive.SmsDateTimeParser
import com.iris.sms.parser.rules.RuleCatalogSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface SmsParser {
    /**
     * Total function. Never throws. Never guesses (FR-013).
     *
     * A message that is not a transaction is an ordinary `Either.Left`, not an error condition.
     */
    fun parse(message: RawSmsMessage): Either<SmsParseError, ParsedMessage>
}

/**
 * The engine. Closed for modification: providers are added as data, never as code.
 *
 * The pipeline is fixed and rule sets cannot change it — sender resolution, promo stripping,
 * exclusions, first-matching message rule, principal extraction, independent fee extraction,
 * assembly. Nothing partial is ever returned.
 */
@Singleton
class RuleDrivenSmsParser @Inject constructor(
    private val catalog: RuleCatalogSource,
    private val amountParser: AmountParser,
    private val dateTimeParser: SmsDateTimeParser,
    private val counterpartyNormalizer: CounterpartyNormalizer,
    private val promoStripper: PromoTailStripper,
) : SmsParser {

    override fun parse(message: RawSmsMessage): Either<SmsParseError, ParsedMessage> = either {
        val ruleSet = resolveSender(message)
        val body = promoStripper.strip(message.body, ruleSet.promoMarkers)
        rejectExcluded(ruleSet, body)

        val matched = ruleSet.messageRules
            .firstNotNullOfOrNull { rule -> rule.pattern.find(body)?.let { rule to it } }
        ensureNotNull(matched) { SmsParseError.NoMatchingPattern(ruleSet.id) }
        val (rule, match) = matched

        val principal = principalOf(rule, match)
        val fee = feeOf(ruleSet, body)
        // A message rule may legitimately match with no principal amount when the provider reports
        // a standalone charge (FR-020); with neither amount there is nothing to capture.
        ensure(principal != null || fee != null) {
            SmsParseError.MissingField(rule.id, RequiredField.Amount)
        }

        ParsedMessage(
            ruleSet = ruleSet.id,
            rule = rule.id,
            principal = principal,
            fee = fee,
            time = timeOf(rule, match, message.receivedAt),
            counterparty = counterpartyOf(rule, match),
            reference = referenceOf(rule, match),
        )
    }

    private fun Raise<SmsParseError>.resolveSender(message: RawSmsMessage): SenderRuleSet {
        val ruleSet = catalog.ruleSets().firstOrNull { candidate ->
            candidate.senderPatterns.any { it.containsMatchIn(message.sender.value) }
        }
        ensureNotNull(ruleSet) { SmsParseError.NoRuleForSender(message.sender) }
        return ruleSet
    }

    /** Runs before any field extraction, so an OTP or advert costs one regex and nothing else. */
    private fun Raise<SmsParseError>.rejectExcluded(ruleSet: SenderRuleSet, body: String) {
        val excluded = ruleSet.exclusions.firstOrNull { it.pattern.containsMatchIn(body) }
        if (excluded != null) {
            raise(SmsParseError.ExcludedByRule(excluded.id, excluded.reason))
        }
    }

    private fun Raise<SmsParseError>.principalOf(
        rule: MessageRule,
        match: MatchResult,
    ): ParsedAmountLine? {
        val raw = match.group(rule.pattern, rule.fields.amount) ?: return null
        val amount = amountParser.parse(raw)
            .mapLeft { SmsParseError.InvalidAmount(rule.id, raw, it) }
            .bind()
        return ParsedAmountLine(amount, KenyanShilling, rule.direction)
    }

    /**
     * Fee rules run over the same stripped body whatever message rule matched, and a cost that
     * reads `0.00` yields no fee rather than an error — `PositiveDouble` rejects it (FR-016).
     */
    private fun feeOf(ruleSet: SenderRuleSet, body: String): ParsedFee? =
        ruleSet.feeRules.firstNotNullOfOrNull { feeRule ->
            feeRule.pattern.find(body)?.let { feeFrom(feeRule, it) }
        }

    private fun feeFrom(feeRule: FeeRule, match: MatchResult): ParsedFee? {
        val amount = match.group(feeRule.pattern, feeRule.amount)
            ?.let { amountParser.parse(it).getOrNull() }
            ?: return null
        val tax = feeRule.tax
            ?.let { match.group(feeRule.pattern, it) }
            ?.let { amountParser.parse(it).getOrNull() }
        return ParsedFee(amount, KenyanShilling, tax)
    }

    private fun Raise<SmsParseError>.timeOf(
        rule: MessageRule,
        match: MatchResult,
        receivedAt: Instant,
    ): Instant {
        val rawDate = match.group(rule.pattern, rule.fields.dateTime)
        ensureNotNull(rawDate) { SmsParseError.MissingField(rule.id, RequiredField.DateTime) }
        val rawTime = rule.fields.timeOfDay?.let { match.group(rule.pattern, it) }
        return dateTimeParser
            .parse(rawDate, rawTime, rule.fields.dateTimeFormats, receivedAt)
            .mapLeft { SmsParseError.InvalidDateTime(rule.id, rawDate, it) }
            .bind()
    }

    /** An unreadable counterparty is absent, not fatal: the amount and time still make an entry. */
    private fun counterpartyOf(rule: MessageRule, match: MatchResult): NotBlankTrimmedString? =
        rule.fields.counterparty
            ?.let { match.group(rule.pattern, it) }
            ?.let { counterpartyNormalizer.normalize(it).getOrNull() }

    private fun referenceOf(rule: MessageRule, match: MatchResult): ProviderReference? =
        rule.fields.reference
            ?.let { match.group(rule.pattern, it) }
            ?.let { ProviderReference.from(it).getOrNull() }

    /**
     * Named-group lookup that cannot throw: `Matcher.group(String)` raises when the pattern never
     * declared the group, so the pattern text is checked first.
     */
    private fun MatchResult.group(pattern: Regex, name: GroupName): String? {
        val declared = pattern.pattern.contains("(?<${name.value}>")
        val named = (groups as? MatchNamedGroupCollection)?.takeIf { declared }
        return named?.get(name.value)?.value?.trim()?.takeIf(String::isNotBlank)
    }
}
