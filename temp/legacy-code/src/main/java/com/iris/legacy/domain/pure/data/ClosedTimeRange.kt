package com.iris.wallet.domain.pure.data

import com.iris.base.time.TimeProvider
import com.iris.legacy.utils.irisMinTime
import java.time.Instant

data class ClosedTimeRange(
    val from: Instant,
    val to: Instant,
) {
    companion object {
        fun allTimeIris(
            timeProvider: TimeProvider,
        ): ClosedTimeRange = ClosedTimeRange(
            from = irisMinTime(),
            to = timeProvider.utcNow(),
        )

        fun to(to: Instant): ClosedTimeRange = ClosedTimeRange(
            from = irisMinTime(),
            to = to
        )
    }
}
