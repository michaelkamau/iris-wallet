package com.iris.onboarding.steps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iris.design.l0_system.UI
import com.iris.design.l0_system.style
import com.iris.legacy.IrisWalletPreview
import com.iris.legacy.data.model.AccountBalance
import com.iris.legacy.datamodel.Account
import com.iris.legacy.utils.toLowerCaseLocal
import com.iris.navigation.navigation
import com.iris.onboarding.components.OnboardingProgressSlider
import com.iris.onboarding.components.OnboardingToolbar
import com.iris.onboarding.components.Suggestions
import com.iris.ui.R
import com.iris.wallet.domain.deprecated.logic.model.CreateAccountData
import com.iris.wallet.ui.theme.GradientIris
import com.iris.wallet.ui.theme.Green
import com.iris.wallet.ui.theme.Iris
import com.iris.wallet.ui.theme.IrisDark
import com.iris.wallet.ui.theme.Orange
import com.iris.wallet.ui.theme.White
import com.iris.wallet.ui.theme.components.GradientCutBottom
import com.iris.wallet.ui.theme.components.ItemIconMDefaultIcon
import com.iris.wallet.ui.theme.components.OnboardingButton
import com.iris.wallet.ui.theme.dynamicContrast
import com.iris.wallet.ui.theme.findContrastTextColor
import com.iris.wallet.ui.theme.modal.edit.AccountModal
import com.iris.wallet.ui.theme.modal.edit.AccountModalData
import com.iris.wallet.ui.theme.toComposeColor
import com.iris.wallet.ui.theme.wallet.AmountCurrencyB1Row

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.OnboardingAccounts(
    baseCurrency: String,

    suggestions: List<CreateAccountData>,
    accounts: List<AccountBalance>,

    onCreateAccount: (CreateAccountData) -> Unit = { },
    onEditAccount: (Account, Double) -> Unit = { _, _ -> },

    onSkip: () -> Unit = {},
    onDoneClick: () -> Unit = {}
) {
    var accountModalData: AccountModalData? by remember { mutableStateOf(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        stickyHeader {
            val nav = navigation()
            OnboardingToolbar(
                hasSkip = accounts.isEmpty(),
                onBack = { nav.onBackPressed() },
                onSkip = onSkip
            )
        }

        item {
            Column {
                Spacer(Modifier.height(8.dp))

                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(R.string.add_accounts),
                    style = UI.typo.h2.style(
                        fontWeight = FontWeight.Black
                    )
                )

//                PremiumInfo(
//                    itemLabelPlural = "accounts",
//                    itemsCount = accounts.size,
//                    freeItemsCount = Constants.FREE_ACCOUNTS
//                )

                if (accounts.isEmpty()) {
                    Spacer(Modifier.height(16.dp))

                    Image(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.onboarding_illustration_accounts),
                        contentDescription = "account illustration"
                    )

                    OnboardingProgressSlider(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        selectedStep = 2,
                        stepsCount = 4,
                        selectedColor = Orange
                    )

                    Spacer(Modifier.height(48.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }

                Accounts(
                    baseCurrency = baseCurrency,
                    accounts = accounts,
                    onClick = {
                        accountModalData = AccountModalData(
                            account = it.account,
                            baseCurrency = baseCurrency,
                            balance = it.balance,
                            autoFocusKeyboard = false
                        )
                    }
                )

                if (accounts.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(R.string.suggestions),
                    style = UI.typo.b1.style(
                        fontWeight = FontWeight.ExtraBold
                    )
                )

                Spacer(Modifier.height(16.dp))

                Suggestions(
                    suggestions = suggestions.filter { suggestion ->
                        accounts.map { it.account.name.toLowerCaseLocal() }
                            .contains(suggestion.name.toLowerCaseLocal()).not()
                    },
                    onAddSuggestion = {
                        onCreateAccount(it as CreateAccountData)
                    },
                    onAddNew = {
                        accountModalData = AccountModalData(
                            account = null,
                            baseCurrency = baseCurrency,
                            balance = 0.0
                        )
                    }
                )

                Spacer(Modifier.height(96.dp))
            }
        }
    }

    GradientCutBottom(
        height = 96.dp
    )

    if (accounts.isNotEmpty()) {
        OnboardingButton(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),

            text = stringResource(R.string.next),
            textColor = White,
            backgroundGradient = GradientIris,
            hasNext = true,
            enabled = true
        ) {
            onDoneClick()
        }
    }

    AccountModal(
        modal = accountModalData,
        onCreateAccount = onCreateAccount,
        onEditAccount = onEditAccount,
        dismiss = {
            accountModalData = null
        }
    )
}

