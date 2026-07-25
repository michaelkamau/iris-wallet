package com.iris.wallet.domain.action.settings

import com.iris.data.db.dao.read.SettingsDao
import com.iris.frp.action.FPAction
import javax.inject.Inject

class BaseCurrencyAct @Inject constructor(
    private val settingsDao: SettingsDao
) : FPAction<Unit, String>() {
    override suspend fun Unit.compose(): suspend () -> String = suspend {
        io { settingsDao.findFirst().currency }
    }
}
