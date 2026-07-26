package com.iris.sms.parser.rules

import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.RuleSetId
import com.iris.sms.parser.ExclusionReason
import com.iris.sms.parser.model.ExclusionRule
import com.iris.sms.parser.model.FeeRule
import com.iris.sms.parser.model.FieldBindings
import com.iris.sms.parser.model.GroupName
import com.iris.sms.parser.model.MessageRule
import com.iris.sms.parser.model.RuleId
import com.iris.sms.parser.model.SenderRuleSet
import com.iris.sms.parser.primitive.SmsDateTimeFormat
import kotlinx.collections.immutable.persistentListOf

/**
 * Kenya Commercial Bank.
 *
 * The card/POS alert carries no reference code and no time of day at all — only `on 25/07/2026` —
 * which is exactly the case [com.iris.sms.parser.primitive.SmsDateTimeParser]'s date-only fallback
 * exists for. It also arrives with the amount glued to the following word (`KES 540.25for a POS
 * PURCHASE`), so the amount pattern must not require trailing whitespace.
 *
 * The merchant line appends the merchant's town and country (`GITHUB, INC. SAN FRANCISCO CA`);
 * the counterparty stops at the first sentence break so the location stays out of the payee name
 * (spec Edge Case "Foreign merchants").
 */
object KcbRules {

    /** `25/07/2026`, with no time of day anywhere in the message. */
    private val DateOnly = FieldBindings(
        amount = GroupName.unsafe("amount"),
        dateTime = GroupName.unsafe("date"),
        counterparty = GroupName.unsafe("counterparty"),
        dateTimeFormats = persistentListOf(
            SmsDateTimeFormat.SlashDayMonthFullYear,
            SmsDateTimeFormat.SlashDayMonthShortYear,
        ),
    )

    /**
     * The bindings for a message that reports *only* a charge (FR-020).
     *
     * `amount` still names a group, because [FieldBindings] has nowhere to say "there is no
     * principal" — but [ChargeNotification]'s pattern never declares it, and the engine's
     * named-group lookup checks the pattern text first, so the principal comes back as `null`
     * rather than as a guess. The charge itself is read by the fee rules, exactly as it is on a
     * message that also carries a transfer.
     */
    private val ChargeDateOnly = FieldBindings(
        amount = GroupName.unsafe("amount"),
        dateTime = GroupName.unsafe("date"),
        dateTimeFormats = persistentListOf(
            SmsDateTimeFormat.SlashDayMonthFullYear,
            SmsDateTimeFormat.SlashDayMonthShortYear,
        ),
    )

    private val SenderPatterns = persistentListOf(
        Regex("KCB"),
        Regex("KENYA\\s*COMMERCIAL"),
    )

    private val PromoMarkers = persistentListOf(
        Regex("(?i)Pata\\s+extra\\s+cash"),
        Regex("(?i)Dial\\s+\\*\\d+#"),
        Regex("(?i)use the KCB App"),
        Regex("(?i)T\\s?&\\s?Cs? apply"),
        Regex("(?i)https?://"),
    )

    private val Exclusions = persistentListOf(
        ExclusionRule(
            id = RuleId.unsafe("kcb/otp"),
            pattern = Regex("(?i)\\b(?:OTP|one[- ]time (?:password|pin)|verification code)\\b"),
            reason = ExclusionReason.OneTimePassword,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/balance-enquiry"),
            pattern = Regex("(?i)\\b(?:available balance is|balance (?:was|enquiry|inquiry))\\b"),
            reason = ExclusionReason.BalanceEnquiry,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/failed"),
            pattern = Regex(
                "(?i)\\b(?:failed|unsuccessful|declined|could not be (?:completed|processed))\\b",
            ),
            reason = ExclusionReason.Failed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/cancelled"),
            pattern = Regex("(?i)\\bcancell?ed\\b"),
            reason = ExclusionReason.Cancelled,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/reversed"),
            pattern = Regex("(?i)\\b(?:reversed|reversal)\\b"),
            reason = ExclusionReason.Reversed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/statement"),
            pattern = Regex("(?i)\\b(?:e-?statement|mini[- ]statement|statement request)\\b"),
            reason = ExclusionReason.Statement,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/advert"),
            pattern = Regex("(?i)\\b(?:loan limit|limit is now|Mobile Loan|check limit)\\b"),
            reason = ExclusionReason.Promotional,
        ),
        ExclusionRule(
            id = RuleId.unsafe("kcb/zero-value"),
            pattern = Regex("(?i)\\b(?:Ksh|KES)\\s*0(?:\\.0{1,2})?\\s*for\\b"),
            reason = ExclusionReason.ZeroValue,
        ),
    )

