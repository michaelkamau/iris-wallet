package com.iris.legacy.ui.component.tags

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iris.data.model.TagId
import com.iris.design.l0_system.Orange3
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.wallet.ui.theme.Gradient
import com.iris.wallet.ui.theme.components.IrisBorderButton
import com.iris.wallet.ui.theme.components.IrisButton
import com.iris.wallet.ui.theme.findContrastTextColor
import kotlinx.collections.immutable.ImmutableList
import com.iris.ui.R

@Composable
fun AddTagButton(
    transactionAssociatedTags: ImmutableList<TagId>,
    onClick: () -> Unit
) {
    if (transactionAssociatedTags.isNotEmpty()) {
        ViewTagsButton(transactionTags = transactionAssociatedTags, onClick = onClick)
    } else {
        AddTagsButton(onClick = onClick)
    }
}

@Composable
private fun ViewTagsButton(
    transactionTags: ImmutableList<TagId>,
    onClick: () -> Unit,
) {
    val contrastColor = findContrastTextColor(Orange3)
    IrisButton(
        modifier = Modifier.padding(start = 24.dp),
        text = if (transactionTags.size <= 1) "${transactionTags.size}\t Tag" else "${transactionTags.size}\t Tags",
        backgroundGradient = Gradient.solid(Orange3),
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

@Composable
private fun AddTagsButton(
    onClick: () -> Unit,
) {
    IrisBorderButton(
        modifier = Modifier.padding(start = 24.dp),
        iconStart = R.drawable.ic_plus,
        iconTint = UI.colors.pureInverse,
        text = stringResource(R.string.add_tags),
        onClick = onClick
    )
}
