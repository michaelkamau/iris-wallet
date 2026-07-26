package com.iris.sms.parser

import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.RuleSetId
import com.iris.sms.parser.model.RuleId
import java.time.Instant

/**
 * The parser's success value — still pure data. Deliberately carries no account, category or
 * description: those are user/domain concerns, and keeping them out means the parser corpus
 * tests never need a repository.
 */
data class ParsedMessage(
    val ruleSet: RuleSetId,
    val rule: RuleId,
    /** null only for a standalone-fee message (FR-020). */
    val principal: ParsedAmountLine?,
    /** null when absent or reported as zero. */
    val fee: ParsedFee?,
    val time: Instant,
    val counterparty: NotBlankTrimmedString?,
    val reference: ProviderReference?,
)

data class ParsedAmountLine(
    val amount: PositiveDouble,
    /** Always KES in this release. */
    val asset: AssetCode,
    val direction: MoneyDirection,
)

data class ParsedFee(
    val amount: PositiveDouble,
    val asset: AssetCode,
    val tax: PositiveDouble?,
)
