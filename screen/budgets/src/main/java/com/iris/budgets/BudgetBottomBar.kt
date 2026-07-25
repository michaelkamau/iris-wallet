package com.iris.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.iris.legacy.IrisWalletPreview
import com.iris.ui.R
import com.iris.wallet.ui.theme.Blue
import com.iris.wallet.ui.theme.components.BackBottomBar
import com.iris.wallet.ui.theme.components.IrisButton

@Composable
internal fun BoxWithConstraintsScope.BudgetBottomBar(
    onClose: () -> Unit,
    onAdd: () -> Unit
) {
    BackBottomBar(onBack = onClose) {
        IrisButton(
            text = stringResource(R.string.add_budget),
            iconStart = R.drawable.ic_plus
        ) {
            onAdd()
        }
    }
}

@Preview
@Composable
private fun PreviewBottomBar() {
    com.iris.legacy.IrisWalletPreview {
        Column(
            Modifier
                .fillMaxSize()
                .background(Blue)
        ) {
        }

        BudgetBottomBar(
            onAdd = {},
            onClose = {}
        )
    }
}
