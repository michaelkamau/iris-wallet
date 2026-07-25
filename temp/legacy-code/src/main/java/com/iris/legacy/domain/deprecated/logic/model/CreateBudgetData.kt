package com.iris.wallet.domain.deprecated.logic.model

data class CreateBudgetData(
    val name: String,
    val amount: Double,
    val categoryIdsSerialized: String,
    val accountIdsSerialized: String
)
