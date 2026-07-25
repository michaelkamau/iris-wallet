package com.iris.wallet.domain.action.transaction

import com.iris.data.model.Transaction
import com.iris.data.repository.TransactionRepository
import com.iris.frp.action.FPAction
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import javax.inject.Inject

class HistoryTrnsAct @Inject constructor(
    private val transactionRepository: TransactionRepository
) : FPAction<ClosedTimeRange, List<Transaction>>() {

    override suspend fun ClosedTimeRange.compose(): suspend () -> List<Transaction> = suspend {
        io {
            transactionRepository.findAllBetween(
                startDate = from,
                endDate = to
            )
        }
    }
}
