package com.iris.design.api

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.iris.base.time.TimeConverter
import com.iris.base.time.TimeProvider
import com.iris.design.IrisContext
import com.iris.design.l0_system.IrisTheme
import com.iris.ui.time.TimeFormatter

val LocalIrisContext = compositionLocalOf<IrisContext> { error("No LocalIrisContext") }

@Suppress("CompositionLocalAllowlist")
@Deprecated("Used only for time migration to Instant. Never use it in new code!")
val LocalTimeConverter = compositionLocalOf<TimeConverter> { error("No LocalTimeConverter") }

@Suppress("CompositionLocalAllowlist")
@Deprecated("Used only for time migration to Instant. Never use it in new code!")
val LocalTimeProvider = compositionLocalOf<TimeProvider> { error("No LocalTimeProvider") }

@Suppress("CompositionLocalAllowlist")
@Deprecated("Used only for time migration to Instant. Never use it in new code!")
val LocalTimeFormatter = compositionLocalOf<TimeFormatter> { error("No LocalTimeFormatter") }

@SuppressLint("ComposeModifierMissing")
@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisUI(
    timeConverter: TimeConverter,
    timeProvider: TimeProvider,
    timeFormatter: TimeFormatter,
    design: IrisDesign,
    includeSurface: Boolean = true,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val irisContext = design.context()

    CompositionLocalProvider(
        LocalIrisContext provides irisContext,
        LocalTimeConverter provides timeConverter,
        LocalTimeProvider provides timeProvider,
        LocalTimeFormatter provides timeFormatter,
    ) {
        IrisTheme(
            theme = irisContext.theme,
            design = design
        ) {
            WrapWithSurface(includeSurface = includeSurface) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    irisContext.screenWidth = with(LocalDensity.current) {
                        maxWidth.roundToPx()
                    }
                    irisContext.screenHeight = with(LocalDensity.current) {
                        maxHeight.roundToPx()
                    }

                    content()
                }
            }
        }
    }
}

@Composable
private fun WrapWithSurface(
    includeSurface: Boolean,
    content: @Composable () -> Unit,
) {
    if (includeSurface) {
        Surface {
            content()
        }
    } else {
        content()
    }
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun irisContext(): IrisContext {
    return LocalIrisContext.current
}
