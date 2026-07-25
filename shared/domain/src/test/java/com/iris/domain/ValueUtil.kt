package com.iris.domain

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import com.iris.data.model.PositiveValue
import com.iris.data.model.primitive.PositiveDouble

fun <T> List<T>.toNonEmptyList(): NonEmptyList<T> =
    this.toNonEmptyListOrNull() ?: error("Test setup error! Expected $this to be non-empty.")

fun NonEmptyList<PositiveValue>.sum(): PositiveDouble {
    var sum = 0.0
    for (value in this) {
        PositiveDouble.from(sum + value.amount.value)
            .onRight { newSum ->
                sum = newSum.value
            }
            .onLeft {
                sum = Double.MAX_VALUE
            }
    }
    return PositiveDouble.unsafe(sum)
}