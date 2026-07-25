package com.iris.wallet.domain.action.loan

import com.iris.data.db.dao.read.LoanDao
import com.iris.frp.action.FPAction
import com.iris.frp.action.thenMap
import com.iris.frp.then
import com.iris.legacy.datamodel.Loan
import com.iris.legacy.datamodel.temp.toLegacyDomain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class LoansAct @Inject constructor(
    private val loanDao: LoanDao
) : FPAction<Unit, ImmutableList<Loan>>() {
    override suspend fun Unit.compose(): suspend () -> ImmutableList<Loan> = suspend {
        loanDao.findAll()
    } thenMap { it.toLegacyDomain() } then { it.toImmutableList() }
}
