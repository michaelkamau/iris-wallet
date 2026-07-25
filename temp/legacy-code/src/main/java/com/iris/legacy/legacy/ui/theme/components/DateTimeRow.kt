package com.iris.legacy.legacy.ui.theme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iris.design.api.LocalTimeFormatter
import com.iris.legacy.utils.convertLocalToUTC
import com.iris.legacy.utils.convertUTCToLocal
import com.iris.legacy.utils.formatNicely
import com.iris.legacy.utils.timeNowUTC
import com.iris.ui.R
import com.iris.wallet.ui.theme.components.IrisOutlinedButton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun DateTimeRow(
    dateTime: LocalDateTime,
    onEditDate: () -> Unit,
    onEditTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = LocalTimeFormatter.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        IrisOutlinedButton(
            text = dateTime.formatNicely(),
            iconStart = R.drawable.ic_date,
            onClick = onEditDate
        )

        Spacer(Modifier.weight(1f))

        IrisOutlinedButton(
            text = with(timeFormatter) {
                dateTime.toLocalTime().format()
            },
            iconStart = R.drawable.ic_date,
            onClick = onEditTime
        )

        Spacer(Modifier.width(24.dp))
    }
}

// The timepicker returns time in UTC, but the date picker returns date in LocalTimeZone
// hence use this method to get both date & time in UTC
@Deprecated("Rework this to use the TimeConverter API")
fun getTrueDate(
    date: LocalDate,
    time: LocalTime,
    convert: Boolean = true
): LocalDateTime {
    val timeLocal = if (convert) time.convertUTCToLocal() else time

    return timeNowUTC()
        .withYear(date.year)
        .withMonth(date.monthValue)
        .withDayOfMonth(date.dayOfMonth)
        .withHour(timeLocal.hour)
        .withMinute(timeLocal.minute)
        .withSecond(0)
        .withNano(0)
        .convertLocalToUTC()
}