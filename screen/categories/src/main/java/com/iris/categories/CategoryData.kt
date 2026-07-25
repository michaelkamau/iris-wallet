package com.iris.categories

import com.iris.data.model.Category
import com.iris.wallet.domain.data.Reorderable

data class CategoryData(
    val category: Category,
    val monthlyBalance: Double,
    val monthlyExpenses: Double,
    val monthlyIncome: Double
) : Reorderable {
    override fun getItemOrderNum() = category.orderNum

    override fun withNewOrderNum(newOrderNum: Double) = this.copy(
        category = category.copy(
            orderNum = newOrderNum
        )
    )
}
