package com.iris.sms.parser.corpus

import com.iris.sms.parser.ExclusionReason
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * The messages the app must decline. This is SC-004 ("zero false positives") as an executable
 * gate, and it is the half of the corpus that stops a rule loosened to fix one wording from
 * quietly inventing transactions out of adverts and OTPs.
 *
 * Two failure modes are deliberately kept apart (contracts/sms-parser-contract.md §6):
 *
 * - [Expectation.Rejected] — a named exclusion fired, so we *understood* the message and chose
 *   not to record it;
 * - [Expectation.NotMatched] — no rule claimed the sender, or no wording matched. The result is
 *   nothing at all, never a half-filled parse (FR-013).
 */
object NegativeCorpus {

    /** One fixed literal for every negative: none of them depends on the receipt time. */
    private val Received = Instant.parse("2026-07-25T16:50:00Z")

    val cases: ImmutableList<CorpusCase> = persistentListOf(
        CorpusCase(
            name = "M-PESA zero-value principal",
            sender = "MPESA",
            body = "UGP7B0ITE7 Confirmed Ksh0.00 paid to james Kinyua Mwangi9. on 25/7/26 " +
                "at 7:50 PM.New M-PESA balance is Ksh1,242.02.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.ZeroValue),
        ),
        CorpusCase(
            name = "M-PESA failed transaction",
            sender = "MPESA",
            body = "UGP7B0ITE8 Confirmed. Your transaction of Ksh1,350.00 to james Kinyua " +
                "Mwangi has failed. Your M-PESA account has not been debited.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Failed),
        ),
        CorpusCase(
            name = "M-PESA cancelled transaction",
            sender = "MPESA",
            body = "UGP7B0ITE1 Confirmed. Your request of Ksh1,350.00 to FRANK INN KIKUYU " +
                "has been cancelled.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Cancelled),
        ),
        CorpusCase(
            name = "M-PESA reversal notice",
            sender = "MPESA",
            body = "UGP7B0ITE2 Confirmed. Transaction UGP7B0ITE4 of Ksh1,350.00 paid to " +
                "james Kinyua Mwangi9. on 25/7/26 at 7:50 PM has been reversed. " +
                "New M-PESA balance is Ksh2,592.02.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Reversed),
        ),
        CorpusCase(
            name = "M-PESA balance enquiry",
            sender = "MPESA",
            body = "UGP7B0ITE3 Confirmed. Your M-PESA balance was Ksh1,242.02 on 25/7/26 " +
                "at 8:01 PM. Transaction cost, Ksh0.00.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.BalanceEnquiry),
        ),
        CorpusCase(
            name = "M-PESA loan-limit advert whose promotional tail is stripped first",
            sender = "MPESA",
            body = "Dear customer, your Fuliza limit is now Ksh5,000. Dial *234# to opt in.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Promotional),
        ),
        CorpusCase(
            name = "M-PESA statement notice",
            sender = "MPESA",
            body = "Your M-PESA statement for July 2026 is ready. It has been sent to the " +
                "email address registered on your line.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Statement),
        ),
        CorpusCase(
            name = "M-PESA one-time password",
            sender = "MPESA",
            body = "Your M-PESA verification code is 458210. Do not share it with anyone, " +
                "including Safaricom staff.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.OneTimePassword),
        ),
        CorpusCase(
            name = "KCB one-time password",
            sender = "KCB",
            body = "Your OTP for the KCB Mobile Banking login is 771205. Valid for 5 minutes.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.OneTimePassword),
        ),
        CorpusCase(
            name = "KCB balance enquiry",
            sender = "KCB",
            body = "ALERT: Your account no. 5XXXXX5001 available balance is KES 12,004.10 " +
                "as at 25/07/2026.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.BalanceEnquiry),
        ),
        CorpusCase(
            name = "DTB declined card purchase",
            sender = "DTB-KENYA",
            body = "DTB 2500.00 KES card purchase at NAIVAS SUPERMARKET was declined due to " +
                "insufficient funds. Ref. BB1CD234EF567 on 25 Jul 2026 at 12:05 EAT.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.Failed),
        ),
        CorpusCase(
            name = "DTB zero-value transfer",
            sender = "DTB-KENYA",
            body = "DTB 0.00 KES has been successfully sent to Michael Kamau Njuguna " +
                "5*****5001 DIAMOND TRUST BANK KENYA LTD. Ref. AD3EA389C13A7 on 25 Jul 2026 " +
                "at 17:41 EAT.",
            receivedAt = Received,
            expected = Expectation.Rejected(ExclusionReason.ZeroValue),
        ),
        CorpusCase(
            name = "a sender nobody claims is never inspected",
            sender = "SUPERMARKET-OFFERS",
            body = "Ksh1,350.00 off your next shop! Show this message at the till.",
            receivedAt = Received,
            expected = Expectation.NotMatched(ruleSet = null),
        ),
        CorpusCase(
            name = "a financial sender in a wording we have never seen produces nothing at all",
            sender = "MPESA",
            body = "UGP7B0ITE6 Confirmed. Ksh1,350.00 has been moved to your Lock Savings " +
                "target on 25/7/26 at 7:50 PM.",
            receivedAt = Received,
            expected = Expectation.NotMatched(ruleSet = "mpesa"),
        ),
        CorpusCase(
            name = "a KCB wording we have never seen produces nothing at all",
            sender = "KCB",
            body = "ALERT: A standing order of KES 4,000.00 on account no. 5XXXXX5001 is due " +
                "for renewal on 30/07/2026.",
            receivedAt = Received,
            expected = Expectation.NotMatched(ruleSet = "kcb"),
        ),
    )
}
