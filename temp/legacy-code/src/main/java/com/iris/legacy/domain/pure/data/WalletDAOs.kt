package com.iris.wallet.domain.pure.data

import com.iris.data.db.dao.read.AccountDao
import com.iris.data.db.dao.read.ExchangeRatesDao
import com.iris.data.db.dao.read.TransactionDao
import javax.inject.Inject

data class WalletDAOs @Inject constructor(
    val accountDao: AccountDao,
    val transactionDao: TransactionDao,
    val exchangeRatesDao: ExchangeRatesDao
)
