package com.iris.wallet.ui.edit.core

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iris.data.model.Category
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.ui.R
import com.iris.wallet.ui.theme.Gradient
import com.iris.wallet.ui.theme.components.IrisBorderButton
import com.iris.wallet.ui.theme.components.IrisButton
import com.iris.wallet.ui.theme.components.getCustomIconIdS
import com.iris.wallet.ui.theme.findContrastTextColor
import com.iris.wallet.ui.theme.toComposeColor

@Deprecated("Old design system. Use `:iris-design` and Material3")
@Composable
fun Category(
    category: Category?,
    onChooseCategory: () -> Unit
) {
    if (category != null) {
        CategoryButton(category = category) {
            onChooseCategory()
        }
    } else {
        IrisBorderButton(
            modifier = Modifier.padding(start = 24.dp),
            iconStart = R.drawable.ic_plus,
            iconTint = UI.colors.pureInverse,
            text = stringResource(R.string.add_category)
        ) {
            onChooseCategory()
        }
    }
}

@Composable
private fun CategoryButton(
    category: Category,
    onClick: () -> Unit,
) {
    val contrastColor = findContrastTextColor(category.color.value.toComposeColor())
    IrisButton(
        modifier = Modifier.padding(start = 24.dp),
        text = category.name.value,
        iconStart = getCustomIconIdS(
            iconName = category.icon?.id,
            defaultIcon = R.drawable.ic_custom_category_s
        ),
        backgroundGradient = Gradient.from(category.color.value, category.color.value),
        textStyle = UI.typo.b2.style(
            color = contrastColor,
            fontWeight = FontWeight.Bold
        ),
        iconTint = contrastColor,
        hasGlow = false,
        iconEnd = R.drawable.ic_onboarding_next_arrow,
        wrapContentMode = true,
        onClick = onClick
    )
}
