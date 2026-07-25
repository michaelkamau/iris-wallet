package com.iris.reports

import com.iris.base.legacy.TransactionHistoryItem
import com.iris.data.model.Category
import com.iris.data.model.Tag
import com.iris.legacy.datamodel.Account
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.*

@Suppress("DataClassDefaultValues")
data class ReportScreenState(
    val baseCurrency: String = "",
    val balance: Double = 0.0,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val upcomingIncome: Double = 0.0,
    val upcomingExpenses: Double = 0.0,
    val overdueIncome: Double = 0.0,
    val overdueExpenses: Double = 0.0,
    val history: ImmutableList<TransactionHistoryItem> = persistentListOf(),
    val upcomingTransactions: ImmutableList<com.iris.base.legacy.Transaction> = persistentListOf(),
    val overdueTransactions: ImmutableList<com.iris.base.legacy.Transaction> = persistentListOf(),
    val categories: ImmutableList<Category> = persistentListOf(),
    val accounts: ImmutableList<Account> = persistentListOf(),
    val upcomingExpanded: Boolean = false,
    val overdueExpanded: Boolean = false,
    val filter: ReportFilter? = null,
    val loading: Boolean = false,
    val accountIdFilters: ImmutableList<UUID> = persistentListOf(),
    val transactions: ImmutableList<com.iris.base.legacy.Transaction> = persistentListOf(),
    val filterOverlayVisible: Boolean = false,
    val showTransfersAsIncExpCheckbox: Boolean = false,
    val treatTransfersAsIncExp: Boolean = false,
    val allTags: ImmutableList<Tag> = persistentListOf(),
    val showAccountColorsInTransactions: Boolean = false
)
