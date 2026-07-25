package com.iris.piechart

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.base.model.TransactionType
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.utils.navigationBarInset
import com.iris.legacy.utils.toDensityDp
import com.iris.ui.R
import com.iris.wallet.ui.theme.Gradient
import com.iris.wallet.ui.theme.GradientGreen
import com.iris.wallet.ui.theme.White
import com.iris.wallet.ui.theme.components.ActionsRow
import com.iris.wallet.ui.theme.components.CloseButton
import com.iris.wallet.ui.theme.components.IrisButton
import com.iris.wallet.ui.theme.gradientCutBackgroundTop

@Composable
fun BoxWithConstraintsScope.PieChartStatisticBottomBar(
    type: TransactionType,
    onClose: () -> Unit,
    onAdd: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = navigationBarInset().toDensityDp()
) {
    ActionsRow(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .gradientCutBackgroundTop(UI.colors.pure, LocalDensity.current)
            .padding(bottom = bottomInset)
            .padding(bottom = 16.dp)
    ) {
        Spacer(Modifier.width(20.dp))

        CloseButton {
            onClose()
        }

        Spacer(Modifier.weight(1f))

        val isIncome = type == TransactionType.INCOME
        IrisButton(
            iconStart = R.drawable.ic_plus,
            text = if (isIncome) {
                stringResource(
                    id = R.string.add_income
                )
            } else {
                stringResource(id = R.string.add_expense)
            },
            backgroundGradient = if (isIncome) GradientGreen else Gradient.solid(UI.colors.pureInverse),
            textStyle = UI.typo.b2.style(
                color = if (isIncome) White else UI.colors.pure,
                fontWeight = FontWeight.ExtraBold
            ),
            iconTint = if (isIncome) White else UI.colors.pure
        ) {
            onAdd(type)
        }

        Spacer(Modifier.width(20.dp))
    }
}

@Preview
@Composable
private fun PreviewBottomBar() {
    com.iris.legacy.IrisWalletPreview {
        PieChartStatisticBottomBar(
            type = TransactionType.INCOME,
            bottomInset = 16.dp,
            onAdd = {},
            onClose = {}
        )
    }
}
