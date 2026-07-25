package com.iris.legacy.data.model

import androidx.compose.runtime.Immutable
import com.iris.legacy.datamodel.Account

@Immutable
data class AccountBalance(
    val account: Account,
    val balance: Double
)
