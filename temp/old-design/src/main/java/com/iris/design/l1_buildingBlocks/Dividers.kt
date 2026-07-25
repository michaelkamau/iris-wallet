package com.iris.design.l1_buildingBlocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.design.utils.IrisComponentPreview
import com.iris.design.utils.thenWhen

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun DividerH(
    size: DividerSize = DividerSize.FillMax(
        padding = 16.dp
    ),
    width: Dp = 1.dp,
    color: Color = UI.colors.gray,
    shape: Shape = UI.shapes.rFull
) {
    Spacer(
        modifier = Modifier
            .thenWhen {
                when (size) {
                    is DividerSize.FillMax -> {
                        fillMaxWidth()
                            .padding(horizontal = size.padding)
                    }
                    is DividerSize.Fixed -> {
                        this.width(size.size)
                    }
                }
            }
            .height(width)
            .background(color, shape)
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun DividerV(
    size: DividerSize = DividerSize.FillMax(
        padding = 16.dp
    ),
    width: Dp = 1.dp,
    color: Color = UI.colors.gray,
    shape: Shape = UI.shapes.rFull
) {
    Spacer(
        modifier = Modifier
            .thenWhen {
                when (size) {
                    is DividerSize.FillMax -> {
                        fillMaxHeight()
                            .padding(vertical = size.padding)
                    }
                    is DividerSize.Fixed -> {
                        this.height(size.size)
                    }
                }
            }
            .width(width)
            .background(color, shape)
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun RowScope.DividerW(
    weight: Float = 1f,
    height: Dp = 1.dp,
    color: Color = UI.colors.gray,
    shape: Shape = UI.shapes.rFull
) {
    Divider(
        modifier = Modifier
            .weight(weight)
            .height(1.dp),
        color = color,
        shape = shape
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = UI.colors.gray,
    shape: Shape = UI.shapes.rFull
) {
    Spacer(
        modifier = modifier
            .background(color, shape)
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
sealed class DividerSize {
    @Deprecated("Old design system. Use `:iris-design` and Material3")
    data class Fixed(val size: Dp) : DividerSize()

    @Deprecated("Old design system. Use `:iris-design` and Material3")
    data class FillMax(val padding: Dp) : DividerSize()
}

@Preview
@Composable
private fun PreviewHorizontalDivider_fillMax() {
    IrisComponentPreview {
        DividerH(
            size = DividerSize.FillMax(
                padding = 16.dp
            )
        )
    }
}

@Preview
@Composable
private fun PreviewHorizontalDivider_fixed() {
    IrisComponentPreview {
        DividerH(
            size = DividerSize.Fixed(
                size = 32.dp
            )
        )
    }
}

@Preview
@Composable
private fun PreviewVerticalDivider_fillMax() {
    IrisComponentPreview {
        DividerV(
            size = DividerSize.FillMax(
                padding = 16.dp
            )
        )
    }
}

@Preview
@Composable
private fun PreviewVerticalDivider_fixed() {
    IrisComponentPreview {
        DividerV(
            size = DividerSize.Fixed(
                size = 16.dp
            )
        )
    }
}

@Preview
@Composable
private fun PreviewDivider() {
    IrisComponentPreview {
        Row {
            SpacerHor(16.dp)

            Divider(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
            )

            SpacerHor(16.dp)

            Divider(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
            )

            SpacerHor(16.dp)

            Divider(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
            )

            SpacerHor(16.dp)
        }
    }
}
