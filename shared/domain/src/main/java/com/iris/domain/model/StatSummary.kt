package com.iris.domain.model

import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.NonNegativeInt
import com.iris.data.model.primitive.PositiveDouble

data class StatSummary(
    val trnCount: NonNegativeInt,
    val values: Map<AssetCode, PositiveDouble>,
) {
    companion object {
        val Zero = StatSummary(
            values = emptyMap(),
            trnCount = NonNegativeInt.Zero
        )
    }
}