package com.iris.piechart

import androidx.compose.runtime.Immutable
import com.iris.base.legacy.Transaction
import com.iris.data.model.Category

@Immutable
data class CategoryAmount(
    val category: Category?,
    val amount: Double,
    val associatedTransactions: List<Transaction> = emptyList(),
    val isCategoryUnspecified: Boolean = false
)
