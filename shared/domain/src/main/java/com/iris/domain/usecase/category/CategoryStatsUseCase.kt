package com.iris.domain.usecase.category

import arrow.core.Option
import com.iris.base.threading.DispatchersProvider
import com.iris.data.model.CategoryId
import com.iris.data.model.PositiveValue
import com.iris.data.model.Transaction
import com.iris.data.model.primitive.AssetCode
import com.iris.domain.model.StatSummary
import com.iris.domain.model.TimeRange
import com.iris.domain.usecase.exchange.ExchangeUseCase
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("UnusedPrivateProperty", "UnusedParameter")
class CategoryStatsUseCase @Inject constructor(
    private val dispatchers: DispatchersProvider,
    private val exchangeUseCase: ExchangeUseCase,
) {
    suspend fun calculate(
        category: CategoryId,
        range: TimeRange,
        outCurrency: AssetCode,
    ): ExchangedCategoryStats {
        TODO("Not implemented")
    }

    suspend fun calculate(
        category: CategoryId,
        range: TimeRange,
    ): CategoryStats {
        TODO("Not implemented")
    }

    suspend fun calculate(
        category: CategoryId,
        transactions: List<Transaction>,
        outCurrency: AssetCode,
    ): ExchangedCategoryStats {
        TODO("Not implemented")
    }

    suspend fun calculate(
        category: CategoryId,
        transactions: List<Transaction>
    ): CategoryStats = withContext(dispatchers.default) {
        TODO("Not implemented")
    }
}

data class CategoryStats(
    val income: StatSummary,
    val expense: StatSummary,
) {
    companion object {
        val Zero = CategoryStats(
            income = StatSummary.Zero,
            expense = StatSummary.Zero,
        )
    }
}

data class ExchangedCategoryStats(
    val income: Option<PositiveValue>,
    val expense: Option<PositiveValue>,
    val exchangeErrors: Set<AssetCode>
)