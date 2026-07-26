package com.iris.sms.parser

import com.iris.sms.parser.primitive.SmsDateTimeFormat
import com.iris.sms.parser.primitive.SmsDateTimeParser
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class SmsDateTimeParserTest {

    private val parser = SmsDateTimeParser()

    @Test
    fun `resolves a two digit year and a meridiem clock in Nairobi time`() {
        // given
        val formats = persistentListOf(
            SmsDateTimeFormat.SlashDayMonthShortYear,
            SmsDateTimeFormat.ClockMeridiem,
        )

        // when
        val res = parser.parse("25/7/26", "7:50 PM", formats, ReceivedAt)

        // then
        res.shouldBeRight() shouldBe Instant.parse("2026-07-25T16:50:00Z")
    }

    @Test
    fun `strips an EAT suffix from a 24 hour clock`() {
        // given
        val formats = persistentListOf(
            SmsDateTimeFormat.SpacedDayMonShortName,
            SmsDateTimeFormat.Clock24,
        )

        // when
        val res = parser.parse("25 Jul 2026", "17:41 EAT", formats, ReceivedAt)

        // then
        res.shouldBeRight() shouldBe Instant.parse("2026-07-25T14:41:00Z")
    }

    @Test
    fun `falls back to the receipt time of day when the message carries no time`() {
        // given
        val formats = persistentListOf(SmsDateTimeFormat.SlashDayMonthFullYear)
        val receivedSameDay = Instant.parse("2026-07-25T05:30:00Z") // 08:30 in Nairobi

        // when
        val res = parser.parse("25/07/2026", null, formats, receivedSameDay)

        // then
        res.shouldBeRight() shouldBe Instant.parse("2026-07-25T05:30:00Z")
    }

    @Test
    fun `falls back to local noon when the receipt lands on another Nairobi day`() {
        // given
        val formats = persistentListOf(SmsDateTimeFormat.SlashDayMonthFullYear)
        val receivedNextDay = Instant.parse("2026-07-27T05:30:00Z")

        // when
        val res = parser.parse("25/07/2026", null, formats, receivedNextDay)

        // then
        res.shouldBeRight() shouldBe Instant.parse("2026-07-25T09:00:00Z")
    }

    @Test
    fun `parses a combined iso date and meridiem time`() {
        // given
        val formats = persistentListOf(SmsDateTimeFormat.IsoDateTimeMeridiem)

        // when
        val res = parser.parse("2026-07-24 07:19:54 PM", null, formats, ReceivedAt)

        // then
        res.shouldBeRight() shouldBe Instant.parse("2026-07-24T16:19:54Z")
    }

    @Test
    fun `is independent of the JVM default time zone`() {
        // given
        val formats = persistentListOf(
            SmsDateTimeFormat.SlashDayMonthShortYear,
            SmsDateTimeFormat.ClockMeridiem,
        )
        val original = TimeZone.getDefault()

        // when
        val results = listOf(ZoneOffset.UTC, ZoneId.of("America/Los_Angeles"), ZoneId.of("Asia/Tokyo"))
            .map { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                parser.parse("25/7/26", "7:50 PM", formats, ReceivedAt).shouldBeRight()
            }
        TimeZone.setDefault(original)

        // then
        results.distinct() shouldBe listOf(Instant.parse("2026-07-25T16:50:00Z"))
    }

    @Test
    fun `rejects a date that matches none of the declared formats`() {
        // given
        val formats = persistentListOf(SmsDateTimeFormat.SlashDayMonthFullYear)

        // when
        val res = parser.parse("July the 25th", null, formats, ReceivedAt)

        // then
        res.shouldBeLeft()
    }

    private companion object {
        val ReceivedAt: Instant = Instant.parse("2026-07-25T17:00:00Z")
    }
}
