package com.iris.legacy.datamodel.temp

import com.iris.data.db.entity.SettingsEntity
import com.iris.legacy.datamodel.Settings

fun SettingsEntity.toLegacyDomain(): Settings = Settings(
    theme = theme,
    baseCurrency = currency,
    bufferAmount = bufferAmount.toBigDecimal(),
    name = name,
    id = id
)
