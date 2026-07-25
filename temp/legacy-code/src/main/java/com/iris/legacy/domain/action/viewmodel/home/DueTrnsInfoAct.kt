package com.iris.wallet.domain.action.viewmodel.home

import com.iris.base.time.TimeProvider
import com.iris.data.model.Transaction
import com.iris.frp.action.FPAction
import com.iris.frp.lambda
import com.iris.frp.then
import com.iris.wallet.domain.action.account.AccountByIdAct
import com.iris.wallet.domain.action.exchange.ExchangeAct
import com.iris.wallet.domain.action.exchange.actInput
import com.iris.wallet.domain.action.transaction.DueTrnsAct
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import com.iris.wallet.domain.pure.exchange.ExchangeTrnArgument
import com.iris.wallet.domain.pure.exchange.exchangeInBaseCurrency
import com.iris.wallet.domain.pure.transaction.expenses
import com.iris.wallet.domain.pure.transaction.incomes
import com.iris.wallet.domain.pure.transaction.sumTrns
import java.time.LocalDate
import javax.inject.Inject

class DueTrnsInfoAct @Inject constructor(
    private val dueTrnsAct: DueTrnsAct,
    private val accountByIdAct: AccountByIdAct,
    private val exchangeAct: ExchangeAct,
    private val timeProvider: TimeProvider
) : FPAction<DueTrnsInfoAct.Input, DueTrnsInfoAct.Output>() {

    override suspend fun Input.compose(): suspend () -> Output =
        suspend {
            range
        } then dueTrnsAct then { trns ->
            val dateNow = timeProvider.localDateNow()
            trns.filter {
                this.dueFilter(it, dateNow)
            }
        } then { dueTrns ->
            // We have due transactions in different currencies
            val exchangeArg = ExchangeTrnArgument(
                baseCurrency = baseCurrency,
                exchange = ::actInput then exchangeAct,
                getAccount = accountByIdAct.lambda()
            )

            Output(
                dueIncomeExpense = IncomeExpensePair(
                    income = sumTrns(
                        incomes(dueTrns),
                        ::exchangeInBaseCurrency,
                        exchangeArg
                    ),
                    expense = sumTrns(
                        expenses(dueTrns),
                        ::exchangeInBaseCurrency,
                        exchangeArg
                    )
                ),
                dueTrns = dueTrns
            )
        }

    data class Input(
        val range: ClosedTimeRange,
        val baseCurrency: String,
        val dueFilter: (Transaction, LocalDate) -> Boolean
    )

    data class Output(
        val dueIncomeExpense: IncomeExpensePair,
        val dueTrns: List<Transaction>
    )
}
