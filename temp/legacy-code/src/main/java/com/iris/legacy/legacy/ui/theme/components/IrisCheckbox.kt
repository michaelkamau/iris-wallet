package com.iris.wallet.ui.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.IrisWalletComponentPreview
import com.iris.legacy.utils.clickableNoIndication
import com.iris.legacy.utils.rememberInteractionSource
import com.iris.ui.R

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisCheckbox(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (checked: Boolean) -> Unit
) {
    Icon(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = {
                onCheckedChange(!checked)
            })
            .padding(all = 12.dp),

        painter = painterResource(
            id = if (checked) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
        ),
        contentDescription = null,
        tint = if (checked) Color.Unspecified else UI.colors.gray
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun IrisCheckboxWithText(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onCheckedChange: (checked: Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .clickableNoIndication(rememberInteractionSource()) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        IrisCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = text,
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Preview
@Composable
private fun PreviewIrisCheckboxWithText() {
    IrisWalletComponentPreview {
        IrisCheckboxWithText(
            text = "Default category",
            checked = false,
        ) {
        }
    }
}
