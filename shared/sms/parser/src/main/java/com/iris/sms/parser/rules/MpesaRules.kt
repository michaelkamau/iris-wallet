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
 * Safaricom M-PESA (FR-004, FR-007, FR-008, FR-011, FR-012, FR-016, FR-019).
 *
 * The patterns are written against the *real* wordings in spec.md, quirks included: `Ksh1,350.00`
 * with no space after the currency marker, `7:50 PM.New M-PESA balance` with no space after the
 * full stop, and payee names carrying trailing noise (`james Kinyua Mwangi9.`) that
 * `CounterpartyNormalizer` cleans up rather than the rule.
 *
 * Case sensitivity is deliberate: these bodies are machine-generated, and a case-insensitive
 * reference class would happily match ordinary lowercase words.
 */
object MpesaRules {

    /**
     * The `on <date> at <time>` shape shared by every classic M-PESA confirmation.
     *
     * Declared first because an `object`'s properties initialise in declaration order.
     */
    private val DateAndClock = FieldBindings(
        amount = GroupName.unsafe("amount"),
        dateTime = GroupName.unsafe("date"),
        timeOfDay = GroupName.unsafe("time"),
        counterparty = GroupName.unsafe("counterparty"),
        reference = GroupName.unsafe("reference"),
        dateTimeFormats = persistentListOf(
            SmsDateTimeFormat.SlashDayMonthShortYear,
            SmsDateTimeFormat.SlashDayMonthFullYear,
            SmsDateTimeFormat.ClockMeridiem,
        ),
    )

    /**
     * `M-PESA`, `MPESA`, and the Safaricom short codes that carry confirmations.
     *
     * Matching is a "contains", so `SAFARICOM-MPESA` resolves here too.
     */
    private val SenderPatterns = persistentListOf(
        Regex("M-?PESA"),
        Regex("SAFARICOM"),
        Regex("^(?:2547\\d{8}|4[05]\\d{2})$"),
    )

    /**
     * Truncation points for the promotional tails Safaricom appends to real confirmations
     * (FR-011). Stripping happens before matching, so nothing here can reach a counterparty,
     * a description or an exclusion.
     */
    private val PromoMarkers = persistentListOf(
        Regex("(?i)Download (?:the )?My\\s?OneApp"),
        Regex("(?i)Pata\\s+extra\\s+cash"),
        Regex("(?i)Dial\\s+\\*\\d+#"),
        Regex("(?i)https?://"),
    )

    /**
     * Everything M-PESA sends that is *not* a transaction (FR-012, spec Edge Cases).
     *
     * Each pattern is anchored on wording a confirmation cannot contain: a confirmation says
     * `New M-PESA balance is`, never `balance was`, so [BalanceEnquiry] cannot swallow one.
     */
    private val Exclusions = persistentListOf(
        ExclusionRule(
            id = RuleId.unsafe("mpesa/otp"),
            pattern = Regex("(?i)\\b(?:OTP|one[- ]time (?:password|pin)|verification code)\\b"),
            reason = ExclusionReason.OneTimePassword,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/balance-enquiry"),
            pattern = Regex("(?i)\\bbalance (?:was|enquiry|inquiry)\\b"),
            reason = ExclusionReason.BalanceEnquiry,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/failed"),
            pattern = Regex(
                "(?i)\\b(?:failed|unsuccessful|not successful|could not be (?:completed|processed))\\b",
            ),
            reason = ExclusionReason.Failed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/cancelled"),
            pattern = Regex("(?i)\\bcancell?ed\\b"),
            reason = ExclusionReason.Cancelled,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/reversed"),
            pattern = Regex("(?i)\\b(?:reversed|reversal)\\b"),
            reason = ExclusionReason.Reversed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/statement"),
            pattern = Regex("(?i)\\b(?:mini[- ])?statement\\b"),
            reason = ExclusionReason.Statement,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/advert"),
            pattern = Regex("(?i)\\b(?:Fuliza|M-?Shwari|loan limit|limit is now|borrow)\\b"),
            reason = ExclusionReason.Promotional,
        ),
        ExclusionRule(
            id = RuleId.unsafe("mpesa/zero-value"),
            pattern = Regex(
                "(?i)\\b(?:Ksh|KES)\\s*0(?:\\.0{1,2})?\\s+(?:paid|sent|received|to|from)\\b",
            ),
            reason = ExclusionReason.ZeroValue,
        ),
    )

