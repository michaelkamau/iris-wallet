package com.iris.sms.parser

import com.iris.data.model.primitive.PositiveDouble
import com.iris.sms.parser.primitive.AmountParser
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.Test

class AmountParserTest {

    private val parser = AmountParser()

    @Test
    fun `parses a prefixed amount with thousands separators`() {
        // given
        val raw = "Ksh1,350.00"

        // when
        val res = parser.parse(raw)

        // then
        res.shouldBeRight() shouldBe PositiveDouble.unsafe(1350.00)
    }

    @Test
    fun `parses a spaced KES prefix`() {
        // given
        val raw = "KES 540.25"

        // when
        val res = parser.parse(raw)

        // then
        res.shouldBeRight() shouldBe PositiveDouble.unsafe(540.25)
    }

    @Test
    fun `parses a trailing KES suffix`() {
        // given
        val raw = "6000.00 KES"

        // when
        val res = parser.parse(raw)

        // then
        res.shouldBeRight() shouldBe PositiveDouble.unsafe(6000.00)
    }

    @Test
    fun `parses a prefixed amount with a separator and a KES marker`() {
        // given
        val raw = "KES 13,000.00"

        // when
        val res = parser.parse(raw)

        // then
        res.shouldBeRight() shouldBe PositiveDouble.unsafe(13000.00)
    }

    @Test
    fun `parses a bare number`() {
        parser.parse("59.76").shouldBeRight() shouldBe PositiveDouble.unsafe(59.76)
    }

    @Test
    fun `rejects a zero amount so it can never become a transaction`() {
        parser.parse("Ksh0.00").shouldBeLeft()
        parser.parse("KES 0").shouldBeLeft()
        parser.parse("0.00 KES").shouldBeLeft()
    }

    @Test
    fun `rejects text that is not an amount`() {
        parser.parse("").shouldBeLeft()
        parser.parse("Ksh").shouldBeLeft()
        parser.parse("one thousand").shouldBeLeft()
        parser.parse("12.34.56").shouldBeLeft()
    }
}
