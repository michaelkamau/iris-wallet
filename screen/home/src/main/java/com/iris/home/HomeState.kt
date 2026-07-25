package com.iris.home

import androidx.compose.runtime.Immutable
import com.iris.base.legacy.Theme
import com.iris.base.legacy.TransactionHistoryItem
import com.iris.home.customerjourney.CustomerJourneyCardModel
import com.iris.legacy.data.AppBaseData
import com.iris.legacy.data.BufferInfo
import com.iris.legacy.data.LegacyDueSection
import com.iris.legacy.data.model.TimePeriod
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import kotlinx.collections.immutable.ImmutableList
import java.math.BigDecimal

@Immutable
data class HomeState(
    val theme: Theme,
    val name: String,

    val period: TimePeriod,
    val baseData: AppBaseData,

    val history: ImmutableList<TransactionHistoryItem>,
    val stats: IncomeExpensePair,

    val balance: BigDecimal,

    val buffer: BufferInfo,

    val upcoming: LegacyDueSection,
    val overdue: LegacyDueSection,

    val customerJourneyCards: ImmutableList<CustomerJourneyCardModel>,
    val hideBalance: Boolean,
    val hideIncome: Boolean,
    val expanded: Boolean,
    val shouldShowAccountSpecificColorInTransactions: Boolean
)
