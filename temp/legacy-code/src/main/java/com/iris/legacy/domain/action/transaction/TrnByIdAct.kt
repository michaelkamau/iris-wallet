package com.iris.wallet.domain.action.transaction

import com.iris.base.legacy.Transaction
import com.iris.data.model.TransactionId
import com.iris.data.repository.TransactionRepository
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.datamodel.temp.toLegacy
import java.util.UUID
import javax.inject.Inject

class TrnByIdAct @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val mapper: TransactionMapper
) : FPAction<UUID, Transaction?>() {
    override suspend fun UUID.compose(): suspend () -> Transaction? = suspend {
        this // transactionId
    } then {
        transactionRepo.findById(TransactionId(it))
    } then {
        it?.toLegacy(mapper)
    }
}
