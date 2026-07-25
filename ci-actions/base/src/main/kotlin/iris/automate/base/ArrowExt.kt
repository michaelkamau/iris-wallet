package iris.automate.base

import arrow.core.Either

fun <A, B> Either<A, B>.getOrThrow(): B {
    return fold(
        ifLeft = { throw IrisError(it.toString()) },
        ifRight = { it }
    )
}
