package com.iris.wallet.domain.action.viewmodel.home

import com.iris.frp.action.FPAction
import com.iris.legacy.IrisWalletCtx
import com.iris.legacy.datamodel.Account
import javax.inject.Inject

class UpdateAccCacheAct @Inject constructor(
    private val irisWalletCtx: IrisWalletCtx
) : FPAction<List<Account>, List<Account>>() {
    override suspend fun List<Account>.compose(): suspend () -> List<Account> = suspend {
        val accounts = this

        irisWalletCtx.accountMap.clear()
        irisWalletCtx.accountMap.putAll(accounts.map { it.id to it })

        accounts
    }
}
