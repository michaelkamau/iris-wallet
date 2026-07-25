package com.iris.legacy.data

import androidx.compose.runtime.Immutable
import com.iris.base.legacy.Transaction
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import kotlinx.collections.immutable.ImmutableList

@Deprecated("Uses legacy Transaction")
@Immutable
data class LegacyDueSection(
    val trns: ImmutableList<Transaction>,
    val expanded: Boolean,
    val stats: IncomeExpensePair
)

@Deprecated("Legacy data model")
@Immutable
data class DueSection(
    val trns: ImmutableList<com.iris.data.model.Transaction>,
    val expanded: Boolean,
    val stats: IncomeExpensePair
)