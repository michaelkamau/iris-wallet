package com.iris.sms.parser

import arrow.core.Either
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.sms.parser.corpus.CorpusCase
import com.iris.sms.parser.corpus.Expectation
import com.iris.sms.parser.corpus.SmsCorpus
import com.iris.sms.parser.model.SenderRuleSet
import com.iris.sms.parser.primitive.AmountParser
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import com.iris.sms.parser.primitive.PromoTailStripper
import com.iris.sms.parser.primitive.SmsDateTimeParser
import com.iris.sms.parser.rules.CompiledRuleCatalogSource
import com.iris.sms.parser.rules.DtbRules
import com.iris.sms.parser.rules.KcbRules
import com.iris.sms.parser.rules.MpesaRules
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

/**
 * The executable form of SC-001 and SC-004: every message the app claims to understand is listed
 * in [SmsCorpus] with the exact result it must produce, and every message it must decline is
 * listed with the exact reason.
 *
 * Each provider rule set brings its own rows, so a regression in the engine shows up as a named
 * failing case rather than as a mystery in the field. The coverage test below is what stops a
 * green run from ever meaning "the corpus was empty".
 */
class SmsParserCorpusTest {

    private val parser: SmsParser = RuleDrivenSmsParser(
        catalog = CompiledRuleCatalogSource(RuleSets),
        amountParser = AmountParser(),
        dateTimeParser = SmsDateTimeParser(),
        counterpartyNormalizer = CounterpartyNormalizer(),
        promoStripper = PromoTailStripper(),
    )

    @Test
    fun `every corpus case parses exactly as recorded`() {
        // given
        val cases = SmsCorpus.cases

        // when
        val outcomes = cases.map { case -> case to parser.parse(case.toMessage()) }

        // then
        outcomes.forEach { (case, result) ->
            withClue("corpus case '${case.name}'") { assertMatches(case, result) }
        }
    }

    @Test
    fun `the corpus exercises every registered rule set, positively and negatively`() {
        // given
        val cases = SmsCorpus.cases

        // when
        val outcomes = cases.map { case -> parser.parse(case.toMessage()) }

        // then a green run can never mean "the corpus was empty"
        withClue("positive cases") {
            outcomes.count { it.isRight() } shouldBe cases.count { it.expected is Expectation.Parsed }
        }
        withClue("rule sets exercised") {
            outcomes.mapNotNull { it.getOrNull()?.ruleSet?.value }.toSet() shouldBe
                RuleSets.map { it.id.value }.toSet()
        }
        withClue("exclusion reasons exercised") {
            cases.expectationsOf<Expectation.Rejected>().map { it.reason }.toSet() shouldBe
                ExclusionReason.entries.toSet()
        }
    }

    @Test
    fun `an unknown sender is declined rather than guessed`() {
        // given
        val message = RawSmsMessage(
            sender = SenderId.unsafe("SOMERANDOMSHOP"),
            body = "Your order is on its way.",
            receivedAt = Instant.parse("2026-07-25T16:50:00Z"),
        )

        // when
        val result = parser.parse(message)

        // then
        result.shouldBeLeft() shouldBe SmsParseError.NoRuleForSender(message.sender)
    }

    private fun assertMatches(
        case: CorpusCase,
        result: Either<SmsParseError, ParsedMessage>,
    ) = when (val expected = case.expected) {
        is Expectation.Parsed -> result.shouldBeRight().summary() shouldBe expected
        is Expectation.Rejected -> reasonOf(result.shouldBeLeft()) shouldBe expected.reason
        is Expectation.NotMatched ->
            result.shouldBeLeft() shouldBe expected.toError(case.sender)
    }

    /**
     * An excluded message must be excluded *by name*: asserting the reason rather than merely
     * "some Left" is what stops a loosened rule from silently reclassifying an OTP as an unseen
     * wording (SC-004).
     */
    private fun reasonOf(error: SmsParseError): ExclusionReason? =
        (error as? SmsParseError.ExcludedByRule)?.reason

    /**
     * The two "nothing at all" outcomes are asserted as exact values, so an unseen wording can
     * never be confused with an unclaimed sender — nor with a half-filled parse (FR-013).
     */
    private fun Expectation.NotMatched.toError(sender: String): SmsParseError = when (ruleSet) {
        null -> SmsParseError.NoRuleForSender(SenderId.unsafe(sender))
        else -> SmsParseError.NoMatchingPattern(RuleSetId.unsafe(ruleSet))
    }

    private fun ParsedMessage.summary() = Expectation.Parsed(
        amount = principal?.amount?.value,
        direction = principal?.direction,
        counterparty = counterparty?.value,
        reference = reference?.value,
        timeIso = time.toString(),
        fee = fee?.amount?.value,
        tax = fee?.tax?.value,
    )

    /** The expectations of a given shape, so a coverage assertion reads as one line. */
    private inline fun <reified T : Expectation> List<CorpusCase>.expectationsOf(): List<T> =
        mapNotNull { it.expected as? T }

    private fun CorpusCase.toMessage() = RawSmsMessage(
        sender = SenderId.unsafe(sender),
        body = body,
        receivedAt = receivedAt,
    )

    private companion object {
        /**
         * Every rule set the app ships, wired here exactly as `SmsRuleCatalogModule` wires them
         * at runtime — so a provider that is registered but never exercised, or exercised but
         * never registered, fails here rather than in the field.
         */
        val RuleSets: Set<SenderRuleSet> = setOf(
            MpesaRules.ruleSet,
            DtbRules.ruleSet,
            KcbRules.ruleSet,
        )
    }
}
