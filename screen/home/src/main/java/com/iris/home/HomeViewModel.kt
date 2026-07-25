package com.iris.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.iris.base.legacy.Theme
import com.iris.base.legacy.Transaction
import com.iris.base.legacy.TransactionHistoryItem
import com.iris.base.time.TimeConverter
import com.iris.base.time.TimeProvider
import com.iris.data.model.primitive.AssetCode
import com.iris.data.repository.CategoryRepository
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.domain.features.Features
import com.iris.domain.usecase.exchange.SyncExchangeRatesUseCase
import com.iris.frp.fixUnit
import com.iris.frp.then
import com.iris.frp.thenInvokeAfter
import com.iris.home.customerjourney.CustomerJourneyCardModel
import com.iris.home.customerjourney.CustomerJourneyCardsProvider
import com.iris.legacy.IrisWalletCtx
import com.iris.legacy.data.AppBaseData
import com.iris.legacy.data.BufferInfo
import com.iris.legacy.data.LegacyDueSection
import com.iris.legacy.data.model.MainTab
import com.iris.legacy.data.model.TimePeriod
import com.iris.legacy.data.model.toUTCCloseTimeRange
import com.iris.legacy.datamodel.Account
import com.iris.legacy.datamodel.Settings
import com.iris.legacy.datamodel.temp.toLegacyDomain
import com.iris.legacy.domain.action.settings.UpdateSettingsAct
import com.iris.legacy.domain.action.viewmodel.home.ShouldHideIncomeAct
import com.iris.legacy.utils.dateNowUTC
import com.iris.legacy.utils.ioThread
import com.iris.navigation.BalanceScreen
import com.iris.navigation.MainScreen
import com.iris.navigation.Navigation
import com.iris.ui.ComposeViewModel
import com.iris.wallet.domain.action.account.AccountsAct
import com.iris.wallet.domain.action.global.StartDayOfMonthAct
import com.iris.wallet.domain.action.settings.CalcBufferDiffAct
import com.iris.wallet.domain.action.settings.SettingsAct
import com.iris.wallet.domain.action.transaction.HistoryWithDateDivsAct
import com.iris.wallet.domain.action.viewmodel.home.HasTrnsAct
import com.iris.wallet.domain.action.viewmodel.home.OverdueAct
import com.iris.wallet.domain.action.viewmodel.home.ShouldHideBalanceAct
import com.iris.wallet.domain.action.viewmodel.home.UpcomingAct
import com.iris.wallet.domain.action.viewmodel.home.UpdateAccCacheAct
import com.iris.wallet.domain.action.viewmodel.home.UpdateCategoriesCacheAct
import com.iris.wallet.domain.action.wallet.CalcIncomeExpenseAct
import com.iris.wallet.domain.action.wallet.CalcWalletBalanceAct
import com.iris.wallet.domain.deprecated.logic.PlannedPaymentsLogic
import com.iris.wallet.domain.pure.data.ClosedTimeRange
import com.iris.wallet.domain.pure.data.IncomeExpensePair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@Stable
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val irisContext: IrisWalletCtx,
    private val nav: Navigation,
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val customerJourneyLogic: CustomerJourneyCardsProvider,
    private val historyWithDateDivsAct: HistoryWithDateDivsAct,
    private val calcIncomeExpenseAct: CalcIncomeExpenseAct,
    private val calcWalletBalanceAct: CalcWalletBalanceAct,
    private val settingsAct: SettingsAct,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val calcBufferDiffAct: CalcBufferDiffAct,
    private val upcomingAct: UpcomingAct,
    private val overdueAct: OverdueAct,
    private val hasTrnsAct: HasTrnsAct,
    private val startDayOfMonthAct: StartDayOfMonthAct,
    private val shouldHideBalanceAct: ShouldHideBalanceAct,
    private val shouldHideIncomeAct: ShouldHideIncomeAct,
    private val updateSettingsAct: UpdateSettingsAct,
    private val updateAccCacheAct: UpdateAccCacheAct,
    private val updateCategoriesCacheAct: UpdateCategoriesCacheAct,
    private val syncExchangeRatesUseCase: SyncExchangeRatesUseCase,
    private val transactionMapper: TransactionMapper,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
    private val features: Features
) : ComposeViewModel<HomeState, HomeEvent>() {
    private var currentTheme by mutableStateOf(Theme.AUTO)
    private var name by mutableStateOf("")
    private var period by mutableStateOf(irisContext.selectedPeriod)
    private var baseData by mutableStateOf(
        AppBaseData(
            baseCurrency = "",
            accounts = persistentListOf(),
            categories = persistentListOf()
        )
    )
    private var history by mutableStateOf<ImmutableList<TransactionHistoryItem>>(persistentListOf())
    private var stats by mutableStateOf(IncomeExpensePair.zero())
    private var balance by mutableStateOf(BigDecimal.ZERO)
    private var buffer by mutableStateOf(
        BufferInfo(
            amount = BigDecimal.ZERO,
            bufferDiff = BigDecimal.ZERO,
        )
    )
    private var upcoming by mutableStateOf(
        LegacyDueSection(
            trns = persistentListOf(),
            stats = IncomeExpensePair.zero(),
            expanded = false,
        )
    )
    private var overdue by mutableStateOf(
        LegacyDueSection(
            trns = persistentListOf(),
            stats = IncomeExpensePair.zero(),
            expanded = false,
        )
    )
    private var customerJourneyCards by
    mutableStateOf<ImmutableList<CustomerJourneyCardModel>>(persistentListOf())
    private var hideBalance by mutableStateOf(false)
    private var hideIncome by mutableStateOf(false)
    private var expanded by mutableStateOf(true)

    @Composable
    override fun uiState(): HomeState {
        LaunchedEffect(Unit) {
            start()
        }

        return HomeState(
            theme = getTheme(),
            name = getName(),
            period = getPeriod(),
            baseData = getBaseData(),
            history = getHistory(),
            stats = getStats(),
            balance = getBalance(),
            buffer = getBuffer(),
            upcoming = getUpcoming(),
            overdue = getOverdue(),
            customerJourneyCards = getCustomerJourneyCards(),
            hideBalance = getHideBalance(),
            expanded = getExpanded(),
            hideIncome = getHideIncome(),
            shouldShowAccountSpecificColorInTransactions = getShouldShowAccountSpecificColorInTransactions()
        )
    }

    @Composable
    fun getShouldShowAccountSpecificColorInTransactions(): Boolean {
        return features.showAccountColorsInTransactions.asEnabledState()
    }

    @Composable
    private fun getTheme(): Theme {
        return currentTheme
    }

    @Composable
    private fun getName(): String {
        return name
    }

    @Composable
    private fun getPeriod(): TimePeriod {
        return period
    }

    @Composable
    private fun getBaseData(): AppBaseData {
        return baseData
    }

    @Composable
    private fun getHistory(): ImmutableList<TransactionHistoryItem> {
        return history
    }

    @Composable
    private fun getStats(): IncomeExpensePair {
        return stats
    }

    @Composable
    private fun getBalance(): BigDecimal {
        return balance
    }

    @Composable
    private fun getBuffer(): BufferInfo {
        return buffer
    }

    @Composable
    private fun getUpcoming(): LegacyDueSection {
        return upcoming
    }

    @Composable
    private fun getOverdue(): LegacyDueSection {
        return overdue
    }

    @Composable
    private fun getCustomerJourneyCards(): ImmutableList<CustomerJourneyCardModel> {
        return customerJourneyCards
    }

    @Composable
    private fun getHideBalance(): Boolean {
        return hideBalance
    }

    @Composable
    private fun getExpanded(): Boolean {
        return expanded
    }

    @Composable
    private fun getHideIncome(): Boolean {
        return hideIncome
    }

    override fun onEvent(event: HomeEvent) {
        viewModelScope.launch {
            when (event) {
                HomeEvent.BalanceClick -> onBalanceClick()
                HomeEvent.HiddenBalanceClick -> onHiddenBalanceClick()
                HomeEvent.HiddenIncomeClick -> onHiddenIncomeClick()
                is HomeEvent.PayOrGetPlanned -> payOrGetPlanned(event.transaction)
                is HomeEvent.SkipPlanned -> skipPlanned(event.transaction)
                is HomeEvent.SkipAllPlanned -> skipAllPlanned(event.transactions)
                is HomeEvent.SetPeriod -> setPeriod(event.period)
                HomeEvent.SelectNextMonth -> onSelectNextMonth()
                HomeEvent.SelectPreviousMonth -> onSelectPreviousMonth()
                is HomeEvent.SetUpcomingExpanded -> setUpcomingExpanded(event.expanded)
                is HomeEvent.SetOverdueExpanded -> setOverdueExpanded(event.expanded)
                is HomeEvent.SetBuffer -> setBuffer(event.buffer)
                is HomeEvent.SetCurrency -> setCurrency(event.currency).fixUnit()
                HomeEvent.SwitchTheme -> switchTheme()
                is HomeEvent.DismissCustomerJourneyCard -> dismissCustomerJourneyCard(event.card)
                is HomeEvent.SetExpanded -> setExpanded(event.expanded)
            }
        }
    }

    private suspend fun start() {
        suspend {
            val startDay = startDayOfMonthAct(Unit)
            irisContext.initSelectedPeriodInMemory(
                startDayOfMonth = startDay
            )
        } thenInvokeAfter ::reload
    }

    // -----------------------------------------------------------------------------------
    private suspend fun reload(
        timePeriod: TimePeriod = irisContext.selectedPeriod
    ) = suspend {
        val settings = settingsAct(Unit)
        val hideBalance = shouldHideBalanceAct(Unit)
        val hideIncome = shouldHideIncomeAct(Unit)

        currentTheme = settings.theme
        name = settings.name
        period = timePeriod
        this.hideBalance = hideBalance
        this.hideIncome = hideIncome

        // This method is used to restore the theme when user imports locally backed up data
        irisContext.switchTheme(theme = settings.theme)

        Pair(
            settings,
            period.toRange(
                startDateOfMonth = irisContext.startDayOfMonth,
                timeConverter = timeConverter,
                timeProvider = timeProvider
            ).toUTCCloseTimeRange()
        )
    } then ::loadAppBaseData then ::loadIncomeExpenseBalance then
            ::loadBuffer then ::loadTrnHistory then
            ::loadDueTrns thenInvokeAfter ::loadCustomerJourney

    private suspend fun loadAppBaseData(
        input: Pair<Settings, ClosedTimeRange>
    ): Triple<Settings, ClosedTimeRange, List<Account>> =
        suspend {} then accountsAct then updateAccCacheAct then { accounts ->
            accounts
        } then { accounts ->
            val retrievedCategories = categoryRepository.findAll()
            val categories = updateCategoriesCacheAct(retrievedCategories)
            accounts to categories
        } thenInvokeAfter { (accounts, categories) ->
            val (settings, timeRange) = input

            baseData = AppBaseData(
                baseCurrency = settings.baseCurrency,
                categories = categories.toImmutableList(),
                accounts = accounts.toImmutableList()
            )

            Triple(settings, timeRange, accounts)
        }

    private suspend fun loadIncomeExpenseBalance(
        input: Triple<Settings, ClosedTimeRange, List<Account>>
    ): Triple<Settings, ClosedTimeRange, BigDecimal> {
        val (settings, timeRange, accounts) = input

        val incomeExpense = calcIncomeExpenseAct(
            CalcIncomeExpenseAct.Input(
                baseCurrency = settings.baseCurrency,
                accounts = accounts,
                range = timeRange
            )
        )

        val balanceAmount = calcWalletBalanceAct(
            CalcWalletBalanceAct.Input(baseCurrency = settings.baseCurrency)
        )

        balance = balanceAmount
        stats = incomeExpense

        return Triple(settings, timeRange, balanceAmount)
    }

    private suspend fun loadBuffer(
        input: Triple<Settings, ClosedTimeRange, BigDecimal>
    ): Pair<String, ClosedTimeRange> {
        val (settings, timeRange, balance) = input

        buffer = BufferInfo(
            amount = settings.bufferAmount,
            bufferDiff = calcBufferDiffAct(
                CalcBufferDiffAct.Input(
                    balance = balance,
                    buffer = settings.bufferAmount
                )
            )
        )

        return settings.baseCurrency to timeRange
    }

    private suspend fun loadTrnHistory(
        input: Pair<String, ClosedTimeRange>
    ): Pair<String, ClosedTimeRange> {
        val (baseCurrency, timeRange) = input

        history = historyWithDateDivsAct(
            HistoryWithDateDivsAct.Input(
                range = timeRange,
                baseCurrency = baseCurrency
            )
        )

        return baseCurrency to timeRange
    }

    private suspend fun loadDueTrns(
        input: Pair<String, ClosedTimeRange>
    ): Unit = suspend {
        UpcomingAct.Input(baseCurrency = input.first, range = input.second)
    } then upcomingAct then { result ->
        upcoming = LegacyDueSection(
            trns = with(transactionMapper) {
                result.upcomingTrns.map {
                    it.toEntity().toLegacyDomain()
                }.toImmutableList()
            },
            stats = result.upcoming,
            expanded = upcoming.expanded
        )
    } then {
        OverdueAct.Input(baseCurrency = input.first, toRange = input.second.to)
    } then overdueAct thenInvokeAfter { result ->
        overdue = LegacyDueSection(
            trns = with(transactionMapper) {
                result.overdueTrns.map {
                    it.toEntity().toLegacyDomain()
                }.toImmutableList()
            },
            stats = result.overdue,
            expanded = overdue.expanded
        )
    }

    private suspend fun loadCustomerJourney(unit: Unit) {
        customerJourneyCards = ioThread {
            customerJourneyLogic.loadCards().toImmutableList()
        }
    }
// -----------------------------------------------------------------

    private fun setUpcomingExpanded(expanded: Boolean) {
        upcoming = upcoming.copy(expanded = expanded)
    }

    private fun setOverdueExpanded(expanded: Boolean) {
        overdue = overdue.copy(expanded = expanded)
    }

    private suspend fun onBalanceClick() {
        val hasTransactions = hasTrnsAct(Unit)
        if (hasTransactions) {
            // has transactions show him "Balance" screen
            nav.navigateTo(BalanceScreen)
        } else {
            // doesn't have transactions lead him to adjust balance
            irisContext.selectMainTab(MainTab.ACCOUNTS)
            nav.navigateTo(MainScreen)
        }
    }

    private suspend fun onHiddenBalanceClick() {
        hideBalance = false

        // Showing Balance fow 5s
        delay(5000)

        hideBalance = true
    }

    private suspend fun onHiddenIncomeClick() {
        hideIncome = false

        // Showing Balance fow 5s
        delay(5000)

        hideIncome = true
    }

    private fun switchTheme() {
        viewModelScope.launch {
            settingsAct.getSettingsWithNextTheme().run {
                updateSettingsAct(this)
                irisContext.switchTheme(this.theme)
                currentTheme = this.theme
            }
        }
    }

    private fun setBuffer(newBuffer: Double) {
        viewModelScope.launch {
            val currentSettings =
                settingsAct.getSettings().copy(bufferAmount = newBuffer.toBigDecimal())
            updateSettingsAct(currentSettings)
            buffer = buffer.copy(amount = currentSettings.bufferAmount)
        }
    }

    private suspend fun setCurrency(newCurrency: String) = settingsAct then {
        it.copy(
            baseCurrency = newCurrency
        )
    } then updateSettingsAct then {
        // update exchange rates from POV of the new base currency
        AssetCode.from(newCurrency).onRight {
            syncExchangeRatesUseCase.sync(it)
        }
    } then {
        reload()
    }

    private suspend fun payOrGetPlanned(transaction: Transaction) {
        plannedPaymentsLogic.payOrGetLegacy(
            transaction = transaction,
            skipTransaction = false
        ) {
            reload()
        }
    }

    private suspend fun skipPlanned(transaction: Transaction) {
        plannedPaymentsLogic.payOrGetLegacy(
            transaction = transaction,
            skipTransaction = true
        ) {
            reload()
        }
    }

    private suspend fun skipAllPlanned(transactions: List<Transaction>) {
        plannedPaymentsLogic.payOrGetLegacy(
            transactions = transactions,
            skipTransaction = true
        ) {
            reload()
        }
    }

    private suspend fun dismissCustomerJourneyCard(card: CustomerJourneyCardModel) = suspend {
        customerJourneyLogic.dismissCard(card)
    } thenInvokeAfter {
        reload()
    }

    private suspend fun onSelectNextMonth() {
        val month = period.month
        val year = period.year ?: dateNowUTC().year
        val period = month?.incrementMonthPeriod(irisContext, 1L, year = year)
        if (period != null) {
            setPeriod(period)
        }
    }

    private suspend fun onSelectPreviousMonth() {
        val month = period.month
        val year = period.year ?: dateNowUTC().year
        val period = month?.incrementMonthPeriod(irisContext, -1L, year = year)
        if (period != null) {
            setPeriod(period)
        }
    }

    private suspend fun setPeriod(period: TimePeriod) {
        reload(period)
    }

    @JvmName("setExpandedMethod")
    private fun setExpanded(expanded: Boolean) {
        this.expanded = expanded
    }
}
