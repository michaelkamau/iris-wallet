package com.iris.wallet.domain.action.settings

import com.iris.base.legacy.Theme
import com.iris.data.db.dao.read.SettingsDao
import com.iris.frp.action.FPAction
import com.iris.frp.then
import com.iris.legacy.datamodel.Settings
import com.iris.legacy.datamodel.temp.toLegacyDomain
import javax.inject.Inject

class SettingsAct @Inject constructor(
    private val settingsDao: SettingsDao
) : FPAction<Unit, Settings>() {
    override suspend fun Unit.compose(): suspend () -> Settings = suspend {
        io { settingsDao.findFirst() }
    } then { it.toLegacyDomain() }

    suspend fun getSettingsWithNextTheme(): Settings {
        val currentSettings = this(Unit)
        val newTheme = when (currentSettings.theme) {
            Theme.LIGHT -> Theme.DARK
            Theme.DARK -> Theme.AMOLED_DARK
            Theme.AMOLED_DARK -> Theme.AUTO
            Theme.AUTO -> Theme.LIGHT
        }
        return currentSettings.copy(theme = newTheme)
    }

    suspend fun getSettings(): Settings = this(Unit)
}