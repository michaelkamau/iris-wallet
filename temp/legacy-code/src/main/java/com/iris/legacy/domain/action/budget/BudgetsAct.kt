package com.iris.wallet.domain.action.budget

import com.iris.data.db.dao.read.BudgetDao
import com.iris.frp.action.FPAction
import com.iris.frp.action.thenMap
import com.iris.frp.then
import com.iris.legacy.datamodel.Budget
import com.iris.legacy.datamodel.temp.toLegacyDomain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class BudgetsAct @Inject constructor(
    private val budgetDao: BudgetDao
) : FPAction<Unit, ImmutableList<Budget>>() {
    override suspend fun Unit.compose(): suspend () -> ImmutableList<Budget> = suspend {
        budgetDao.findAll()
    } thenMap { it.toLegacyDomain() } then { it.toImmutableList() }
}
