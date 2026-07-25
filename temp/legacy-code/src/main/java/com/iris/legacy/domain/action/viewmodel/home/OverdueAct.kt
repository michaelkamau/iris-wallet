package com.iris.wallet.domain.action.viewmodel.home

import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.utils.irisMinTime
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import com.iris.wallet.domain.pure.transaction.isOverdue
import java.time.Instant
import javax.inject.Inject

class OverdueAct @Inject constructor(
    private val dueTrnsInfoAct: DueTrnsInfoAct
) : FPAction<OverdueAct.Input, OverdueAct.Output>() {

    override suspend fun Input.compose(): suspend () -> Output = suspend {
        DueTrnsInfoAct.Input(
            range = ClosedTimeRange(
                from = irisMinTime(),
                to = toRange
            ),
            baseCurrency = baseCurrency,
            dueFilter = ::isOverdue
        )
    } then dueTrnsInfoAct then {
        Output(
            overdue = it.dueIncomeExpense,
            overdueTrns = it.dueTrns
        )
    }

    data class Input(
        val toRange: Instant,
        val baseCurrency: String
    )

    data class Output(
        val overdue: IncomeExpensePair,
        val overdueTrns: List<com.iris.data.model.Transaction>
    )
}
