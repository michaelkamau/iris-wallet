package com.iris.sms.parser.corpus

import com.iris.data.model.sms.MoneyDirection
import com.iris.sms.parser.ExclusionReason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant

/**
 * One real message and exactly what the parser must make of it.
 *
 * [receivedAt] is always a fixed literal — never `Instant.now()` — so a case that depends on the
 * date-without-time fallback stays deterministic for ever.
 */
data class CorpusCase(
    val name: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val expected: Expectation,
)

/**
 * Stated in plain values rather than parser types so a case reads like the message it came from,
 * and so a contributor adding a provider never has to construct a `ParsedMessage` by hand.
 */
sealed interface Expectation {
    data class Parsed(
        val amount: Double?,
        val direction: MoneyDirection?,
        val counterparty: String?,
        val reference: String?,
        /** e.g. `"2026-07-25T16:50:00Z"` — 19:50 in Nairobi. */
        val timeIso: String,
        val fee: Double?,
        val tax: Double?,
    ) : Expectation

    /**
     * @param reason the exclusion the message must trip.
     */
    data class Rejected(val reason: ExclusionReason) : Expectation

    /**
     * The message reached no rule at all — very different from a half-filled parse (SC-004).
     *
     * @param ruleSet the rule set that took responsibility for the sender but had no wording for
     *   the body (`NoMatchingPattern`), or null when no rule set claims the sender at all
     *   (`NoRuleForSender`).
     */
    data class NotMatched(val ruleSet: String?) : Expectation
}

/**
 * The registry `SmsParserCorpusTest` iterates.
 *
 * Each provider contributes its own file, so a rule always arrives together with the messages
 * that justify it (contracts/sms-parser-contract.md §6).
 */
object SmsCorpus {
    val cases: ImmutableList<CorpusCase> = buildList {
        addAll(MpesaCorpus.cases)
        addAll(DtbCorpus.cases)
        addAll(KcbCorpus.cases)
        addAll(NegativeCorpus.cases)
    }.toImmutableList()
}
