package com.iris.domain.usecase.sms

import com.iris.data.model.AccountId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.sms.parser.ParsedAmountLine
import com.iris.sms.parser.ParsedFee
import com.iris.sms.parser.ParsedMessage
import com.iris.sms.parser.RawSmsMessage
import com.iris.sms.parser.model.RuleId
import java.time.Instant
import java.util.UUID

/**
 * Shared fixtures for the SMS use-case tests.
 *
 * They exist so each test states only the one thing it is about: a test named "a disabled sender
 * is refused" should not also have to spell out an asset code and a rule id.
 */
internal object SmsFixtures {

    val Kes: AssetCode = AssetCode.unsafe("KES")
    val Mpesa: SenderId = SenderId.unsafe("MPESA")
    val Account: AccountId = AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    val OtherAccount: AccountId =
        AccountId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"))

    /** 25 Jul 2026, 19:50 in Nairobi — the instant of the M-PESA sample in spec.md. */
    val PaidAt: Instant = Instant.parse("2026-07-25T16:50:00Z")
    val CapturedAt: Instant = Instant.parse("2026-07-25T16:50:05Z")

    fun sender(
        account: AccountId? = Account,
        enabled: Boolean = true,
        ruleSet: RuleSetId? = RuleSetId.unsafe("mpesa"),
    ) = FinancialSender(
        id = Mpesa,
        displayName = NotBlankTrimmedString.unsafe("M-PESA"),
        ruleSet = ruleSet,
        account = account,
        enabled = enabled,
    )

    fun message(
        body: String = "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to James Kinyua on 25/7/26 at 7:50 PM.",
    ) = RawSmsMessage(sender = Mpesa, body = body, receivedAt = PaidAt)

    /**
     * @param amount null makes the message a standalone charge, which is the one shape that
     *   reaches capture with no principal at all (FR-020).
     * @param fee null is the `Transaction cost, Ksh0.00` case as much as the "no cost reported"
     *   one — the parser cannot tell them apart, and neither must the ledger (Acceptance 2.3).
     */
    fun parsed(
        amount: Double? = 1_350.0,
        direction: MoneyDirection = MoneyDirection.MoneyOut,
        reference: String? = "UGP7B0ITE4",
        counterparty: String? = "James Kinyua",
        fee: Double? = null,
        tax: Double? = null,
    ) = ParsedMessage(
        ruleSet = RuleSetId.unsafe("mpesa"),
        rule = RuleId.unsafe("mpesa/paid-to"),
        principal = amount?.let {
            ParsedAmountLine(
                amount = PositiveDouble.unsafe(it),
                asset = Kes,
                direction = direction,
            )
        },
        fee = fee?.let {
            ParsedFee(
                amount = PositiveDouble.unsafe(it),
                asset = Kes,
                tax = tax?.let(PositiveDouble::unsafe),
            )
        },
        time = PaidAt,
        counterparty = counterparty?.let(NotBlankTrimmedString::unsafe),
        reference = reference?.let(ProviderReference::unsafe),
    )

    fun captured(
        id: CapturedTransactionId = CapturedTransactionId(UUID.randomUUID()),
        account: AccountId? = Account,
        amount: Double = 1_350.0,
        direction: MoneyDirection = MoneyDirection.MoneyOut,
        reference: String? = "UGP7B0ITE4",
        counterparty: String? = "James Kinyua",
        description: String? = null,
        kind: CapturedKind = CapturedKind.Principal(direction),
    ) = CapturedTransaction(
        id = id,
        sender = Mpesa,
        kind = kind,
        amount = PositiveDouble.unsafe(amount),
        asset = Kes,
        time = PaidAt,
        counterparty = counterparty?.let(NotBlankTrimmedString::unsafe),
        reference = reference?.let(ProviderReference::unsafe),
        account = account,
        category = null,
        description = description?.let(NotBlankTrimmedString::unsafe),
        duplicateOf = null,
        capturedAt = CapturedAt,
        origin = CaptureOrigin.LiveBroadcast,
    )
}

/** The transaction cost captured beside [SmsFixtures.captured], as `CaptureSmsUseCase` writes it. */
internal fun SmsFixtures.fee(
    parent: CapturedTransactionId?,
    amount: Double = 59.76,
    tax: Double? = null,
    id: CapturedTransactionId = CapturedTransactionId(UUID.randomUUID()),
) = captured(
    id = id,
    amount = amount,
    kind = CapturedKind.Fee(parent = parent, tax = tax?.let(PositiveDouble::unsafe)),
)
