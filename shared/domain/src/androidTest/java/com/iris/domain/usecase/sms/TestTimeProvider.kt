package com.iris.domain.usecase.sms

import com.iris.base.time.TimeProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A clock the integration tests can assert against.
 *
 * Several of the rows written by [ConfirmCapturedTransactionUseCase] carry an `updatedAt` stamped
 * from the clock. Reading a real one would make those assertions unwritable, so every SMS
 * integration test shares this fixed instant.
 */
internal object TestTimeProvider : TimeProvider {
    val Now: Instant = Instant.parse("2026-07-26T09:00:00Z")

    override fun getZoneId(): ZoneId = ZoneId.of("Africa/Nairobi")
    override fun utcNow(): Instant = Now
    override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(Now, getZoneId())
    override fun localDateNow(): LocalDate = localNow().toLocalDate()
    override fun localTimeNow(): LocalTime = localNow().toLocalTime()
}
