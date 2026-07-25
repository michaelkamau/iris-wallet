package com.iris.wallet.domain.action.transaction

import com.iris.data.model.Transaction
import com.iris.data.repository.TransactionRepository
import com.iris.frp.action.FPAction
import javax.inject.Inject

class AllTrnsAct @Inject constructor(
    private val transactionRepository: TransactionRepository
) : FPAction<Unit, List<Transaction>>() {
    override suspend fun Unit.compose(): suspend () -> List<Transaction> = suspend {
        transactionRepository.findAll()
    }
}
