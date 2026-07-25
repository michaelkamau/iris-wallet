package com.iris.legacy.data

import androidx.compose.runtime.Immutable
import com.iris.data.model.Category
import com.iris.legacy.datamodel.Account
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AppBaseData(
    val baseCurrency: String,
    val accounts: ImmutableList<Account>,
    val categories: ImmutableList<Category>
)
