package com.iris.wallet.domain.action.viewmodel.home

import com.iris.frp.action.FPAction
import com.iris.base.legacy.SharedPrefs
import javax.inject.Inject

class ShouldHideBalanceAct @Inject constructor(
    private val sharedPrefs: SharedPrefs
) : FPAction<Unit, Boolean>() {
    override suspend fun Unit.compose(): suspend () -> Boolean = {
        sharedPrefs.getBoolean(
            SharedPrefs.HIDE_CURRENT_BALANCE,
            false
        )
    }
}
