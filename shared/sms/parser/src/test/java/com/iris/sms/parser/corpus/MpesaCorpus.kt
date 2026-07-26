package com.iris.sms.parser.corpus

import com.iris.data.model.sms.MoneyDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * Real M-PESA bodies from spec.md, copied verbatim — quirks included.
 *
 * Nothing here is tidied: `Ksh1,350.00` has no space after the marker, `7:50 PM.New` has none
 * after the full stop, `Mwangi9.` carries trailing noise and the promotional tail is left on. A
 * case that has been cleaned up first proves nothing about the message that actually arrives.
 *
 * Two of these rows state `fee = null` for a reason worth naming: they report
 * `Transaction cost, Ksh0.00`, and a zero cost must create *nothing at all* (FR-012,
 * Acceptance 2.3). That is not a filter in the engine — `PositiveDouble` cannot hold zero, so a
 * zero fee is unrepresentable rather than merely discarded.
 */
object MpesaCorpus {

    val cases: ImmutableList<CorpusCase> = persistentListOf(
        CorpusCase(
            name = "paid to a person, with a promo tail and a zero transaction cost",
            sender = "MPESA",
            body = "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 " +
                "at 7:50 PM.New M-PESA balance is Ksh1,242.02. Transaction cost, Ksh0.00. " +
                "Amount you can transact within the day is 498,650.00. " +
                "Download My OneApp on https://saf.cx/lPKcC",
            receivedAt = Instant.parse("2026-07-25T16:50:12Z"),
            expected = Expectation.Parsed(
                amount = 1350.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "James Kinyua Mwangi",
                reference = "UGP7B0ITE4",
                timeIso = "2026-07-25T16:50:00Z",
                // `Transaction cost, Ksh0.00` -> no fee row at all (Acceptance 2.3).
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "paid to an all-caps merchant, 'Confirmed.' with a full stop",
            sender = "MPESA",
            body = "UGO7B0DQ91 Confirmed. Ksh3,050.00 paid to FRANK INN KIKUYU. on 24/7/26 " +
                "at 4:10 PM.New M-PESA balance is Ksh2,592.02. Transaction cost, Ksh0.00. " +
                "Amount you can transact within the day is 495,600.00.",
            receivedAt = Instant.parse("2026-07-24T13:10:04Z"),
            expected = Expectation.Parsed(
                amount = 3050.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Frank Inn Kikuyu",
                reference = "UGO7B0DQ91",
                timeIso = "2026-07-24T13:10:00Z",
                // `Transaction cost, Ksh0.00` -> no fee row at all (Acceptance 2.3).
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "send to M-PESA request, ISO timestamp and a trailing reference",
            sender = "MPESA",
            body = "MBNHEUH57FE6NEO9 Completed. Your SEND TO M-PESA request of KES 13,000.00 " +
                "from 135****819 to 254****956 - JACKLINE MUTHEU NGEI at 2026-07-24 07:19:54 PM " +
                "has been processed successfully. Transaction cost KES 66.00 Incl. Tax Amount " +
                "KES 8.25. M-PESA REF: UGO680QNMS. Pata extra cash with a flexible Mobile Loan. " +
                "Dial *522# or use the KCB App to check limit.",
            receivedAt = Instant.parse("2026-07-24T16:20:01Z"),
            expected = Expectation.Parsed(
                amount = 13000.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Jackline Mutheu Ngei",
                reference = "UGO680QNMS",
                timeIso = "2026-07-24T16:19:54Z",
                // The tax is detail on the fee, never a third transaction (FR-019).
                fee = 66.00,
                tax = 8.25,
            ),
        ),
        CorpusCase(
            name = "money received is income, not an expense",
            sender = "MPESA",
            body = "RGP7B0ITE5 Confirmed. You have received Ksh2,500.00 from JOHN DOE " +
                "254712345678 on 25/7/26 at 8:15 PM New M-PESA balance is Ksh3,742.02.",
            receivedAt = Instant.parse("2026-07-25T17:15:30Z"),
            expected = Expectation.Parsed(
                amount = 2500.00,
                direction = MoneyDirection.MoneyIn,
                counterparty = "John Doe",
                reference = "RGP7B0ITE5",
                timeIso = "2026-07-25T17:15:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "sent to a masked phone number, thousands separator and lowercase payee",
            sender = "MPESA",
            body = "RGO7B0DQ92 Confirmed. Ksh12,000.00 sent to mary wanjiru kamau 0722000111 " +
                "on 1/8/26 at 9:05 AM. New M-PESA balance is Ksh1,000.00. " +
                "Transaction cost, Ksh0.00.",
            receivedAt = Instant.parse("2026-08-01T06:05:00Z"),
            expected = Expectation.Parsed(
                amount = 12000.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Mary Wanjiru Kamau",
                reference = "RGO7B0DQ92",
                timeIso = "2026-08-01T06:05:00Z",
                // `Transaction cost, Ksh0.00` -> no fee row at all (Acceptance 2.3).
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "an evening payment still lands on the Nairobi calendar day, not the UTC one",
            sender = "M-PESA",
            body = "SGP7B0ITE9 Confirmed Ksh800.00 paid to NAIVAS SUPERMARKET. on 31/12/26 " +
                "at 11:45 PM.New M-PESA balance is Ksh200.00.",
            receivedAt = Instant.parse("2026-12-31T20:45:00Z"),
            expected = Expectation.Parsed(
                amount = 800.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Naivas Supermarket",
                reference = "SGP7B0ITE9",
                // 23:45 on 31 Dec in Nairobi is 20:45 UTC on the same date — the entry must not
                // slide onto 1 Jan for a user whose device is set to UTC.
                timeIso = "2026-12-31T20:45:00Z",
                fee = null,
                tax = null,
            ),
        ),
    )
}
