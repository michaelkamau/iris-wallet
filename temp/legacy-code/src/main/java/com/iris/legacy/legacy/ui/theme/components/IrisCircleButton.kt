package com.iris.wallet.ui.theme.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.legacy.IrisWalletComponentPreview
import com.iris.legacy.utils.drawColoredShadow
import com.iris.design.utils.thenIf
import com.iris.ui.R
import com.iris.wallet.ui.theme.Gradient
import com.iris.wallet.ui.theme.GradientIris
import com.iris.wallet.ui.theme.GradientRed
import com.iris.wallet.ui.theme.White

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisCircleButton(
    modifier: Modifier = Modifier,
    backgroundPadding: Dp = 0.dp,
    backgroundGradient: Gradient = GradientIris,
    horizontalGradient: Boolean = true,
    @DrawableRes icon: Int,
    tint: Color = White,
    enabled: Boolean = true,
    hasShadow: Boolean = true,
    onClick: () -> Unit
) {
    IrisIcon(
        modifier = modifier
            .thenIf(enabled && hasShadow) {
                drawColoredShadow(
                    color = backgroundGradient.startColor,
                    borderRadius = 0.dp,
                    shadowRadius = 16.dp,
                    offsetX = 0.dp,
                    offsetY = 8.dp
                )
            }
            .clip(UI.shapes.rFull)
            .background(
                brush = if (enabled) {
                    if (horizontalGradient) {
                        backgroundGradient.asHorizontalBrush()
                    } else {
                        backgroundGradient.asVerticalBrush()
                    }
                } else {
                    SolidColor(UI.colors.gray)
                },
                shape = UI.shapes.rFull
            )
            .clickable(onClick = onClick, enabled = enabled)
            .padding(all = backgroundPadding),
        icon = icon,
        tint = tint,
        contentDescription = "circle button"
    )
}

@Preview
@Composable
private fun PreviewIrisCircleButton_Enabled() {
    IrisWalletComponentPreview {
        IrisCircleButton(
            icon = R.drawable.ic_delete,
            backgroundGradient = GradientRed,
            tint = White
        ) {
        }
    }
}

@Preview
@Composable
private fun PreviewIrisCircleButton_Disabled() {
    IrisWalletComponentPreview {
        IrisCircleButton(
            icon = R.drawable.ic_delete,
            backgroundGradient = GradientRed,
            enabled = false,
            tint = White
        ) {
        }
    }
}