@Composable
private fun Accounts(
    baseCurrency: String,
    accounts: List<AccountBalance>,
    onClick: (AccountBalance) -> Unit
) {
    for (account in accounts) {
        AccountCard(
            baseCurrency = baseCurrency,
            accountBalance = account
        ) {
            onClick(account)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountCard(
    baseCurrency: String,
    accountBalance: AccountBalance,
    onClick: () -> Unit
) {
    val account = accountBalance.account
    val accountColor = account.color.toComposeColor()
    val dynamicContrast = accountColor.dynamicContrast()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r3)
            .background(accountColor, UI.shapes.r3)
            .clickable {
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(24.dp))

        ItemIconMDefaultIcon(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .background(dynamicContrast, CircleShape),
            iconName = account.icon,
            defaultIcon = R.drawable.ic_custom_account_m,
            tint = accountColor
        )

        Spacer(Modifier.width(20.dp))

        Column {
            Text(
                text = account.name,
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = dynamicContrast
                )
            )

            AmountCurrencyB1Row(
                amount = accountBalance.balance,
                currency = account.currency ?: baseCurrency,
                amountFontWeight = FontWeight.ExtraBold,
                textColor = findContrastTextColor(accountColor)
            )
        }

        Spacer(Modifier.width(24.dp))
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Empty() {
    IrisWalletPreview {
        val baseCurrency = "BGN"
        OnboardingAccounts(
            baseCurrency = baseCurrency,
            suggestions = listOf(
                CreateAccountData(
                    name = "Cash",
                    currency = baseCurrency,
                    color = Green,
                    icon = "cash",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Bank",
                    currency = baseCurrency,
                    color = Iris,
                    icon = "bank",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Revolut",
                    currency = baseCurrency,
                    color = Color(0xFF4DCAFF),
                    icon = "revolut",
                    balance = 0.0
                ),
            ),
            accounts = listOf()
        )
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Accounts() {
    IrisWalletPreview {
        val baseCurrency = "BGN"
        OnboardingAccounts(
            baseCurrency = baseCurrency,
            suggestions = listOf(
                CreateAccountData(
                    name = "Cash",
                    currency = baseCurrency,
                    color = Green,
                    icon = "cash",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Bank",
                    currency = baseCurrency,
                    color = Iris,
                    icon = "bank",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Revolut",
                    currency = baseCurrency,
                    color = Color(0xFF4DCAFF),
                    icon = "revolut",
                    balance = 0.0
                ),
            ),
            accounts = listOf(
                AccountBalance(
                    account = Account(
                        name = "Cash",
                        color = Green.toArgb(),
                        icon = "cash"
                    ),
                    balance = 0.0
                )
            )
        )
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_Premium() {
    IrisWalletPreview {
        val baseCurrency = "BGN"
        OnboardingAccounts(
            baseCurrency = baseCurrency,
            suggestions = listOf(
                CreateAccountData(
                    name = "Cash",
                    currency = baseCurrency,
                    color = Green,
                    icon = "cash",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Bank",
                    currency = baseCurrency,
                    color = Iris,
                    icon = "bank",
                    balance = 0.0
                ),
                CreateAccountData(
                    name = "Revolut",
                    currency = baseCurrency,
                    color = Color(0xFF4DCAFF),
                    icon = "revolut",
                    balance = 0.0
                ),
            ),
            accounts = listOf(
                AccountBalance(
                    account = Account(
                        name = "Cash",
                        color = Green.toArgb(),
                        icon = "cash"
                    ),
                    balance = 0.0
                ),
                AccountBalance(
                    account = Account(
                        name = "Revolut",
                        color = IrisDark.toArgb(),
                        icon = "cash"
                    ),
                    balance = 0.0
                ),
                AccountBalance(
                    account = Account(
                        name = "Revolut",
                        color = Color(0xFF4DCAFF).toArgb(),
                        icon = "revolut"
                    ),
                    balance = 0.0
                ),
            )
        )
    }
}
