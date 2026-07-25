package com.iris.wallet.ui.edit.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.api.LocalTimeFormatter
import com.iris.design.api.LocalTimeProvider
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.IrisWalletComponentPreview
import com.iris.ui.R
import com.iris.ui.time.TimeFormatter
import com.iris.wallet.ui.theme.components.IrisIcon
import java.time.Instant
import java.util.concurrent.TimeUnit

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun DueDate(
    dueDate: Instant,
    onPickDueDate: () -> Unit,
) {
    DueDateCard(
        dueDate = dueDate,
        onClick = {
            onPickDueDate()
        }
    )
}

@Composable
private fun DueDateCard(
    dueDate: Instant,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))

        IrisIcon(icon = R.drawable.ic_planned_payments)

        Spacer(Modifier.width(8.dp))

        Text(
            text = stringResource(R.string.planned_for),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.ExtraBold,
                color = UI.colors.pureInverse
            )
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = with(LocalTimeFormatter.current) {
                dueDate.formatLocal(TimeFormatter.Style.DateOnly(includeWeekDay = false))
            },
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Suppress("MagicNumber")
@Preview
@Composable
private fun Preview_OneTime() {
    IrisWalletComponentPreview {
        DueDate(
            dueDate = LocalTimeProvider.current.utcNow()
                .plusSeconds(TimeUnit.DAYS.toSeconds(6)),
        ) {
        }
    }
}
