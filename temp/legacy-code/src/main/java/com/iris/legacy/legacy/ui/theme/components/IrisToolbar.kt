package com.iris.wallet.ui.theme.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.wallet.ui.theme.gradientCutBackgroundBottom

@Deprecated("Old design system. Use `:iris-design` and Material3")
enum class BackButtonType {
    BACK, CLOSE
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisToolbar(
    onBack: () -> Unit,
    backButtonType: BackButtonType = BackButtonType.BACK,
    paddingTop: Dp = 16.dp,
    paddingBottom: Dp = 16.dp,
    Content: @Composable RowScope.() -> Unit = { }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gradientCutBackgroundBottom(UI.colors.pure, paddingBottom = paddingBottom)
            .padding(top = paddingTop),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(20.dp))

        when (backButtonType) {
            BackButtonType.BACK -> {
                BackButton(
                    modifier = Modifier.testTag("toolbar_back")
                ) {
                    onBack()
                }
            }

            BackButtonType.CLOSE -> {
                CloseButton(
                    modifier = Modifier.testTag("toolbar_close")
                ) {
                    onBack()
                }
            }
        }

        Content()
    }
}
