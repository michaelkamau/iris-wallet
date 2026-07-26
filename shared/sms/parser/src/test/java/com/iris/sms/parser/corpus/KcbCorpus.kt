package com.iris.sms.parser.corpus

import com.iris.data.model.sms.MoneyDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * KCB. The headline case is the POS alert from spec.md, verbatim — including `540.25for`, which
 * is how the message really arrives.
 *
 * The alert carries no time of day, so the instant comes from the date-only fallback: the
 * receipt's Nairobi time-of-day when the receipt lands on the same Nairobi day, and local noon
 * otherwise. Both branches are pinned here with fixed [CorpusCase.receivedAt] literals.
 */
object KcbCorpus {

    val cases: ImmutableList<CorpusCase> = persistentListOf(
        CorpusCase(
            name = "POS purchase abroad, no reference, no time of day, amount glued to 'for'",
            sender = "KCB",
            body = "ALERT: Your account no. 5XXXXX5001 has been debited with KES 540.25for a " +
                "POS PURCHASE at GITHUB, INC. SAN FRANCISCO CA on 25/07/2026.",
            // 20:30 in Nairobi on 25 Jul 2026 — the same Nairobi day as the stated date, so the
            // receipt's time-of-day is kept.
            receivedAt = Instant.parse("2026-07-25T17:30:00Z"),
            expected = Expectation.Parsed(
                amount = 540.25,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Github, Inc",
                reference = null,
                timeIso = "2026-07-25T17:30:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "the same alert read a day late falls back to local noon, not to the read time",
            sender = "KCB",
            body = "ALERT: Your account no. 5XXXXX5001 has been debited with KES 1,200.00for a " +
                "POS PURCHASE at NAIVAS SUPERMARKET WESTLANDS on 25/07/2026.",
            // Read on 27 Jul, e.g. by the historical import: noon Nairobi is 09:00 UTC.
            receivedAt = Instant.parse("2026-07-27T06:00:00Z"),
            expected = Expectation.Parsed(
                amount = 1200.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Naivas Supermarket Westlands",
                reference = null,
                timeIso = "2026-07-25T09:00:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "an account credit is income",
            sender = "KCB",
            body = "ALERT: Your account no. 5XXXXX5001 has been credited with KES 25,000.00 " +
                "from ACME LIMITED PAYROLL on 25/07/2026.",
            receivedAt = Instant.parse("2026-07-25T05:15:00Z"),
            expected = Expectation.Parsed(
                amount = 25000.00,
                direction = MoneyDirection.MoneyIn,
                counterparty = "Acme Limited Payroll",
                reference = null,
                timeIso = "2026-07-25T05:15:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "the KCB app's send-to-M-PESA confirmation, same body as M-PESA's own",
            sender = "KCB",
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
                // Identical body, identical fee: the shared pattern is exercised under both
                // senders so a change to it cannot pass under one and fail under the other.
                fee = 66.00,
                tax = 8.25,
            ),
        ),
        CorpusCase(
            name = "a charge advice with no transfer in it is still a transaction cost",
            sender = "KCB",
            body = "ALERT: A charge of KES 33.00 has been applied to your account no. " +
                "5XXXXX5001 for a MOBILE MONEY TRANSFER on 25/07/2026.",
            // 14:20 in Nairobi on the stated day, so the receipt's time-of-day is kept.
            receivedAt = Instant.parse("2026-07-25T11:20:00Z"),
            expected = Expectation.Parsed(
                // No principal anywhere in the message: the charge is the whole of it (FR-020),
                // and inventing an amount here is precisely what FR-013 forbids.
                amount = null,
                direction = null,
                counterparty = null,
                reference = null,
                timeIso = "2026-07-25T11:20:00Z",
                fee = 33.00,
                tax = null,
            ),
        ),
    )
}
