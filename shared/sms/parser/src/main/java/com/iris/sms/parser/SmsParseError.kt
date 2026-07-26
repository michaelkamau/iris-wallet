package com.iris.sms.parser

import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.sms.parser.model.RuleId

/**
 * Every way parsing can decline to produce a [ParsedMessage].
 *
 * A message that simply is not a transaction is an ordinary `Either.Left` here, never an
 * exception (FR-013).
 *
 * Callers must log [ExcludedByRule] and [NoMatchingPattern] at `Timber.d` with the rule id only —
 * they are *expected* outcomes. [InvalidAmount], [InvalidDateTime] and [InvalidValue] indicate a
 * rule that matched but then failed, and are logged at `Timber.w` with the rule id and the
 * offending token only, never the body (FR-006).
 */
sealed interface SmsParseError {
    data class NoRuleForSender(val sender: SenderId) : SmsParseError
    data class NoMatchingPattern(val ruleSet: RuleSetId) : SmsParseError
    data class ExcludedByRule(val rule: RuleId, val reason: ExclusionReason) : SmsParseError
    data class MissingField(val rule: RuleId, val field: RequiredField) : SmsParseError
    data class InvalidAmount(val rule: RuleId, val raw: String, val cause: String) : SmsParseError
    data class InvalidDateTime(val rule: RuleId, val raw: String, val cause: String) : SmsParseError
    data class InvalidValue(
        val rule: RuleId,
        val field: RequiredField,
        val cause: String,
    ) : SmsParseError
}

enum class ExclusionReason {
    Promotional,
    BalanceEnquiry,
    OneTimePassword,
    Failed,
    Cancelled,
    Reversed,
    ZeroValue,
    Statement,
}

enum class RequiredField { Amount, DateTime, Direction, Counterparty, Reference }
