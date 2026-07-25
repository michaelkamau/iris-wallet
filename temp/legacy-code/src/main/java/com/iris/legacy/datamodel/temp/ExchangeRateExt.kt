package com.iris.legacy.datamodel.temp

import com.iris.data.db.entity.ExchangeRateEntity
import com.iris.legacy.datamodel.ExchangeRate

fun ExchangeRateEntity.toLegacyDomain(): ExchangeRate = ExchangeRate(
    baseCurrency = baseCurrency,
    currency = currency,
    rate = rate
)
