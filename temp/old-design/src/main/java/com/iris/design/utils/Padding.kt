package com.iris.design.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.design.l1_buildingBlocks.data.IrisPadding

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun Modifier.irisPadding(irisPadding: IrisPadding): Modifier {
    return this.padding(
        top = irisPadding.top ?: 0.dp,
        bottom = irisPadding.bottom ?: 0.dp,
        start = irisPadding.start ?: 0.dp,
        end = irisPadding.end ?: 0.dp
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun padding(
    top: Dp? = null,
    start: Dp? = null,
    end: Dp? = null,
    bottom: Dp? = null
): IrisPadding {
    return IrisPadding(
        top = top,
        bottom = bottom,
        start = start,
        end = end
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun padding(
    horizontal: Dp? = null,
    vertical: Dp? = null
): IrisPadding {
    return IrisPadding(
        top = vertical,
        bottom = vertical,
        start = horizontal,
        end = horizontal
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun padding(
    all: Dp? = null
): IrisPadding {
    return IrisPadding(
        top = all,
        bottom = all,
        start = all,
        end = all
    )
}
