package com.iris

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import com.iris.attributions.AttributionsScreenImpl
import com.iris.balance.BalanceScreen
import com.iris.budgets.BudgetScreen
import com.iris.categories.CategoriesScreen
import com.iris.contributors.ContributorsScreenImpl
import com.iris.disclaimer.DisclaimerScreenImpl
import com.iris.exchangerates.ExchangeRatesScreen
import com.iris.features.FeaturesScreenImpl
import com.iris.importdata.csv.CSVScreen
import com.iris.importdata.csvimport.ImportCSVScreen
import com.iris.loans.loan.LoansScreen
import com.iris.loans.loandetails.LoanDetailsScreen
import com.iris.main.MainScreen
import com.iris.navigation.AttributionsScreen
import com.iris.navigation.BalanceScreen
import com.iris.navigation.BudgetScreen
import com.iris.navigation.CSVScreen
import com.iris.navigation.CategoriesScreen
import com.iris.navigation.ContributorsScreen
import com.iris.navigation.DisclaimerScreen
import com.iris.navigation.EditPlannedScreen
import com.iris.navigation.EditTransactionScreen
import com.iris.navigation.ExchangeRatesScreen
import com.iris.navigation.FeaturesScreen
import com.iris.navigation.ImportScreen
import com.iris.navigation.LoanDetailsScreen
import com.iris.navigation.LoansScreen
import com.iris.navigation.MainScreen
import com.iris.navigation.OnboardingScreen
import com.iris.navigation.PieChartStatisticScreen
import com.iris.navigation.PlannedPaymentsScreen
import com.iris.navigation.ReleasesScreen
import com.iris.navigation.ReportScreen
import com.iris.navigation.Screen
import com.iris.navigation.SearchScreen
import com.iris.navigation.SettingsScreen
import com.iris.navigation.SmsReviewScreen
import com.iris.navigation.TransactionsScreen
import com.iris.onboarding.OnboardingScreen
import com.iris.piechart.PieChartStatisticScreen
import com.iris.planned.edit.EditPlannedScreen
import com.iris.planned.list.PlannedPaymentsScreen
import com.iris.releases.ReleasesScreenImpl
import com.iris.reports.ReportScreen
import com.iris.search.SearchScreen
import com.iris.settings.SettingsScreen
import com.iris.sms.review.SmsReviewScreenImpl
import com.iris.transaction.EditTransactionScreen
import com.iris.transactions.TransactionsScreen

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
@Suppress("CyclomaticComplexMethod", "FunctionNaming")
fun BoxWithConstraintsScope.IrisNavGraph(screen: Screen?) {
    when (screen) {
        null -> {
            // show nothing
        }

        is MainScreen -> MainScreen(screen = screen)
        is OnboardingScreen -> OnboardingScreen(screen = screen)
        is ExchangeRatesScreen -> ExchangeRatesScreen()
        is EditTransactionScreen -> EditTransactionScreen(screen = screen)
        is TransactionsScreen -> TransactionsScreen(screen = screen)
        is PieChartStatisticScreen -> PieChartStatisticScreen(screen = screen)
        is CategoriesScreen -> CategoriesScreen(screen = screen)
        is SettingsScreen -> SettingsScreen()
        is PlannedPaymentsScreen -> PlannedPaymentsScreen(screen = screen)
        is EditPlannedScreen -> EditPlannedScreen(screen = screen)
        is BalanceScreen -> BalanceScreen(screen = screen)
        is ImportScreen -> ImportCSVScreen(screen = screen)
        is ReportScreen -> ReportScreen(screen = screen)
        is BudgetScreen -> BudgetScreen(screen = screen)
        is LoansScreen -> LoansScreen(screen = screen)
        is LoanDetailsScreen -> LoanDetailsScreen(screen = screen)
        is SearchScreen -> SearchScreen(screen = screen)
        is CSVScreen -> CSVScreen(screen = screen)
        FeaturesScreen -> FeaturesScreenImpl()
        AttributionsScreen -> AttributionsScreenImpl()
        ContributorsScreen -> ContributorsScreenImpl()
        ReleasesScreen -> ReleasesScreenImpl()
        DisclaimerScreen -> DisclaimerScreenImpl()
        SmsReviewScreen -> SmsReviewScreenImpl()
    }
}
