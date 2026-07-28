package com.iris.home.customerjourney

import com.iris.base.legacy.SharedPrefs
import com.iris.base.legacy.stringRes
import com.iris.base.model.TransactionType
import com.iris.data.db.dao.read.PlannedPaymentRuleDao
import com.iris.data.repository.TransactionRepository
import com.iris.design.l0_system.Blue
import com.iris.design.l0_system.Blue3
import com.iris.design.l0_system.Gradient
import com.iris.design.l0_system.Green
import com.iris.design.l0_system.GreenLight
import com.iris.design.l0_system.Iris
import com.iris.design.l0_system.Orange
import com.iris.design.l0_system.Red
import com.iris.design.l0_system.Red3
import com.iris.legacy.Constants
import com.iris.legacy.IrisWalletCtx
import com.iris.legacy.data.model.MainTab
import com.iris.navigation.EditPlannedScreen
import com.iris.navigation.PieChartStatisticScreen
import com.iris.ui.R
import com.iris.widget.transaction.AddTransactionWidgetCompact
import javax.inject.Inject

@Deprecated("Legacy code")
class CustomerJourneyCardsProvider @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val sharedPrefs: SharedPrefs,
    private val irisContext: IrisWalletCtx
) {

    suspend fun loadCards(): List<CustomerJourneyCardModel> {
        val trnCount = transactionRepository.countHappenedTransactions().value
        val plannedPaymentsCount = plannedPaymentRuleDao.countPlannedPayments()

        return ACTIVE_CARDS
            .filter {
                it.condition(trnCount, plannedPaymentsCount, irisContext) && !isCardDismissed(it)
            }
    }

    private fun isCardDismissed(cardData: CustomerJourneyCardModel): Boolean {
        return sharedPrefs.getBoolean(sharedPrefsKey(cardData), false)
    }

    fun dismissCard(cardData: CustomerJourneyCardModel) {
        sharedPrefs.putBoolean(sharedPrefsKey(cardData), true)
    }

    private fun sharedPrefsKey(cardData: CustomerJourneyCardModel): String {
        return "${cardData.id}${SharedPrefs._CARD_DISMISSED}"
    }

    companion object {
        val ACTIVE_CARDS = listOf(
            adjustBalanceCard(),
            addPlannedPaymentCard(),
            didYouKnow_pinAddTransactionWidgetCard(),
            didYouKnow_expensesPieChart(),
        )

        fun adjustBalanceCard() = CustomerJourneyCardModel(
            id = "adjust_balance",
            condition = { trnCount, _, _ ->
                trnCount == 0L
            },
            title = stringRes(R.string.adjust_initial_balance),
            description = stringRes(R.string.adjust_initial_balance_description),
            cta = stringRes(R.string.to_accounts),
            ctaIcon = R.drawable.ic_custom_account_s,
            background = Gradient.solid(Iris),
            hasDismiss = false,
            onAction = { _, irisContext, _ ->
                irisContext.selectMainTab(MainTab.ACCOUNTS)
            }
        )

        fun addPlannedPaymentCard() = CustomerJourneyCardModel(
            id = "add_planned_payment",
            condition = { trnCount, plannedPaymentCount, _ ->
                trnCount >= 1 && plannedPaymentCount == 0L
            },
            title = stringRes(R.string.create_first_planned_payment),
            description = stringRes(R.string.create_first_planned_payment_description),
            cta = stringRes(R.string.add_planned_payment),
            ctaIcon = R.drawable.ic_planned_payments,
            background = Gradient.solid(Orange),
            hasDismiss = true,
            onAction = { navigation, _, _ ->
                navigation.navigateTo(
                    EditPlannedScreen(
                        type = TransactionType.EXPENSE,
                        plannedPaymentRuleId = null
                    )
                )
            }
        )

        fun didYouKnow_pinAddTransactionWidgetCard() = CustomerJourneyCardModel(
            id = "add_transaction_widget",
            condition = { trnCount, _, _ ->
                trnCount >= 3
            },
            title = stringRes(R.string.did_you_know),
            description = stringRes(R.string.widget_description),
            cta = stringRes(R.string.add_widget),
            ctaIcon = R.drawable.ic_custom_atom_s,
            background = Gradient.solid(GreenLight),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.pinWidget(AddTransactionWidgetCompact::class.java)
            }
        )

        fun didYouKnow_expensesPieChart() = CustomerJourneyCardModel(
            id = "expenses_pie_chart",
            condition = { trnCount, _, _ ->
                trnCount >= 7
            },
            title = stringRes(R.string.did_you_know),
            description = stringRes(R.string.you_can_see_a_piechart),
            cta = stringRes(R.string.expenses_piechart),
            ctaIcon = R.drawable.ic_custom_bills_s,
            background = Gradient.solid(Red),
            hasDismiss = true,
            onAction = { navigation, _, _ ->
                navigation.navigateTo(PieChartStatisticScreen(type = TransactionType.EXPENSE))
            }
        )

        fun rateUsCard() = CustomerJourneyCardModel(
            id = "rate_us",
            condition = { trnCount, _, _ ->
                trnCount >= 10
            },
            title = stringRes(R.string.review_iris_wallet),
            description = stringRes(R.string.review_iris_wallet_description),
            cta = stringRes(R.string.rate_us_on_google_play),
            ctaIcon = R.drawable.ic_custom_star_s,
            background = Gradient.solid(Green),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.reviewIrisWallet(dismissReviewCard = true)
            }
        )

        fun shareIrisWalletCard() = CustomerJourneyCardModel(
            id = "share_iris_wallet",
            condition = { trnCount, _, _ ->
                trnCount >= 11
            },
            title = stringRes(R.string.share_iris_wallet),
            description = stringRes(R.string.help_us_grow),
            cta = stringRes(R.string.share_with_friends),
            ctaIcon = R.drawable.ic_custom_family_s,
            background = Gradient.solid(Red3),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.shareIrisWallet()
            }
        )

        fun irisWalletIsOpenSource() = CustomerJourneyCardModel(
            id = "open_source",
            condition = { trnCount, _, _ ->
                trnCount >= 20
            },
            title = stringRes(R.string.iris_wallet_is_opensource),
            description = stringRes(R.string.iris_wallet_is_opensource_description),
            cta = stringRes(R.string.contribute),
            ctaIcon = R.drawable.github_logo,
            background = Gradient.solid(Blue3),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.openUrlInBrowser(Constants.URL_IRIS_WALLET_REPO)
            }
        )

        fun rateUsCard_2() = CustomerJourneyCardModel(
            id = "rate_us_2",
            condition = { trnCount, _, _ ->
                trnCount >= 22
            },
            title = stringRes(R.string.review_iris_wallet),
            description = stringRes(R.string.make_iris_wallet_better_description),
            cta = stringRes(R.string.rate_us_on_google_play),
            ctaIcon = R.drawable.ic_custom_star_s,
            background = Gradient.solid(GreenLight),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.reviewIrisWallet(dismissReviewCard = true)
            }
        )

        fun bugsApology(): CustomerJourneyCardModel = CustomerJourneyCardModel(
            id = "bugs_apology_1",
            condition = { trnCount, _, _ ->
                trnCount > 10
            },
            title = "Apologies for the bugs!",
            description = "Iris Wallet v4.6.2 had some annoying bugs... " +
                    "We're sorry for that and we hope that we have fixed them.\n\n" +
                    "Iris Wallet is an open-source and community-driven project " +
                    "that is maintained and develop solely by voluntary contributors. " +
                    "So to help us and make your experience better, " +
                    "please report any bugs as a GitHub issue. You can also" +
                    " join our community and become a contributor!",
            cta = "Report a bug",
            ctaIcon = R.drawable.github_logo,
            background = Gradient.solid(Blue),
            hasDismiss = true,
            onAction = { _, _, irisActivity ->
                irisActivity.openUrlInBrowser(Constants.URL_GITHUB_NEW_ISSUE)
            }
        )
    }
}
