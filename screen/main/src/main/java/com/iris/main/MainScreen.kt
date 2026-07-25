package com.iris.main

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.accounts.AccountsTab
import com.iris.base.model.TransactionType
import com.iris.home.HomeTab
import com.iris.legacy.IrisWalletPreview
import com.iris.legacy.data.model.MainTab
import com.iris.legacy.irisWalletCtx
import com.iris.legacy.utils.onScreenStart
import com.iris.navigation.EditPlannedScreen
import com.iris.navigation.EditTransactionScreen
import com.iris.navigation.MainScreen
import com.iris.navigation.navigation
import com.iris.wallet.domain.deprecated.logic.model.CreateAccountData
import com.iris.wallet.ui.theme.modal.edit.AccountModal
import com.iris.wallet.ui.theme.modal.edit.AccountModalData

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.MainScreen(screen: MainScreen) {
    val viewModel: MainViewModel = viewModel()

    val currency by viewModel.currency.observeAsState("")

    onScreenStart {
        viewModel.start(screen)
    }

    val irisContext = irisWalletCtx()
    UI(
        screen = screen,
        tab = irisContext.mainTab,
        baseCurrency = currency,
        selectTab = viewModel::selectTab,
        onCreateAccount = viewModel::createAccount
    )
}

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    screen: MainScreen,
    tab: MainTab,

    baseCurrency: String,

    selectTab: (MainTab) -> Unit,
    onCreateAccount: (CreateAccountData) -> Unit,
) {
    when (tab) {
        MainTab.HOME -> HomeTab()
        MainTab.ACCOUNTS -> AccountsTab()
    }

    var accountModalData: AccountModalData? by remember { mutableStateOf(null) }

    val nav = navigation()
    BottomBar(
        tab = tab,
        selectTab = selectTab,

        onAddIncome = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.INCOME
                )
            )
        },
        onAddExpense = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.EXPENSE
                )
            )
        },
        onAddTransfer = {
            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = TransactionType.TRANSFER
                )
            )
        },
        onAddPlannedPayment = {
            nav.navigateTo(
                EditPlannedScreen(
                    type = TransactionType.EXPENSE,
                    plannedPaymentRuleId = null
                )
            )
        },

        showAddAccountModal = {
            accountModalData = AccountModalData(
                account = null,
                balance = 0.0,
                baseCurrency = baseCurrency
            )
        }
    )

    AccountModal(
        modal = accountModalData,
        onCreateAccount = onCreateAccount,
        onEditAccount = { _, _ -> },
        dismiss = {
            accountModalData = null
        }
    )
}

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Preview
@Composable
private fun PreviewMainScreen() {
    IrisWalletPreview {
        UI(
            screen = MainScreen,
            tab = MainTab.HOME,
            baseCurrency = "BGN",
            selectTab = {},
            onCreateAccount = { }
        )
    }
}
