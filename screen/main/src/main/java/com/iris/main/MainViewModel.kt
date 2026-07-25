package com.iris.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.base.legacy.SharedPrefs
import com.iris.data.repository.CurrencyRepository
import com.iris.domain.usecase.exchange.SyncExchangeRatesUseCase
import com.iris.frp.test.TestIdlingResource
import com.iris.legacy.IrisWalletCtx
import com.iris.legacy.data.model.MainTab
import com.iris.legacy.domain.deprecated.logic.AccountCreator
import com.iris.legacy.utils.asLiveData
import com.iris.legacy.utils.ioThread
import com.iris.navigation.MainScreen
import com.iris.navigation.Navigation
import com.iris.wallet.domain.deprecated.logic.model.CreateAccountData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val irisContext: IrisWalletCtx,
    private val nav: Navigation,
    private val syncExchangeRatesUseCase: SyncExchangeRatesUseCase,
    private val accountCreator: AccountCreator,
    private val sharedPrefs: SharedPrefs,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _currency = MutableLiveData<String>()
    val currency = _currency.asLiveData()

    fun start(screen: MainScreen) {
        nav.onBackPressed[screen] = {
            if (irisContext.mainTab == MainTab.ACCOUNTS) {
                irisContext.selectMainTab(MainTab.HOME)
                true
            } else {
                // Exiting (the backstack will close the app)
                false
            }
        }

        viewModelScope.launch {
            TestIdlingResource.increment()

            val baseCurrency = currencyRepository.getBaseCurrency()
            _currency.value = baseCurrency.code

            irisContext.dataBackupCompleted =
                sharedPrefs.getBoolean(SharedPrefs.DATA_BACKUP_COMPLETED, false)

            ioThread {
                // Sync exchange rates
                syncExchangeRatesUseCase.sync(baseCurrency)
            }

            TestIdlingResource.decrement()
        }
    }

    fun selectTab(tab: MainTab) {
        irisContext.selectMainTab(tab)
    }

    fun createAccount(data: CreateAccountData) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            accountCreator.createAccount(data) {}

            TestIdlingResource.decrement()
        }
    }
}
