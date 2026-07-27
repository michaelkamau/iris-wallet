package com.iris.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.iris.design.system.colors.IrisColors

/**
 * The Material 3 counterpart of Iris's established bottom back affordance.
 *
 * Detail screens keep navigation reachable with the same thumb-friendly placement as Categories,
 * Budgets, and Exchange Rates without depending on the retiring legacy design system.
 */
@Composable
fun IrisBackBottomBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Spacer(modifier = Modifier.width(20.dp))
        Icon(
            modifier = Modifier
                .size(BACK_BUTTON_SIZE)
                .clip(CircleShape)
                .background(IrisColors.Black, CircleShape)
                .border(width = 2.dp, color = IrisColors.DarkGray, shape = CircleShape)
                .clickable(onClick = onBack)
                .padding(BACK_ICON_PADDING),
            imageVector = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = IrisColors.White,
        )
    }
}

private val BACK_BUTTON_SIZE = 56.dp
private val BACK_ICON_PADDING = 12.dp
