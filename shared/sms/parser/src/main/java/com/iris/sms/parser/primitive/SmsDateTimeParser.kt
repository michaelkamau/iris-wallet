package com.iris.sms.parser.primitive

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.util.Locale
import javax.inject.Inject

/**
 * The date/time wordings seen in the Kenyan providers in scope.
 *
 * The pattern is kept as a plain string so the whole rule model stays serialisable-friendly
 * (research.md D2).
 */
enum class SmsDateTimeFormat(internal val pattern: String) {
    /** `25/7/26` */
    SlashDayMonthShortYear("d/M/yy"),

    /** `25/07/2026` */
    SlashDayMonthFullYear("dd/MM/yyyy"),

    /** `25 Jul 2026` */
    SpacedDayMonShortName("d MMM yyyy"),

    /** `2026-07-24 07:19:54 PM` */
    IsoDateTimeMeridiem("yyyy-MM-dd hh:mm:ss a"),

    /** `7:50 PM` */
    ClockMeridiem("h:mm a"),

    /** `17:41` — a trailing `EAT` is stripped before parsing. */
    Clock24("HH:mm"),
    ;

    internal val hasDate: Boolean = pattern.any { it == 'd' || it == 'M' || it == 'y' }
    internal val hasTime: Boolean = pattern.any { it == 'h' || it == 'H' || it == 'm' }
    internal val formatter: DateTimeFormatter = buildFormatter(pattern)
}

/** `25/7/26` is 2026, not 1926 (research.md D8). */
private const val TwoDigitYearBase = 2000

/**
 * A two-digit year needs `appendValueReduced` rather than a plain `yy` pattern, which is why the
 * formatters are built rather than taken straight from `DateTimeFormatter.ofPattern`.
 *
 * Top level rather than in a companion object because an enum's companion is initialised *after*
 * its constants, so a constant initialiser cannot call into it.
 */
private fun buildFormatter(pattern: String): DateTimeFormatter {
    val builder = DateTimeFormatterBuilder().parseCaseInsensitive()
    val shortYear = pattern.endsWith("yy") && !pattern.endsWith("yyyy")
    if (shortYear) {
        builder.appendPattern(pattern.removeSuffix("yy"))
            .appendValueReduced(ChronoField.YEAR, 2, 2, TwoDigitYearBase)
    } else {
        builder.appendPattern(pattern)
    }
    return builder.toFormatter(Locale.US)
}

/**
 * Resolves every SMS timestamp in the fixed zone `Africa/Nairobi`, whatever the device is set to.
 *
 * All senders in scope are Kenyan, so a fixed zone is both correct and simpler than a per-rule-set
 * one — and it is what makes a 7:50 PM Nairobi payment land on the right local day for a
 * traveller (spec Edge Case "Time zones").
 */
class SmsDateTimeParser @Inject constructor() {

    /**
     * @param rawTime the separate time-of-day capture, when the rule has one. When it is absent
     *   the date falls back to [receivedAt]'s Nairobi time-of-day if the receipt lands on the same
     *   Nairobi calendar day, and to local noon otherwise (research.md D8).
     */
    fun parse(
        rawDate: String,
        rawTime: String?,
        formats: ImmutableList<SmsDateTimeFormat>,
        receivedAt: Instant,
    ): Either<String, Instant> = either {
        val dateText = clean(rawDate)
        val timeText = rawTime?.let(::clean)?.takeIf(String::isNotBlank)

        val local = parseCombined(dateText, formats)
            ?: parseSeparately(dateText, timeText, formats, receivedAt)
        ensureNotNull(local) { "'$rawDate'/'$rawTime' matches none of $formats" }

        local.atZone(ZONE).toInstant()
    }

    private fun parseCombined(
        dateText: String,
        formats: ImmutableList<SmsDateTimeFormat>,
    ): LocalDateTime? = formats
        .filter { it.hasDate && it.hasTime }
        .firstNotNullOfOrNull { format ->
            format.formatter.parseOrNull(dateText)?.let { parsed ->
                val date = parsed.toLocalDateOrNull()
                val time = parsed.toLocalTimeOrNull()
                if (date != null && time != null) LocalDateTime.of(date, time) else null
            }
        }

    private fun parseSeparately(
        dateText: String,
        timeText: String?,
        formats: ImmutableList<SmsDateTimeFormat>,
        receivedAt: Instant,
    ): LocalDateTime? {
        val date = formats
            .filter { it.hasDate && !it.hasTime }
            .firstNotNullOfOrNull { it.formatter.parseOrNull(dateText)?.toLocalDateOrNull() }
            ?: return null

        val time = when (timeText) {
            null -> fallbackTime(date, receivedAt)
            else -> timeOfDay(timeText, formats)
        }
        return time?.let { LocalDateTime.of(date, it) }
    }

    private fun timeOfDay(
        timeText: String,
        formats: ImmutableList<SmsDateTimeFormat>,
    ): LocalTime? = formats
        .filter { it.hasTime && !it.hasDate }
        .firstNotNullOfOrNull { it.formatter.parseOrNull(timeText)?.toLocalTimeOrNull() }

    /**
     * A date with no time-of-day keeps the receipt's time-of-day when the receipt happened on the
     * same Nairobi day, so an ordering by time still reads naturally; otherwise local noon, which
     * cannot drift onto a neighbouring day in any nearby zone.
     */
    private fun fallbackTime(date: LocalDate, receivedAt: Instant): LocalTime {
        val received = receivedAt.atZone(ZONE)
        return if (received.toLocalDate() == date) received.toLocalTime() else LocalTime.NOON
    }

    private fun clean(raw: String): String = raw
        .replace(ZONE_SUFFIX, "")
        .replace(WHITESPACE, " ")
        .trim()
        .trim('.', ',')

    /**
     * `DateTimeFormatter.parse` throws on a mismatch — and a mismatch is the *normal* case here,
     * since every candidate format is tried in turn. [Either.catch] turns that into the absence
     * this module can compose with, so no exception ever escapes.
     */
    private fun DateTimeFormatter.parseOrNull(text: String): TemporalAccessor? =
        Either.catch { parse(text) }.getOrNull()

    private fun TemporalAccessor.toLocalDateOrNull(): LocalDate? =
        if (isSupported(ChronoField.EPOCH_DAY)) LocalDate.from(this) else null

    private fun TemporalAccessor.toLocalTimeOrNull(): LocalTime? =
        if (isSupported(ChronoField.NANO_OF_DAY)) LocalTime.from(this) else null

    companion object {
        val ZONE: ZoneId = ZoneId.of("Africa/Nairobi")

        /** `17:41 EAT`, `17:41 E.A.T.`, `17:41 (EAT)` — the zone is fixed, so the label is noise. */
        private val ZONE_SUFFIX = Regex("(?i)[\\s(]*\\bE\\.?A\\.?T\\.?\\)?\\s*$")
        private val WHITESPACE = Regex("\\s+")
    }
}
