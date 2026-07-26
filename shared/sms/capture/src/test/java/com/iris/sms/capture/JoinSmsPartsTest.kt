package com.iris.sms.capture

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant

/**
 * Multipart joining, tested on the JVM because none of it is actually about Android.
 *
 * A long M-PESA confirmation routinely arrives as two parts of one broadcast, and the tail of one
 * on its own looks enough like a message to be parsed as a second, wrong transaction. Getting this
 * wrong is a duplicate in the user's ledger, so it is pinned here rather than left to the device.
 */
class JoinSmsPartsTest {

    @Test
    fun `parts of one message are concatenated in the order they arrived`() {
        // given
        val parts = listOf(
            part(body = "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9."),
            part(body = " on 25/7/26 at 7:50 PM.New M-PESA balance is Ksh1,242.02."),
        )

        // when
        val joined = joinSmsParts(parts)

        // then
        joined.size shouldBe 1
        joined.single().body shouldBe
            "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. " +
            "on 25/7/26 at 7:50 PM.New M-PESA balance is Ksh1,242.02."
    }

    @Test
    fun `nothing is inserted between parts`() {
        // given a message split mid-word, as the transport is free to do
        val parts = listOf(part(body = "Ksh1,3"), part(body = "50.00 paid"))

        // when
        val joined = joinSmsParts(parts)

        // then a separator here would corrupt the amount
        joined.single().body shouldBe "Ksh1,350.00 paid"
    }

    @Test
    fun `parts from different senders stay different messages`() {
        // given
        val parts = listOf(
            part(address = "MPESA", body = "from mpesa"),
            part(address = "KCB", body = "from kcb"),
        )

        // when
        val joined = joinSmsParts(parts)

        // then
        joined.size shouldBe 2
        joined.map { it.sender.value }.toSet() shouldBe setOf("MPESA", "KCB")
    }

    @Test
    fun `the receipt time of the first part wins`() {
        // given
        val parts = listOf(
            part(body = "first", timestampMillis = 1_785_000_000_000L),
            part(body = "second", timestampMillis = 1_785_000_009_000L),
        )

        // when
        val joined = joinSmsParts(parts)

        // then that is when the event happened as far as the user is concerned
        joined.single().receivedAt shouldBe Instant.ofEpochMilli(1_785_000_000_000L)
    }

    @Test
    fun `a part with no usable address is dropped rather than guessed at`() {
        // given
        val parts = listOf(part(address = "   ", body = "who sent this?"))

        // when
        val joined = joinSmsParts(parts)

        // then
        joined.shouldBeEmpty()
    }

    @Test
    fun `a broadcast carrying no parts produces no messages`() {
        // given / when
        val joined = joinSmsParts(emptyList())

        // then
        joined.shouldBeEmpty()
    }

    @Test
    fun `the sender address is normalised the same way it is stored`() {
        // given a lowercase address, which the transport is free to deliver
        val parts = listOf(part(address = "mpesa", body = "hello"))

        // when
        val joined = joinSmsParts(parts)

        // then it must match the enrolled sender id, which is uppercased
        joined.single().sender.value shouldBe "MPESA"
    }

    private fun part(
        address: String = "MPESA",
        body: String,
        timestampMillis: Long = 1_785_000_000_000L,
    ) = SmsPart(address = address, body = body, timestampMillis = timestampMillis)
}
