package com.iris.wallet.domain.action.viewmodel.account

import arrow.core.toOption
import com.iris.frp.action.FPAction
import com.iris.frp.action.thenMap
import com.iris.frp.then
import com.iris.legacy.data.model.AccountData
import com.iris.legacy.datamodel.Account
import com.iris.wallet.domain.action.account.CalcAccBalanceAct
import com.iris.wallet.domain.action.account.CalcAccIncomeExpenseAct
import com.iris.wallet.domain.action.exchange.ExchangeAct
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import com.iris.wallet.domain.pure.exchange.ExchangeData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class AccountDataAct @Inject constructor(
    private val exchangeAct: ExchangeAct,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val calcAccIncomeExpenseAct: CalcAccIncomeExpenseAct
) : FPAction<AccountDataAct.Input, ImmutableList<AccountData>>() {

    override suspend fun Input.compose(): suspend () -> ImmutableList<AccountData> = suspend {
        accounts
    } thenMap { acc ->
        val balance = calcAccBalanceAct(
            CalcAccBalanceAct.Input(
                account = acc
            )
        ).balance

        val balanceBaseCurrency = if (acc.asset.code != baseCurrency) {
            exchangeAct(
                ExchangeAct.Input(
                    data = ExchangeData(
                        baseCurrency = baseCurrency,
                        fromCurrency = acc.asset.code.toOption()
                    ),
                    amount = balance
                )
            ).getOrNull()
        } else {
            null
        }

        val incomeExpensePair = calcAccIncomeExpenseAct(
            CalcAccIncomeExpenseAct.Input(
                account = acc,
                range = range,
                includeTransfersInCalc = includeTransfersInCalc
            )
        ).incomeExpensePair

        com.iris.legacy.data.model.AccountData(
            account = acc,
            balance = balance.toDouble(),
            balanceBaseCurrency = balanceBaseCurrency?.toDouble(),
            monthlyIncome = incomeExpensePair.income.toDouble(),
            monthlyExpenses = incomeExpensePair.expense.toDouble(),
        )
    } then {
        it.toImmutableList()
    }

    data class Input(
        val accounts: ImmutableList<com.iris.data.model.Account>,
        val baseCurrency: String,
        val range: ClosedTimeRange,
        val includeTransfersInCalc: Boolean = false
    )
}
