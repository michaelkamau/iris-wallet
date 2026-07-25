package com.iris.piechart

import com.iris.data.model.Category
import com.iris.legacy.data.model.TimePeriod
import com.iris.navigation.PieChartStatisticScreen

sealed interface PieChartStatisticEvent {
    data class OnStart(val screen: PieChartStatisticScreen) : PieChartStatisticEvent
    data object OnSelectNextMonth : PieChartStatisticEvent
    data object OnSelectPreviousMonth : PieChartStatisticEvent
    data class OnSetPeriod(val timePeriod: TimePeriod) : PieChartStatisticEvent
    data class OnCategoryClicked(val category: Category?) : PieChartStatisticEvent
    data class OnShowMonthModal(val timePeriod: TimePeriod?) : PieChartStatisticEvent
}
