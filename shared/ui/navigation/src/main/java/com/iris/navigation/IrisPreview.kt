package com.iris.navigation

import androidx.compose.runtime.Composable
import com.iris.design.system.IrisMaterial3Theme

@Composable
fun IrisPreview(
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    NavigationRoot(navigation = Navigation()) {
        IrisMaterial3Theme(dark = dark, isTrueBlack = false, content = content)
    }
}
