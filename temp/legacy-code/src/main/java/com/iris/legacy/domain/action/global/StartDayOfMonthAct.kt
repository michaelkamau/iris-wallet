package com.iris.wallet.domain.action.global

import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.IrisWalletCtx
import com.iris.base.legacy.SharedPrefs
import javax.inject.Inject

class StartDayOfMonthAct @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val irisWalletCtx: IrisWalletCtx
) : FPAction<Unit, Int>() {

    override suspend fun Unit.compose(): suspend () -> Int = suspend {
        sharedPrefs.getInt(SharedPrefs.START_DATE_OF_MONTH, 1)
    } then { startDay ->
        irisWalletCtx.setStartDayOfMonth(startDay)
        startDay
    }
}
