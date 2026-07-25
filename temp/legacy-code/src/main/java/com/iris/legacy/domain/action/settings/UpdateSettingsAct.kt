package com.iris.legacy.domain.action.settings

import com.iris.data.db.dao.write.WriteSettingsDao
import com.iris.frp.action.FPAction
import com.iris.legacy.datamodel.Settings
import javax.inject.Inject

class UpdateSettingsAct @Inject constructor(
    private val writeSettingsDao: WriteSettingsDao
) : FPAction<Settings, Settings>() {
    override suspend fun Settings.compose(): suspend () -> Settings = suspend {
        writeSettingsDao.save(this.toEntity())
        this
    }
}
