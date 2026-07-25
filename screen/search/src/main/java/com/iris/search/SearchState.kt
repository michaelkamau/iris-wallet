package com.iris.search

import com.iris.base.legacy.TransactionHistoryItem
import com.iris.data.model.Category
import com.iris.legacy.datamodel.Account
import kotlinx.collections.immutable.ImmutableList

data class SearchState(
    val searchQuery: String,
    val transactions: ImmutableList<TransactionHistoryItem>,
    val baseCurrency: String,
    val accounts: ImmutableList<Account>,
    val categories: ImmutableList<Category>,
    val shouldShowAccountSpecificColorInTransactions: Boolean
)
