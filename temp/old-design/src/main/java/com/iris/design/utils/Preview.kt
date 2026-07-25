package com.iris.design.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.iris.base.resource.AndroidResourceProvider
import com.iris.base.legacy.Theme
import com.iris.base.time.impl.DeviceTimeProvider
import com.iris.base.time.impl.StandardTimeConverter
import com.iris.design.IrisContext
import com.iris.design.api.IrisDesign
import com.iris.design.api.IrisUI
import com.iris.design.api.systems.IrisWalletDesign
import com.iris.design.l0_system.UI
import com.iris.ui.time.impl.AndroidDevicePreferences
import com.iris.ui.time.impl.IrisTimeFormatter

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisComponentPreview(
    modifier: Modifier = Modifier,
    design: IrisDesign = defaultDesign(),
    theme: Theme = Theme.LIGHT,
    content: @Composable BoxScope.() -> Unit
) {
    IrisPreview(
        design = design,
        theme = theme
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(UI.colors.pure),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisPreview(
    design: IrisDesign,
    theme: Theme = Theme.LIGHT,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    design.context().switchTheme(theme = theme)
    val timeProvider = DeviceTimeProvider()
    val timeConverter = StandardTimeConverter(timeProvider)
    IrisUI(
        design = design,
        content = content,
        timeConverter = timeConverter,
        timeProvider = timeProvider,
        timeFormatter = IrisTimeFormatter(
            resourceProvider = AndroidResourceProvider(LocalContext.current),
            timeProvider = timeProvider,
            converter = timeConverter,
            devicePreferences = AndroidDevicePreferences(LocalContext.current)
        )
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun defaultDesign(): IrisDesign = object : IrisWalletDesign() {
    override fun context(): IrisContext = object : IrisContext() {
    }
}
