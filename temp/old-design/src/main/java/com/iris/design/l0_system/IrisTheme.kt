package com.iris.design.l0_system

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.iris.base.legacy.Theme
import com.iris.design.api.IrisDesign
import com.iris.design.system.IrisMaterial3Theme

@Deprecated("Old design system. Use `:iris-design` and Material3")
val LocalIrisColors = compositionLocalOf<IrisColors> { error("No IrisColors") }

@Deprecated("Old design system. Use `:iris-design` and Material3")
val LocalIrisTypography = compositionLocalOf<IrisTypography> { error("No IrisTypography") }

@Deprecated("Old design system. Use `:iris-design` and Material3")
val LocalIrisShapes = compositionLocalOf<IrisShapes> { error("No IrisShapes") }

@Deprecated("Old design system. Use `:iris-design` and Material3")
object UI {
    val colors: IrisColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIrisColors.current

    val typo: IrisTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalIrisTypography.current

    val shapes: IrisShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalIrisShapes.current
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisTheme(
    theme: Theme,
    design: IrisDesign,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = design.colors(theme, isDarkTheme)
    val typography = design.typography()
    val shapes = design.shapes()

    CompositionLocalProvider(
        LocalIrisColors provides colors,
        LocalIrisTypography provides typography,
        LocalIrisShapes provides shapes
    ) {
        val view = LocalView.current
        if (!view.isInEditMode && view.context is Activity) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    colors.isLight
            }
        }

        IrisMaterial3Theme(
            dark = !colors.isLight,
            isTrueBlack = theme == Theme.AMOLED_DARK,
            content = content,
        )
    }
}
