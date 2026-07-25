package com.iris.legacy.data.model

import androidx.compose.runtime.Immutable
import com.iris.base.time.TimeProvider
import com.iris.data.model.IntervalType
import com.iris.legacy.forDisplay
import com.iris.legacy.incrementDate
import java.time.Instant

@Suppress("DataClassFunctions")
@Immutable
data class LastNTimeRange(
    val periodN: Int,
    val periodType: IntervalType,
) {
    fun fromDate(
        timeProvider: TimeProvider
    ): Instant = periodType.incrementDate(
        date = timeProvider.utcNow(),
        intervalN = -periodN.toLong()
    )

    fun forDisplay(): String =
        "$periodN ${periodType.forDisplay(periodN)}"
}
