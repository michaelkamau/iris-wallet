package com.iris.domain.usecase.wallet

import arrow.core.Option
import com.iris.data.model.PositiveValue
import com.iris.data.model.primitive.AssetCode
import com.iris.data.repository.TransactionRepository
import com.iris.domain.model.StatSummary
import com.iris.domain.model.TimeRange
import com.iris.domain.usecase.exchange.ExchangeUseCase
import javax.inject.Inject

@Suppress("UnusedPrivateProperty", "UnusedParameter")
class WalletStatsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val exchangeUseCase: ExchangeUseCase,
) {
    /**
     * Calculates the stats for Iris Wallet including excluded accounts.
     * It ignores transfers and focuses only on income and expenses.
     * Stats that can't be exchanged in [outCurrency] are skipped
     * and accumulated as [ExchangedWalletStats.exchangeErrors].
     */
    suspend fun calculate(
        range: TimeRange,
        outCurrency: AssetCode
    ): ExchangedWalletStats {
        // Use the StatSummaryBuilder
        TODO("Not implemented")
    }

    /**
     * Calculates the stats for Iris Wallet including excluded accounts.
     * It ignores transfers and focuses only on income and expenses.
     */
    suspend fun calculate(
        timeRange: TimeRange
    ): WalletStats {
        // Use the StatSummaryBuilder
        TODO("Not implemented")
    }
}

data class WalletStats(
    val income: StatSummary,
    val expense: StatSummary,
)

data class ExchangedWalletStats(
    val income: Option<PositiveValue>,
    val expense: Option<PositiveValue>,
    val exchangeErrors: Set<AssetCode>,
)