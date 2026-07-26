package com.iris.sms.parser.corpus

import com.iris.data.model.sms.MoneyDirection
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * Diamond Trust Bank. The headline case is the transfer from spec.md, verbatim.
 *
 * The `Charges ... KES` tail is a fee of its own (FR-016): the transfer and the charge are two
 * separate amounts out of the same message, and neither is folded into the other.
 */
object DtbCorpus {

    val cases: ImmutableList<CorpusCase> = persistentListOf(
        CorpusCase(
            name = "transfer to a person, masked account and receiving bank after the payee",
            sender = "DTB-KENYA",
            body = "DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna " +
                "5*****5001 DIAMOND TRUST BANK KENYA LTD. Ref. AD3EA389C13A7 on 25 Jul 2026 " +
                "at 17:41 EAT. Charges 59.76 KES",
            receivedAt = Instant.parse("2026-07-25T14:41:20Z"),
            expected = Expectation.Parsed(
                amount = 6000.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Michael Kamau Njuguna",
                reference = "AD3EA389C13A7",
                // 17:41 EAT is 14:41 UTC.
                timeIso = "2026-07-25T14:41:00Z",
                fee = 59.76,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "card purchase, merchant followed by its location",
            sender = "DTB",
            body = "DTB 2500.00 KES has been debited from your account 5*****5001 for a card " +
                "purchase at NAIVAS SUPERMARKET. WESTLANDS NAIROBI Ref. BB1CD234EF567 " +
                "on 25 Jul 2026 at 12:05 EAT.",
            receivedAt = Instant.parse("2026-07-25T09:05:00Z"),
            expected = Expectation.Parsed(
                amount = 2500.00,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Naivas Supermarket",
                reference = "BB1CD234EF567",
                timeIso = "2026-07-25T09:05:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "salary credit is income",
            sender = "DTB-KENYA",
            body = "DTB 45000.00 KES has been credited to your account 5*****5001 from " +
                "ACME LIMITED PAYROLL. Ref. CC9DE876FA543 on 25 Jul 2026 at 09:02 EAT.",
            receivedAt = Instant.parse("2026-07-25T06:02:00Z"),
            expected = Expectation.Parsed(
                amount = 45000.00,
                direction = MoneyDirection.MoneyIn,
                counterparty = "Acme Limited Payroll",
                reference = "CC9DE876FA543",
                timeIso = "2026-07-25T06:02:00Z",
                fee = null,
                tax = null,
            ),
        ),
        CorpusCase(
            name = "the same transfer wording with a promotional tail attached",
            sender = "DTB-KENYA",
            body = "DTB 1200.50 KES has been successfully sent to Grace Atieno Ochieng " +
                "5*****7788 EQUITY BANK KENYA LTD. Ref. DD4FE555AB912 on 2 Aug 2026 " +
                "at 08:30 EAT. Charges 25.00 KES. Download the DTB app on https://dtb.co.ke/app",
            receivedAt = Instant.parse("2026-08-02T05:30:00Z"),
            expected = Expectation.Parsed(
                amount = 1200.50,
                direction = MoneyDirection.MoneyOut,
                counterparty = "Grace Atieno Ochieng",
                reference = "DD4FE555AB912",
                timeIso = "2026-08-02T05:30:00Z",
                // Stripped before the promo tail, so `Charges 25.00 KES` is still reachable.
                fee = 25.00,
                tax = null,
            ),
        ),
    )
}
