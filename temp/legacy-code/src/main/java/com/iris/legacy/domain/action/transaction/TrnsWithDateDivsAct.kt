package com.iris.wallet.domain.action.transaction

import com.iris.base.legacy.Transaction
import com.iris.base.legacy.TransactionHistoryItem
import com.iris.base.time.TimeConverter
import com.iris.data.db.dao.read.AccountDao
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.TagRepository
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.datamodel.temp.toLegacyDomain
import com.iris.legacy.domain.pure.transaction.LegacyTrnDateDividers
import com.iris.legacy.domain.pure.transaction.transactionsWithDateDividers
import com.iris.wallet.domain.action.exchange.ExchangeAct
import com.iris.wallet.domain.action.exchange.actInput
import javax.inject.Inject

class TrnsWithDateDivsAct @Inject constructor(
    private val accountDao: AccountDao,
    private val exchangeAct: ExchangeAct,
    private val tagRepository: TagRepository,
    private val accountRepository: AccountRepository,
) : FPAction<TrnsWithDateDivsAct.Input, List<TransactionHistoryItem>>() {

    override suspend fun Input.compose(): suspend () -> List<TransactionHistoryItem> = suspend {
        transactionsWithDateDividers(
            transactions = transactions,
            baseCurrencyCode = baseCurrency,
            getTags = { tagIds -> tagRepository.findByIds(tagIds) },
            getAccount = accountDao::findById then { it?.toLegacyDomain() },
            accountRepository = accountRepository,
            exchange = ::actInput then exchangeAct
        )
    }

    data class Input(
        val baseCurrency: String,
        val transactions: List<com.iris.data.model.Transaction>
    )
}

@Deprecated("Uses legacy Transaction")
class LegacyTrnsWithDateDivsAct @Inject constructor(
    private val accountDao: AccountDao,
    private val exchangeAct: ExchangeAct,
    private val timeConverter: TimeConverter,
) : FPAction<LegacyTrnsWithDateDivsAct.Input, List<TransactionHistoryItem>>() {

    override suspend fun Input.compose(): suspend () -> List<TransactionHistoryItem> = suspend {
        LegacyTrnDateDividers.transactionsWithDateDividers(
            transactions = transactions,
            baseCurrencyCode = baseCurrency,

            getAccount = accountDao::findById then { it?.toLegacyDomain() },
            exchange = ::actInput then exchangeAct,
            timeConverter = timeConverter,
        )
    }

    data class Input(
        val baseCurrency: String,
        val transactions: List<Transaction>
    )
}