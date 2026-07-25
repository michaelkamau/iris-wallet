package com.iris.wallet.domain.action.account

import com.iris.data.db.dao.read.AccountDao
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.datamodel.Account
import com.iris.legacy.datamodel.temp.toLegacyDomain
import java.util.UUID
import javax.inject.Inject

class AccountByIdAct @Inject constructor(
    private val accountDao: AccountDao
) : FPAction<UUID, Account?>() {
    @Deprecated("Legacy code. Don't use it, please.")
    override suspend fun UUID.compose(): suspend () -> Account? = suspend {
        this // accountId
    } then accountDao::findById then {
        it?.toLegacyDomain()
    }
}
