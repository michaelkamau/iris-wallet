package com.iris.data.model

import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble

data class ExchangeRate(
    val baseCurrency: AssetCode,
    val currency: AssetCode,
    val rate: PositiveDouble,
    val manualOverride: Boolean,
)
