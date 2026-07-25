package com.iris.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iris.data.model.Category
import com.iris.legacy.data.model.AccountBalance
import com.iris.legacy.utils.onScreenStart
import com.iris.navigation.OnboardingScreen
import com.iris.onboarding.steps.OnboardingAccounts
import com.iris.onboarding.steps.OnboardingCategories
import com.iris.onboarding.steps.OnboardingSetCurrency
import com.iris.onboarding.steps.OnboardingSplashLogin
import com.iris.onboarding.steps.OnboardingType
import com.iris.onboarding.viewmodel.OnboardingViewModel
import com.iris.wallet.domain.data.IrisCurrency
import com.iris.wallet.domain.deprecated.logic.model.CreateAccountData
import com.iris.wallet.domain.deprecated.logic.model.CreateCategoryData
import kotlinx.collections.immutable.ImmutableList

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.OnboardingScreen(screen: OnboardingScreen) {
    val viewModel: OnboardingViewModel = viewModel()

    val state by viewModel.state
    val uiState = viewModel.uiState()

    val isSystemDarkTheme = isSystemInDarkTheme()
    onScreenStart {
        viewModel.start(
            screen = screen,
            isSystemDarkMode = isSystemDarkTheme
        )
    }

    UI(
        onboardingState = state,
        currency = uiState.currency,

        accountSuggestions = uiState.accountSuggestions,
        accounts = uiState.accounts,

        categorySuggestions = uiState.categorySuggestions,
        categories = uiState.categories,

        onEvent = viewModel::onEvent

    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    onboardingState: OnboardingState,
    currency: IrisCurrency,

    accountSuggestions: ImmutableList<CreateAccountData>,
    accounts: ImmutableList<AccountBalance>,

    categorySuggestions: ImmutableList<CreateCategoryData>,
    categories: ImmutableList<Category>,

    onEvent: (OnboardingEvent) -> Unit = {}
) {
    when (onboardingState) {
        OnboardingState.SPLASH, OnboardingState.LOGIN -> {
            OnboardingSplashLogin(
                onboardingState = onboardingState,
                onSkip = { onEvent(OnboardingEvent.LoginOfflineAccount) }
            )
        }

        OnboardingState.CHOOSE_PATH -> {
            OnboardingType(
                onStartImport = { onEvent(OnboardingEvent.StartImport) },
                onStartFresh = { onEvent(OnboardingEvent.StartFresh) }
            )
        }

        OnboardingState.CURRENCY -> {
            OnboardingSetCurrency(
                preselectedCurrency = currency,
                onSetCurrency = { onEvent(OnboardingEvent.SetBaseCurrency(it)) }
            )
        }

        OnboardingState.ACCOUNTS -> {
            OnboardingAccounts(
                baseCurrency = currency.code,
                suggestions = accountSuggestions,
                accounts = accounts,

                onCreateAccount = { onEvent(OnboardingEvent.CreateAccount(it)) },
                onEditAccount = { account, newBalance ->
                    onEvent(
                        OnboardingEvent.EditAccount(
                            account,
                            newBalance
                        )
                    )
                },

                onDoneClick = { onEvent(OnboardingEvent.OnAddAccountsDone) },
                onSkip = { onEvent(OnboardingEvent.OnAddAccountsSkip) }
            )
        }

        OnboardingState.CATEGORIES -> {
            OnboardingCategories(
                suggestions = categorySuggestions,
                categories = categories,

                onCreateCategory = { onEvent(OnboardingEvent.CreateCategory(it)) },
                onEditCategory = { onEvent(OnboardingEvent.EditCategory(it)) },

                onDoneClick = { onEvent(OnboardingEvent.OnAddCategoriesDone) },
                onSkip = { onEvent(OnboardingEvent.OnAddCategoriesSkip) }
            )
        }
    }
}