    /**
     * `ALERT: Your account no. 5XXXXX5001 has been debited with KES 540.25for a POS PURCHASE at
     * GITHUB, INC. SAN FRANCISCO CA on 25/07/2026.`
     */
    private val Debited = MessageRule(
        id = RuleId.unsafe("kcb/debited"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "has been debited with\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)" +
                "\\s*for\\s+[\\s\\S]*?\\bat\\s+" +
                "(?<counterparty>.+?)(?=\\.\\s|\\s+on\\s+\\d)" +
                "[\\s\\S]*?\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})",
        ),
        fields = DateOnly,
    )

    /**
     * `ALERT: Your account no. 5XXXXX5001 has been credited with KES 25,000.00 from SALARY
     * PAYMENT ACME LTD on 25/07/2026.`
     */
    private val Credited = MessageRule(
        id = RuleId.unsafe("kcb/credited"),
        direction = MoneyDirection.MoneyIn,
        pattern = Regex(
            "has been credited with\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)" +
                "\\s*(?:from|by)\\s+" +
                "(?<counterparty>.+?)(?=\\.\\s|\\s+on\\s+\\d)" +
                "[\\s\\S]*?\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})",
        ),
        fields = DateOnly,
    )

    /**
     * The KCB app's M-PESA transfer confirmation. The body is byte-for-byte the one M-PESA sends
     * for the same event, so the pattern is shared with [MpesaRules] rather than re-derived —
     * only the rule id differs, which keeps the two providers' diagnostics apart.
     */
    private val SendToMpesaRequest = MessageRule(
        id = RuleId.unsafe("kcb/send-to-mpesa-request"),
        direction = MoneyDirection.MoneyOut,
        pattern = MpesaRules.SendToMpesaRequestPattern,
        fields = MpesaRules.SendToMpesaRequestFields,
    )

    /**
     * A charge advice that arrives as its own message, with no transfer anywhere in it:
     * `ALERT: A charge of KES 33.00 has been applied to your account no. 5XXXXX5001 for a MOBILE
     * MONEY TRANSFER on 25/07/2026.`
     *
     * It declares no `amount` group on purpose — see [ChargeDateOnly]. The charge is the only
     * money in the message, so it is read by [FeeRules] and becomes a standalone transaction-cost
     * expense with no principal to hang off (FR-020, spec Edge Case "Ambiguous fee attribution").
     */
    private val ChargeNotification = MessageRule(
        id = RuleId.unsafe("kcb/charge-notification"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "(?i)\\b(?:charge|transaction cost|levy|excise duty)\\b[\\s\\S]*?" +
                "\\baccount(?:\\s+no\\.?)?\\s+[\\dX*]+\\b[\\s\\S]*?" +
                "\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})",
        ),
        fields = ChargeDateOnly,
    )

    /**
     * `Transaction cost KES 66.00 Incl. Tax Amount KES 8.25` reaches this sender byte-for-byte as
     * it reaches M-PESA, so the patterns are shared rather than re-derived — only the rule ids
     * differ, which keeps the two providers' diagnostics apart.
     *
     * `kcb/charge` covers the standalone advice wording ([ChargeNotification]) and the `levy` /
     * `excise duty` variants KCB uses for the same thing.
     */
    private val FeeRules = persistentListOf(
        FeeRule(
            id = RuleId.unsafe("kcb/transaction-cost-with-tax"),
            pattern = MpesaRules.TransactionCostWithTaxPattern,
            amount = GroupName.unsafe("amount"),
            tax = GroupName.unsafe("tax"),
        ),
        FeeRule(
            id = RuleId.unsafe("kcb/transaction-cost"),
            pattern = MpesaRules.TransactionCostPattern,
            amount = GroupName.unsafe("amount"),
        ),
        FeeRule(
            id = RuleId.unsafe("kcb/charge"),
            pattern = Regex(
                "(?i)\\b(?:charges?|levy|excise duty)\\s+of\\s+(?:Ksh|KES)\\s*" +
                    "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)",
            ),
            amount = GroupName.unsafe("amount"),
        ),
    )

    val ruleSet = SenderRuleSet(
        id = RuleSetId.unsafe("kcb"),
        displayName = NotBlankTrimmedString.unsafe("KCB Bank"),
        senderPatterns = SenderPatterns,
        promoMarkers = PromoMarkers,
        exclusions = Exclusions,
        // The charge advice is tried last: its wording is a fragment of every message that
        // reports a cost alongside a transfer, so any earlier position would steal them.
        messageRules = persistentListOf(SendToMpesaRequest, Debited, Credited, ChargeNotification),
        feeRules = FeeRules,
    )
}
