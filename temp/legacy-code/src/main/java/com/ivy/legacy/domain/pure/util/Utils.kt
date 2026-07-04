package com.ivy.wallet.domain.pure.util

import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.toNonEmptyListOrNull
import java.math.BigDecimal

fun <T> NonEmptyList<T>.mapIndexedNel(
    f: (Int, T) -> T
): NonEmptyList<T> {
    return requireNotNull(
        this.mapIndexed(f).toNonEmptyListOrNull()
    )
}

suspend fun <T> NonEmptyList<T>.mapIndexedNelSuspend(
    f: suspend (Int, T) -> T
): NonEmptyList<T> {
    val result = mutableListOf<T>()
    for ((index, elem) in this.withIndex()) {
        result.add(f(index, elem))
    }
    return requireNotNull(result.toNonEmptyListOrNull())
}

fun nonEmptyListOfZeros(n: Int): NonEmptyList<BigDecimal> {
    return requireNotNull(
        List(n) { BigDecimal.ZERO }.toNonEmptyListOrNull()
    )
}

fun Option<BigDecimal>.orZero(): BigDecimal {
    return this.getOrNull() ?: BigDecimal.ZERO
}
