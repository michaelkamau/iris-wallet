package com.iris.piechart

import androidx.compose.runtime.Immutable
import com.iris.data.model.Category

@Immutable
data class SelectedCategory(
    val category: Category // null - Unspecified
)
