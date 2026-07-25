package com.iris.wallet.ui.theme.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.IrisWalletPreview
import com.iris.design.utils.thenIf
import com.iris.ui.R
import com.iris.wallet.ui.theme.Iris
import com.iris.wallet.ui.theme.White
import java.util.UUID

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Suppress("ParameterNaming")
@Composable
fun BoxWithConstraintsScope.ChooseStartDateOfMonthModal(
    id: UUID = UUID.randomUUID(),
    visible: Boolean,
    selectedStartDateOfMonth: Int,

    dismiss: () -> Unit,
    onStartDateOfMonthSelected: (Int) -> Unit,
) {
    IrisModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = { }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(text = stringResource(R.string.choose_start_date_of_month))

        Spacer(Modifier.height(32.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 1,
            toInclusive = 5
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 6,
            toInclusive = 10
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 11,
            toInclusive = 15
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 16,
            toInclusive = 20
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 21,
            toInclusive = 25
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 26,
            toInclusive = 30
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(16.dp))

        NumberRow(
            selectedNumber = selectedStartDateOfMonth,
            fromInclusive = 31,
            toInclusive = 31,
        ) {
            save(
                number = it,
                onStartDateOfMonthSelected = onStartDateOfMonthSelected,
                dismiss = dismiss
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun save(
    number: Int,

    onStartDateOfMonthSelected: (Int) -> Unit,
    dismiss: () -> Unit
) {
    onStartDateOfMonthSelected(number)
    dismiss()
}

@Composable
private fun ColumnScope.NumberRow(
    selectedNumber: Int,
    fromInclusive: Int,
    toInclusive: Int,
    onClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        for (number in fromInclusive..toInclusive) {
            NumberView(
                number = number,
                selected = number == selectedNumber
            ) {
                onClick(it)
            }

            Spacer(Modifier.width(20.dp))
        }

        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun NumberView(
    number: Int,
    selected: Boolean,
    onClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Iris else UI.colors.medium, CircleShape)
            .thenIf(selected) {
                background(Iris, CircleShape)
            }
            .clickable {
                onClick(number)
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = UI.typo.nB2.style(
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) White else UI.colors.pureInverse,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Preview
@Composable
private fun Preview() {
    IrisWalletPreview {
        ChooseStartDateOfMonthModal(
            visible = true,
            selectedStartDateOfMonth = 1,
            dismiss = {}
        ) {
        }
    }
}
