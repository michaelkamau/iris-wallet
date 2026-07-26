package com.iris.sms.parser

import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.CounterpartyKey
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.Test

class CounterpartyNormalizerTest {

    private val normalizer = CounterpartyNormalizer()

    @Test
    fun `title cases a name and drops its trailing digits and punctuation`() {
        // given
        val raw = "james Kinyua Mwangi9."

        // when
        val res = normalizer.normalize(raw)

        // then
        res.shouldBeRight() shouldBe NotBlankTrimmedString.unsafe("James Kinyua Mwangi")
    }

    @Test
    fun `title cases a shouty merchant line`() {
        // given
        val raw = "GITHUB, INC. SAN FRANCISCO CA"

        // when
        val res = normalizer.normalize(raw)

        // then
        res.shouldBeRight() shouldBe
            NotBlankTrimmedString.unsafe("Github, Inc. San Francisco Ca")
    }

    @Test
    fun `leaves a short all caps acronym alone`() {
        normalizer.normalize("KCB").shouldBeRight() shouldBe NotBlankTrimmedString.unsafe("KCB")
        normalizer.normalize("DTB").shouldBeRight() shouldBe NotBlankTrimmedString.unsafe("DTB")
    }

    @Test
    fun `drops masked identifier fragments`() {
        // given
        val raw = "Account 5XXXXX5001 Jane Wanjiru"

        // when
        val res = normalizer.normalize(raw)

        // then
        res.shouldBeRight() shouldBe NotBlankTrimmedString.unsafe("Account Jane Wanjiru")
    }

    @Test
    fun `collapses internal whitespace`() {
        // given
        val raw = "  frank   inn  kikuyu "

        // when
        val res = normalizer.normalize(raw)

        // then
        res.shouldBeRight() shouldBe NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")
    }

    @Test
    fun `rejects an input that is all noise`() {
        normalizer.normalize("5XXXXX5001").shouldBeLeft()
        normalizer.normalize("254****956").shouldBeLeft()
        normalizer.normalize("   ").shouldBeLeft()
        normalizer.normalize("12345").shouldBeLeft()
    }

    @Test
    fun `derives one memory key regardless of case and spacing`() {
        // given
        val mixed = normalizer.normalize("frank inn kikuyu").shouldBeRight()
        val shouty = NotBlankTrimmedString.unsafe("FRANK INN KIKUYU")

        // when
        val fromMixed = normalizer.key(mixed).shouldBeRight()
        val fromShouty = normalizer.key(shouty).shouldBeRight()

        // then
        fromMixed shouldBe fromShouty
        fromMixed shouldBe CounterpartyKey.unsafe("FRANKINNKIKUYU")
    }
}
