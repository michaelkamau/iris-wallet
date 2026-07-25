package com.iris.wallet.domain.action.viewmodel.transaction

import com.iris.base.legacy.Transaction
import com.iris.data.repository.TransactionRepository
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.datamodel.toEntity
import javax.inject.Inject

class SaveTrnLocallyAct @Inject constructor(
    private val transactionRepo: TransactionRepository,
) : FPAction<Transaction, Unit>() {
    override suspend fun Transaction.compose(): suspend () -> Unit = {
        this.copy(
            isSynced = false
        ).toEntity()
    } then {
        transactionRepo::save then {}
    }
}
