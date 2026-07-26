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
 * Diamond Trust Bank Kenya.
 *
 * DTB writes the amount *before* the currency marker (`6000.00 KES`), states the time in an
 * explicit `EAT` label that [com.iris.sms.parser.primitive.SmsDateTimeParser] strips, and follows
 * the payee with a masked account number and the receiving bank's name — none of which belong in
 * the counterparty, so the rule stops the capture at the masked fragment.
 *
 * The `Charges 59.76 KES` tail is a fee rule, so it becomes a transaction cost of its own rather
 * than disappearing into the transfer (FR-016).
 */
object DtbRules {

    /** `25 Jul 2026` + `17:41 EAT`. */
    private val SpacedDateAndClock = FieldBindings(
        amount = GroupName.unsafe("amount"),
        dateTime = GroupName.unsafe("date"),
        timeOfDay = GroupName.unsafe("time"),
        counterparty = GroupName.unsafe("counterparty"),
        reference = GroupName.unsafe("reference"),
        dateTimeFormats = persistentListOf(
            SmsDateTimeFormat.SpacedDayMonShortName,
            SmsDateTimeFormat.SlashDayMonthFullYear,
            SmsDateTimeFormat.Clock24,
            SmsDateTimeFormat.ClockMeridiem,
        ),
    )

    private val SenderPatterns = persistentListOf(
        Regex("DTB"),
        Regex("DIAMOND\\s*TRUST"),
    )

    private val PromoMarkers = persistentListOf(
        Regex("(?i)Download (?:the )?DTB"),
        Regex("(?i)Dial\\s+\\*\\d+#"),
        Regex("(?i)T\\s?&\\s?Cs? apply"),
        Regex("(?i)https?://"),
    )

    private val Exclusions = persistentListOf(
        ExclusionRule(
            id = RuleId.unsafe("dtb/otp"),
            pattern = Regex("(?i)\\b(?:OTP|one[- ]time (?:password|pin)|verification code)\\b"),
            reason = ExclusionReason.OneTimePassword,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/balance-enquiry"),
            pattern = Regex("(?i)\\b(?:available balance is|balance (?:was|enquiry|inquiry))\\b"),
            reason = ExclusionReason.BalanceEnquiry,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/failed"),
            pattern = Regex(
                "(?i)\\b(?:failed|unsuccessful|declined|could not be (?:completed|processed))\\b",
            ),
            reason = ExclusionReason.Failed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/cancelled"),
            pattern = Regex("(?i)\\bcancell?ed\\b"),
            reason = ExclusionReason.Cancelled,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/reversed"),
            pattern = Regex("(?i)\\b(?:reversed|reversal)\\b"),
            reason = ExclusionReason.Reversed,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/statement"),
            pattern = Regex("(?i)\\b(?:e-?statement|mini[- ]statement|statement request)\\b"),
            reason = ExclusionReason.Statement,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/advert"),
            pattern = Regex("(?i)\\b(?:loan limit|apply for a loan|enjoy up to|introducing)\\b"),
            reason = ExclusionReason.Promotional,
        ),
        ExclusionRule(
            id = RuleId.unsafe("dtb/zero-value"),
            pattern = Regex("(?i)\\b0(?:\\.0{1,2})?\\s*KES\\s+has been\\b"),
            reason = ExclusionReason.ZeroValue,
        ),
    )

    /**
     * `DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna 5*****5001 DIAMOND
     * TRUST BANK KENYA LTD. Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT.`
     *
     * The counterparty stops at the first masked identifier or at `Ref.`, whichever comes first,
     * so the receiving bank's name never lands in the payee (spec Edge Case "Masked identifiers").
     */
    private val SentTo = MessageRule(
        id = RuleId.unsafe("dtb/sent-to"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "DTB\\s+(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s*KES\\s+has been (?:successfully )?sent to\\s+" +
                "(?<counterparty>.+?)(?=\\s+[\\dX*]*[X*]{2,}[\\dX*]*\\b|\\s+Ref\\.|\\s+on\\s+\\d)" +
                "[\\s\\S]*?\\bRef\\.?\\s*(?<reference>[A-Z0-9]{6,20})\\s+on\\s+" +
                "(?<date>\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\s+at\\s+" +
                "(?<time>\\d{1,2}:\\d{2}(?:\\s*[AP]M)?)",
        ),
        fields = SpacedDateAndClock,
    )

    /**
     * `DTB 2500.00 KES has been debited from your account 5*****5001 for a card purchase at
     * NAIVAS SUPERMARKET. Ref. BB1CD234EF567 on 25 Jul 2026 at 12:05 EAT.`
     */
    private val Debited = MessageRule(
        id = RuleId.unsafe("dtb/debited"),
        direction = MoneyDirection.MoneyOut,
        pattern = Regex(
            "DTB\\s+(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s*KES\\s+has been debited\\b[\\s\\S]*?" +
                "\\bat\\s+(?<counterparty>.+?)(?=\\.\\s|\\s+Ref\\.|\\s+on\\s+\\d)" +
                "[\\s\\S]*?\\bRef\\.?\\s*(?<reference>[A-Z0-9]{6,20})\\s+on\\s+" +
                "(?<date>\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\s+at\\s+" +
                "(?<time>\\d{1,2}:\\d{2}(?:\\s*[AP]M)?)",
        ),
        fields = SpacedDateAndClock,
    )

    /**
     * `DTB 45000.00 KES has been credited to your account 5*****5001 from ACME LIMITED PAYROLL.
     * Ref. CC9DE876FA543 on 25 Jul 2026 at 09:02 EAT.`
     */
    private val Credited = MessageRule(
        id = RuleId.unsafe("dtb/credited"),
        direction = MoneyDirection.MoneyIn,
        pattern = Regex(
            "DTB\\s+(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s*KES\\s+has been (?:successfully )?" +
                "(?:credited|received)\\b[\\s\\S]*?\\bfrom\\s+" +
                "(?<counterparty>.+?)(?=\\.\\s|\\s+[\\dX*]*[X*]{2,}[\\dX*]*\\b|\\s+Ref\\.|\\s+on\\s+\\d)" +
                "[\\s\\S]*?\\bRef\\.?\\s*(?<reference>[A-Z0-9]{6,20})\\s+on\\s+" +
                "(?<date>\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\s+at\\s+" +
                "(?<time>\\d{1,2}:\\d{2}(?:\\s*[AP]M)?)",
        ),
        fields = SpacedDateAndClock,
    )

    /**
     * `Charges 59.76 KES`, and the `Charges KES 59.76` ordering DTB uses on some products.
     *
     * The currency marker is optional on both sides because the amount is what identifies the
     * charge; `AmountParser` strips whichever marker came along. A charge written `0.00` produces
     * no fee at all — `PositiveDouble` rejects zero (FR-016).
     */
    private val FeeRules = persistentListOf(
        FeeRule(
            id = RuleId.unsafe("dtb/charges"),
            pattern = Regex(
                "(?i)\\bcharges?\\b\\s*(?:of\\s+)?(?:(?:Ksh|KES)\\s*)?" +
                    "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s*(?:Ksh|KES)?",
            ),
            amount = GroupName.unsafe("amount"),
        ),
    )

    val ruleSet = SenderRuleSet(
        id = RuleSetId.unsafe("dtb"),
        displayName = NotBlankTrimmedString.unsafe("Diamond Trust Bank"),
        senderPatterns = SenderPatterns,
        promoMarkers = PromoMarkers,
        exclusions = Exclusions,
        messageRules = persistentListOf(SentTo, Debited, Credited),
        feeRules = FeeRules,
    )
}
