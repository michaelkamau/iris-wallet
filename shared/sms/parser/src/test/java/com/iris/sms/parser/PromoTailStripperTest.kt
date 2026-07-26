package com.iris.sms.parser

import com.iris.sms.parser.primitive.PromoTailStripper
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class PromoTailStripperTest {

    private val stripper = PromoTailStripper()

    private val markers = persistentListOf(
        Regex("(?i)download\\s+my\\s+oneapp"),
        Regex("(?i)dial\\s+\\*\\d+#"),
    )

    @Test
    fun `cuts the message at a promotional tail`() {
        // given
        val body = "You have received KES 540.25 from JOHN DOE. Download My OneApp on https://x.co/a"

        // when
        val stripped = stripper.strip(body, markers)

        // then
        stripped shouldBe "You have received KES 540.25 from JOHN DOE."
    }

    @Test
    fun `cuts at the earliest marker when several match`() {
        // given
        val body = "Paid KES 20. Dial *522# now. Download My OneApp on https://x.co/a"

        // when
        val stripped = stripper.strip(body, markers)

        // then
        stripped shouldBe "Paid KES 20."
    }

    @Test
    fun `leaves a message without a tail untouched apart from trimming`() {
        // given
        val body = "  Paid KES 20 to FRANK INN KIKUYU.  "

        // when
        val stripped = stripper.strip(body, markers)

        // then
        stripped shouldBe "Paid KES 20 to FRANK INN KIKUYU."
    }

    @Test
    fun `leaves the message untouched when the rule set declares no markers`() {
        // given
        val body = "Paid KES 20. Download My OneApp on https://x.co/a"

        // when
        val stripped = stripper.strip(body, persistentListOf())

        // then
        stripped shouldBe body
    }
}
