package com.iris.design.l2_components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.R
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.design.l1_buildingBlocks.IrisIcon
import com.iris.design.l1_buildingBlocks.SpacerHor
import com.iris.design.utils.IrisComponentPreview
import com.iris.design.utils.clickableNoIndication
import com.iris.design.utils.rememberInteractionSource

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun Checkbox(
    modifier: Modifier = Modifier,
    checked: Boolean,
    contentDescription: String = "checkbox",
    onCheckedChange: (checked: Boolean) -> Unit
) {
    IrisIcon(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = {
                onCheckedChange(!checked)
            })
            .padding(all = 12.dp),
        icon = if (checked) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked,
        contentDescription = contentDescription,
        tint = if (checked) Color.Unspecified else UI.colors.gray
    )
}

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun CheckboxWithText(
    modifier: Modifier = Modifier,
    checked: Boolean,
    text: String,
    textStyle: TextStyle = UI.typo.b2.style(
        color = UI.colors.pureInverse,
        fontWeight = FontWeight.SemiBold
    ),
    onCheckedChange: (checked: Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .clickableNoIndication(rememberInteractionSource()) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        SpacerHor(width = 4.dp)

        Text(
            modifier = Modifier,
            text = text,
            style = textStyle
        )
    }
}

@Preview
@Composable
private fun PreviewIrisCheckboxWithText() {
    IrisComponentPreview {
        CheckboxWithText(
            text = "Default category",
            checked = false,
        ) {
        }
    }
}
