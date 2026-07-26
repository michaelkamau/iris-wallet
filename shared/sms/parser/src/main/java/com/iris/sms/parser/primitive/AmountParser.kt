package com.iris.sms.parser.primitive

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import javax.inject.Inject

/** The only currency this release handles (research.md D9). */
val KenyanShilling: AssetCode = AssetCode.unsafe("KES")

/**
 * Turns an amount as written in a Kenyan SMS into a [PositiveDouble].
 *
 * `"Ksh1,350.00"`, `"KES 540.25"` and `"6000.00 KES"` all parse; `"Ksh0.00"` is a `Left` **by
 * construction**, because [PositiveDouble] rejects zero. That is what makes FR-012 and the
 * "a transaction cost of 0.00 is not a fee" rule (FR-016) need no extra code path.
 */
class AmountParser @Inject constructor() {

    fun parse(raw: String): Either<String, PositiveDouble> = either {
        val stripped = raw
            .replace(CURRENCY_PREFIX, "")
            .replace(CURRENCY_SUFFIX, "")
            .replace(GROUPING, "")
            .trim()
        ensure(stripped.matches(DECIMAL)) { "'$raw' is not a decimal amount" }

        val amount = stripped.toDoubleOrNull()
        ensure(amount != null) { "'$raw' is not a number" }
        PositiveDouble.from(amount).bind()
    }

    private companion object {
        /** `Ksh1,350.00`, `KES 540.25`, `KShs. 20` — the marker leads the number. */
        val CURRENCY_PREFIX = Regex("(?i)^\\s*(?:kshs?|kes)\\.?\\s*")

        /** `6000.00 KES`, `20/=` — the marker trails the number. */
        val CURRENCY_SUFFIX = Regex("(?i)\\s*(?:(?:kshs?|kes)\\.?|/=)\\s*$")

        /** Thousands separators and any whitespace left inside the number. */
        val GROUPING = Regex("[,\\s]")

        val DECIMAL = Regex("\\d+(?:\\.\\d+)?")
    }
}
