package com.iris.piechart

import androidx.compose.runtime.Immutable
import com.iris.base.legacy.Transaction
import com.iris.base.model.TransactionType
import com.iris.legacy.data.model.TimePeriod
import com.iris.wallet.ui.theme.modal.ChoosePeriodModalData
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

@Immutable
data class PieChartStatisticState(
    val transactionType: TransactionType,
    val period: TimePeriod,
    val baseCurrency: String,
    val totalAmount: Double,
    val categoryAmounts: ImmutableList<CategoryAmount>,
    val selectedCategory: SelectedCategory?,
    val accountIdFilterList: ImmutableList<UUID>,
    val showCloseButtonOnly: Boolean,
    val filterExcluded: Boolean,
    val transactions: ImmutableList<Transaction>,
    val choosePeriodModal: ChoosePeriodModalData?
)
