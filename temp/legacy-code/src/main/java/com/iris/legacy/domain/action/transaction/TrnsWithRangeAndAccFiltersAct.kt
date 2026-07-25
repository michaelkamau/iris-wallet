package com.iris.wallet.domain.action.transaction

import com.iris.base.legacy.Transaction
import com.iris.data.db.dao.read.TransactionDao
import com.iris.frp.action.FPAction
import com.iris.frp.action.thenFilter
import com.iris.legacy.datamodel.temp.toLegacyDomain
import java.util.UUID
import javax.inject.Inject

class TrnsWithRangeAndAccFiltersAct @Inject constructor(
    private val transactionDao: TransactionDao
) : FPAction<TrnsWithRangeAndAccFiltersAct.Input, List<Transaction>>() {

    override suspend fun Input.compose(): suspend () -> List<Transaction> = suspend {
        transactionDao.findAllBetween(range.from(), range.to())
            .map { it.toLegacyDomain() }
    } thenFilter {
        accountIdFilterSet.contains(it.accountId) || accountIdFilterSet.contains(it.toAccountId)
    }

    data class Input(
        val range: com.iris.legacy.data.model.FromToTimeRange,
        val accountIdFilterSet: Set<UUID>
    )
}
