package com.iris.wallet.domain.action.global

import com.iris.frp.action.FPAction
import com.iris.frp.monad.Res
import com.iris.frp.monad.thenIfSuccess
import com.iris.legacy.IrisWalletCtx
import com.iris.base.legacy.SharedPrefs
import javax.inject.Inject

class UpdateStartDayOfMonthAct @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val irisWalletCtx: IrisWalletCtx
) : FPAction<Int, Res<String, Int>>() {

    override suspend fun Int.compose(): suspend () -> Res<String, Int> = suspend {
        val startDay = this

        if (startDay in 1..31) {
            Res.Ok(startDay)
        } else {
            Res.Err("Invalid start day $startDay. Start date must be between 1 and 31.")
        }
    } thenIfSuccess { startDay ->
        sharedPrefs.putInt(SharedPrefs.START_DATE_OF_MONTH, startDay)
        irisWalletCtx.setStartDayOfMonth(startDay)
        Res.Ok(startDay)
    } thenIfSuccess { startDay ->
        irisWalletCtx.initSelectedPeriodInMemory(
            startDayOfMonth = startDay,
            forceReinitialize = true
        )
        Res.Ok(startDay)
    }
}
