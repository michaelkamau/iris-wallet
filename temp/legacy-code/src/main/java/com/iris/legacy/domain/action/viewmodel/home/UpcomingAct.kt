package com.iris.wallet.domain.action.viewmodel.home

import com.iris.data.model.Transaction
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import com.iris.wallet.domain.pure.transaction.isUpcoming
import javax.inject.Inject

class UpcomingAct @Inject constructor(
    private val dueTrnsInfoAct: DueTrnsInfoAct
) : FPAction<UpcomingAct.Input, UpcomingAct.Output>() {

    override suspend fun Input.compose(): suspend () -> Output = suspend {
        DueTrnsInfoAct.Input(
            range = range,
            baseCurrency = baseCurrency,
            dueFilter = ::isUpcoming
        )
    } then dueTrnsInfoAct then {
        Output(
            upcoming = it.dueIncomeExpense,
            upcomingTrns = it.dueTrns
        )
    }

    data class Input(
        val range: ClosedTimeRange,
        val baseCurrency: String
    )

    data class Output(
        val upcoming: IncomeExpensePair,
        val upcomingTrns: List<Transaction>
    )
}
