package com.iris.data.sync

import com.iris.base.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** [TimeProvider] returning a fixed instant, so sync timestamps are deterministic. */
class FixedTimeProvider(private val now: Instant) : TimeProvider {

    override fun getZoneId(): ZoneId = ZoneId.of("UTC")

    override fun utcNow(): Instant = now

    override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now, getZoneId())

    override fun localDateNow(): LocalDate = localNow().toLocalDate()

    override fun localTimeNow(): LocalTime = localNow().toLocalTime()
}
