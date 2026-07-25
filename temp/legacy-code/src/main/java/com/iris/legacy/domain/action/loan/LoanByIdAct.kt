package com.iris.wallet.domain.action.loan

import com.iris.data.db.dao.read.LoanDao
import com.iris.frp.action.FPAction
import com.iris.legacy.datamodel.Loan
import com.iris.legacy.datamodel.temp.toLegacyDomain
import java.util.UUID
import javax.inject.Inject

class LoanByIdAct @Inject constructor(
    private val loanDao: LoanDao
) : FPAction<UUID, Loan?>() {
    override suspend fun UUID.compose(): suspend () -> Loan? = suspend {
        loanDao.findById(this)?.toLegacyDomain()
    }
}
