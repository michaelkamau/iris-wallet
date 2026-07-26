package com.iris.data.model.sms

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.Test

class SmsPrimitivesTest {

    @Test
    fun `normalises a sender address to trimmed uppercase`() {
        // given
        val rawInput = "  m-pesa "

        // when
        val res = SenderId.from(rawInput)

        // then
        res.shouldBeRight().value shouldBe "M-PESA"
    }

    @Test
    fun `collapses internal whitespace in a sender address`() {
        // given
        val rawInput = "DTB   KENYA"

        // when
        val res = SenderId.from(rawInput)

        // then
        res.shouldBeRight() shouldBe SenderId.unsafe("DTB KENYA")
    }

    @Test
    fun `fails for a blank sender address`() {
        SenderId.from("").shouldBeLeft()
        SenderId.from("   ").shouldBeLeft()
    }

    @Test
    fun `uppercases a provider reference`() {
        // given
        val rawInput = " ugp7b0ite4 "

        // when
        val res = ProviderReference.from(rawInput)

        // then
        res.shouldBeRight() shouldBe ProviderReference.unsafe("UGP7B0ITE4")
    }

    @Test
    fun `rejects a provider reference that is not alphanumeric`() {
        ProviderReference.from("UGP7-B0ITE4").shouldBeLeft()
        ProviderReference.from("UGP7 B0ITE4").shouldBeLeft()
        ProviderReference.from("REF#1234").shouldBeLeft()
    }

    @Test
    fun `rejects a provider reference whose length is out of range`() {
        ProviderReference.from("ABC").shouldBeLeft()
        ProviderReference.from("A".repeat(33)).shouldBeLeft()
        ProviderReference.from("ABCD").shouldBeRight()
        ProviderReference.from("A".repeat(32)).shouldBeRight()
    }

    @Test
    fun `rejects a rule set id that is not a lowercase slug`() {
        RuleSetId.from("m pesa").shouldBeLeft()
        RuleSetId.from("mpesa_v2").shouldBeLeft()
        RuleSetId.from("mpesa!").shouldBeLeft()
    }

    @Test
    fun `lowercases a rule set id`() {
        // given
        val rawInput = " MPESA "

        // when
        val res = RuleSetId.from(rawInput)

        // then
        res.shouldBeRight() shouldBe RuleSetId.unsafe("mpesa")
    }

    @Test
    fun `derives one counterparty key regardless of case and punctuation`() {
        // given
        val mixedCase = "Frank Inn Kikuyu"
        val upperCase = "FRANK INN KIKUYU"

        // when
        val fromMixed = CounterpartyKey.from(mixedCase).shouldBeRight()
        val fromUpper = CounterpartyKey.from(upperCase).shouldBeRight()

        // then
        fromMixed shouldBe fromUpper
        fromMixed shouldBe CounterpartyKey.unsafe("FRANKINNKIKUYU")
    }

    @Test
    fun `fails for a counterparty with no alphanumeric characters`() {
        CounterpartyKey.from("...").shouldBeLeft()
        CounterpartyKey.from("   ").shouldBeLeft()
    }

    @Test
    fun `rejects a message fingerprint longer than the column allows`() {
        MessageFingerprint.from("h:${"a".repeat(70)}").shouldBeLeft()
        MessageFingerprint.from("mpesa:UGP7B0ITE4").shouldBeRight()
    }
}
