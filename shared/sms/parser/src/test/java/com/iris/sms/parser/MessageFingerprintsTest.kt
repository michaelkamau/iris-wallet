package com.iris.sms.parser

import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.sms.parser.model.RuleId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test
import java.time.Instant

class MessageFingerprintsTest {

    @Test
    fun `prefers the provider reference so the same receipt never lands twice`() {
        // given
        val broadcast = message(body = Body, receivedAt = Instant.parse("2026-07-25T16:50:00Z"))
        val fromInbox = message(body = Body, receivedAt = Instant.parse("2026-07-25T17:04:11Z"))
        val parsed = parsed(reference = ProviderReference.unsafe("UGP7B0ITE4"))

        // when
        val first = MessageFingerprints.of(broadcast, parsed)
        val second = MessageFingerprints.of(fromInbox, parsed)

        // then
        first shouldBe second
        first.value shouldBe "mpesa:UGP7B0ITE4"
    }

    @Test
    fun `falls back to a digest of sender and body when there is no reference`() {
        // given
        val broadcast = message(body = Body, receivedAt = Instant.parse("2026-07-25T16:50:00Z"))
        val fromInbox = message(body = Body, receivedAt = Instant.parse("2026-07-25T17:04:11Z"))

        // when
        val first = MessageFingerprints.of(broadcast, parsed(reference = null))
        val second = MessageFingerprints.of(fromInbox, null)

        // then
        first shouldBe second
        first.value.startsWith("h:") shouldBe true
    }

    @Test
    fun `never stores the message body`() {
        // given
        val message = message(body = Body, receivedAt = Instant.parse("2026-07-25T16:50:00Z"))

        // when
        val fingerprint = MessageFingerprints.of(message, null)

        // then
        fingerprint.value shouldNotContain "540.25"
        fingerprint.value shouldNotContain "JOHN"
    }

    @Test
    fun `separates two different messages from the same sender`() {
        // given
        val one = message(body = Body, receivedAt = Instant.parse("2026-07-25T16:50:00Z"))
        val other = message(body = "$Body extra", receivedAt = Instant.parse("2026-07-25T16:50:00Z"))

        // when
        val first = MessageFingerprints.of(one, null)
        val second = MessageFingerprints.of(other, null)

        // then
        first shouldNotBe second
    }

    @Test
    fun `separates the same body sent by two different senders`() {
        // given
        val mpesa = message(sender = "MPESA", body = Body)
        val bank = message(sender = "DTB", body = Body)

        // when / then
        MessageFingerprints.of(mpesa, null) shouldNotBe MessageFingerprints.of(bank, null)
    }

    private fun message(
        sender: String = "MPESA",
        body: String,
        receivedAt: Instant = Instant.parse("2026-07-25T16:50:00Z"),
    ) = RawSmsMessage(
        sender = SenderId.unsafe(sender),
        body = body,
        receivedAt = receivedAt,
    )

    private fun parsed(reference: ProviderReference?) = ParsedMessage(
        ruleSet = RuleSetId.unsafe("mpesa"),
        rule = RuleId.unsafe("mpesa/received"),
        principal = ParsedAmountLine(
            amount = PositiveDouble.unsafe(540.25),
            asset = AssetCode.unsafe("KES"),
            direction = MoneyDirection.MoneyIn,
        ),
        fee = null,
        time = Instant.parse("2026-07-25T16:50:00Z"),
        counterparty = null,
        reference = reference,
    )

    private companion object {
        const val Body = "UGP7B0ITE4 Confirmed. You have received KES 540.25 from JOHN DOE."
    }
}