    /**
     * `UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM.`
     *
     * The counterparty runs lazily up to ` on <date>` so a payee with internal punctuation
     * survives; the trailing `9.` is left for [com.iris.sms.parser.primitive.CounterpartyNormalizer].
     */
    private val PaidTo = MessageRule(
        id = RuleId.unsafe("mpesa/paid-to"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "(?<reference>[A-Z0-9]{8,12})\\s+Confirmed\\.?\\s+" +
                "(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+paid to\\s+" +
                "(?<counterparty>.+?)\\s+on\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})" +
                "\\s+at\\s+(?<time>\\d{1,2}:\\d{2}\\s*[AP]M)",
        ),
        fields = DateAndClock,
    )

    /** `UGO7B0DQ91 Confirmed. Ksh500.00 sent to JANE DOE 0722000000 on 24/7/26 at 4:10 PM.` */
    private val SentTo = MessageRule(
        id = RuleId.unsafe("mpesa/sent-to"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "(?<reference>[A-Z0-9]{8,12})\\s+Confirmed\\.?\\s+" +
                "(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+sent to\\s+" +
                "(?<counterparty>.+?)(?=\\s+\\d|\\s+on\\s+\\d)[\\s\\S]*?" +
                "\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})" +
                "\\s+at\\s+(?<time>\\d{1,2}:\\d{2}\\s*[AP]M)",
        ),
        fields = DateAndClock,
    )

    /**
     * The bank-app-originated wording, whose timestamp is a full ISO date-time and whose reference
     * sits *after* it: `… request of KES 13,000.00 … at 2026-07-24 07:19:54 PM … M-PESA REF: …`.
     *
     * Shared with [KcbRules], because the identical body reaches the phone from either sender
     * depending on which app initiated the transfer.
     */
    internal val SendToMpesaRequestPattern = Regex(
        "SEND TO M-PESA request of\\s+(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+" +
            "from\\s+[\\d*X]+\\s+to\\s+[\\d*X]+\\s*-\\s*" +
            "(?<counterparty>.+?)\\s+at\\s+" +
            "(?<dateTime>\\d{4}-\\d{2}-\\d{2}\\s+\\d{1,2}:\\d{2}:\\d{2}\\s*[AP]M)" +
            "[\\s\\S]*?M-PESA REF:\\s*(?<reference>[A-Z0-9]{6,20})",
    )

    /** Formats and group names for [SendToMpesaRequestPattern]; the date and time arrive together. */
    internal val SendToMpesaRequestFields = FieldBindings(
        amount = GroupName.unsafe("amount"),
        dateTime = GroupName.unsafe("dateTime"),
        counterparty = GroupName.unsafe("counterparty"),
        reference = GroupName.unsafe("reference"),
        dateTimeFormats = persistentListOf(SmsDateTimeFormat.IsoDateTimeMeridiem),
    )

    private val SendToMpesaRequest = MessageRule(
        id = RuleId.unsafe("mpesa/send-to-mpesa-request"),
        direction = MoneyDirection.MoneyOut,
        pattern = SendToMpesaRequestPattern,
        fields = SendToMpesaRequestFields,
    )

