package com.iris.design.l2_components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.R
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.White
import com.iris.design.l1_buildingBlocks.IrisIcon
import com.iris.design.l1_buildingBlocks.data.Background
import com.iris.design.l1_buildingBlocks.data.background
import com.iris.design.l1_buildingBlocks.data.clipBackground
import com.iris.design.utils.IrisComponentPreview
import com.iris.design.utils.padding

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IconButton(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    iconTint: Color = White,
    background: Background = Background.Solid(
        color = UI.colors.primary,
        shape = CircleShape,
        padding = padding(all = 8.dp)
    ),
    onClick: () -> Unit
) {
    IrisIcon(
        modifier = modifier
            .clipBackground(background)
            .clickable {
                onClick()
            }
            .background(background),
        icon = icon,
        tint = iconTint
    )
}

@Preview
@Composable
private fun Preview() {
    IrisComponentPreview {
        IconButton(
            icon = R.drawable.ic_baseline_add_24
        ) {
        }
    }
}
