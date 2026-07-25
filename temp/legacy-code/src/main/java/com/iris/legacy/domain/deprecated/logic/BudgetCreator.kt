package com.iris.legacy.domain.deprecated.logic

import com.iris.data.db.dao.read.BudgetDao
import com.iris.data.db.dao.write.WriteBudgetDao
import com.iris.legacy.datamodel.Budget
import com.iris.legacy.utils.ioThread
import com.iris.wallet.domain.deprecated.logic.model.CreateBudgetData
import com.iris.wallet.domain.pure.util.nextOrderNum
import javax.inject.Inject

class BudgetCreator @Inject constructor(
    private val budgetDao: BudgetDao,
    private val budgetWriter: WriteBudgetDao,
) {
    suspend fun createBudget(
        data: CreateBudgetData,
        onRefreshUI: suspend (Budget) -> Unit
    ) {
        val name = data.name
        if (name.isBlank()) return
        if (data.amount <= 0) return

        try {
            val newBudget = ioThread {
                val budget = Budget(
                    name = name.trim(),
                    amount = data.amount,
                    categoryIdsSerialized = data.categoryIdsSerialized,
                    accountIdsSerialized = data.accountIdsSerialized,
                    orderId = budgetDao.findMaxOrderNum().nextOrderNum(),
                    isSynced = false
                )

                budgetWriter.save(budget.toEntity())
                budget
            }

            onRefreshUI(newBudget)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun editBudget(
        updatedBudget: Budget,
        onRefreshUI: suspend (Budget) -> Unit
    ) {
        if (updatedBudget.name.isBlank()) return
        if (updatedBudget.amount <= 0.0) return

        try {
            ioThread {
                budgetWriter.save(
                    updatedBudget.toEntity().copy(
                        isSynced = false
                    )
                )
            }

            onRefreshUI(updatedBudget)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteBudget(
        budget: Budget,
        onRefreshUI: suspend () -> Unit
    ) {
        try {
            ioThread {
                budgetWriter.deleteById(budget.id)
            }

            onRefreshUI()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
