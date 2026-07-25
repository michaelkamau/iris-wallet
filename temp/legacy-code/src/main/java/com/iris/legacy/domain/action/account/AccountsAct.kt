package com.iris.wallet.domain.action.account

import com.iris.data.db.dao.read.AccountDao
import com.iris.frp.action.FPAction
import com.iris.legacy.datamodel.Account
import com.iris.legacy.datamodel.temp.toLegacyDomain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class AccountsAct @Inject constructor(
    private val accountDao: AccountDao
) : FPAction<Unit, ImmutableList<Account>>() {

    override suspend fun Unit.compose(): suspend () -> ImmutableList<Account> = suspend {
        io { accountDao.findAll().map { it.toLegacyDomain() }.toImmutableList() }
    }
}