    /** `RGP7B0ITE5 Confirmed. You have received Ksh2,500.00 from JOHN DOE 254712345678 on …` */
    private val Received = MessageRule(
        id = RuleId.unsafe("mpesa/received"),
        direction = MoneyDirection.MoneyIn,
        pattern = Regex(
            "(?<reference>[A-Z0-9]{8,12})\\s+Confirmed\\.?\\s*(?:You have )?received\\s+" +
                "(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+from\\s+" +
                "(?<counterparty>.+?)(?=\\s+\\d|\\s+on\\s+\\d)[\\s\\S]*?" +
                "\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})" +
                "\\s+at\\s+(?<time>\\d{1,2}:\\d{2}\\s*[AP]M)",
        ),
        fields = DateAndClock,
    )

    /** `RGP7B0ITE6 Confirmed. Give Ksh1,000.00 cash to … deposit of Ksh1,000.00 …` */
    private val Deposit = MessageRule(
        id = RuleId.unsafe("mpesa/deposit"),
        direction = MoneyDirection.MoneyIn,
        pattern = Regex(
            "(?<reference>[A-Z0-9]{8,12})\\s+Confirmed\\.?\\s*(?:You have )?deposited?\\s+" +
                "(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s+(?:in)?to\\s+" +
                "(?<counterparty>.+?)(?=\\s+\\d|\\s+on\\s+\\d)[\\s\\S]*?" +
                "\\bon\\s+(?<date>\\d{1,2}/\\d{1,2}/\\d{2,4})" +
                "\\s+at\\s+(?<time>\\d{1,2}:\\d{2}\\s*[AP]M)",
        ),
        fields = DateAndClock,
    )

    /**
     * The tax-bearing wording, shared with [KcbRules] because the `SEND TO M-PESA` body reaches
     * the phone from either sender: `Transaction cost KES 66.00 Incl. Tax Amount KES 8.25.`
     *
     * Both amounts are bound, and the tax stays *detail on the fee* — it is never a second amount
     * the engine could turn into a third transaction (FR-019).
     */
    internal val TransactionCostWithTaxPattern = Regex(
        "Transaction cost[,:]?\\s*(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{1,2})?)" +
            "\\s*Incl\\.?\\s*Tax(?:\\s+Amount)?[,:]?\\s*(?:Ksh|KES)\\s*" +
            "(?<tax>[\\d,]+(?:\\.\\d{1,2})?)",
    )

    /**
     * The plain wording: `Transaction cost, Ksh0.00.`
     *
     * `Ksh0.00` needs no special case anywhere. [com.iris.sms.parser.primitive.AmountParser] hands
     * it to `PositiveDouble`, which rejects zero, so the rule simply yields no fee and no
     * transaction-cost row is ever created (FR-016, Acceptance 2.3).
     */
    internal val TransactionCostPattern = Regex(
        "Transaction cost[,:]?\\s*(?:Ksh|KES)\\s*(?<amount>[\\d,]+(?:\\.\\d{1,2})?)",
    )

    /**
     * Order matters exactly as it does for message rules: the tax-bearing wording is a prefix of
     * the plain one, so it has to be tried first or the tax would silently never be captured.
     */
    private val FeeRules = persistentListOf(
        FeeRule(
            id = RuleId.unsafe("mpesa/transaction-cost-with-tax"),
            pattern = TransactionCostWithTaxPattern,
            amount = GroupName.unsafe("amount"),
            tax = GroupName.unsafe("tax"),
        ),
        FeeRule(
            id = RuleId.unsafe("mpesa/transaction-cost"),
            pattern = TransactionCostPattern,
            amount = GroupName.unsafe("amount"),
        ),
    )

    val ruleSet = SenderRuleSet(
        id = RuleSetId.unsafe("mpesa"),
        displayName = NotBlankTrimmedString.unsafe("M-PESA"),
        senderPatterns = SenderPatterns,
        promoMarkers = PromoMarkers,
        exclusions = Exclusions,
        // Order matters: the engine takes the first match, so the narrower `paid to` wording is
        // tried before the more permissive transfer wordings.
        messageRules = persistentListOf(
            PaidTo,
            SentTo,
            SendToMpesaRequest,
            Received,
            Deposit,
        ),
        feeRules = FeeRules,
    )
}
