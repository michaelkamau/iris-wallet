package com.iris.wallet.ui.theme.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.IrisWalletComponentPreview
import com.iris.ui.R

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun AddPrimaryAttributeButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))

        IrisIcon(icon = icon)

        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Preview
@Composable
private fun PreviewAddPrimaryAttributeButton() {
    IrisWalletComponentPreview {
        AddPrimaryAttributeButton(
            icon = R.drawable.ic_description,
            text = "Add description"
        ) {
        }
    }
}
