package com.iris.legacy

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.iris.base.legacy.Theme
import com.iris.base.legacy.appContext
import com.iris.design.IrisContext
import com.iris.design.api.IrisDesign
import com.iris.design.api.irisContext
import com.iris.design.api.systems.IrisWalletDesign
import com.iris.design.l0_system.UI
import com.iris.design.utils.IrisPreview
import com.iris.domain.RootScreen
import com.iris.navigation.Navigation
import com.iris.navigation.NavigationRoot

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun irisWalletCtx(): IrisWalletCtx = irisContext() as IrisWalletCtx

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun rootView(): View = LocalView.current

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun rootActivity(): AppCompatActivity = LocalContext.current as AppCompatActivity

@Composable
fun rootScreen(): RootScreen = LocalContext.current as RootScreen

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisWalletComponentPreview(
    theme: Theme = Theme.LIGHT,
    Content: @Composable BoxScope.() -> Unit
) {
    IrisWalletPreview(
        theme = theme
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.pure),
            contentAlignment = Alignment.Center
        ) {
            Content()
        }
    }
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisWalletPreview(
    theme: Theme = Theme.LIGHT,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    appContext = rootView().context
    IrisPreview(
        theme = theme,
        design = appDesign(IrisWalletCtx()),
    ) {
        NavigationRoot(navigation = Navigation()) {
            content()
        }
    }
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
fun appDesign(context: IrisWalletCtx): IrisDesign = object : IrisWalletDesign() {
    override fun context(): IrisContext = context
}
