package com.iris.sms.parser.primitive

import arrow.core.Either
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.CounterpartyKey
import javax.inject.Inject

/**
 * Turns the counterparty fragment of a message into a name a human would recognise (FR-014):
 * `"james Kinyua Mwangi9."` becomes `"James Kinyua Mwangi"`.
 *
 * The steps are fixed and shared by every rule set, so a wording fix never has to re-implement
 * them:
 *
 * 1. drop masked-identifier fragments (`254****956`, `5XXXXX5001`);
 * 2. drop a trailing run of digits and punctuation (`Mwangi9.` -> `Mwangi`);
 * 3. collapse internal whitespace;
 * 4. title-case each word, keeping words that carry a digit, and keeping a name that is *only* an
 *    all-caps acronym of four letters or fewer (`KCB` stays `KCB`);
 * 5. reject an empty result, so a blank counterparty is unrepresentable.
 */
class CounterpartyNormalizer @Inject constructor() {

    fun normalize(raw: String): Either<String, NotBlankTrimmedString> {
        val words = raw
            .replace(MASKED_FRAGMENT, " ")
            .replace(TRAILING_NOISE, "")
            .split(WHITESPACE)
            .filter(String::isNotBlank)
        val cleaned = if (words.isAcronymOnly()) {
            words.joinToString(" ")
        } else {
            words.joinToString(" ", transform = ::titleCase)
        }
        return NotBlankTrimmedString.from(cleaned)
    }

    /**
     * Uppercases and drops every non-alphanumeric, so `"Frank Inn Kikuyu"` and
     * `"FRANK INN KIKUYU"` share one counterparty→category memory row (FR-024).
     *
     * Returns `Either` rather than a bare [CounterpartyKey] because a [NotBlankTrimmedString] may
     * still be pure punctuation, and this module never throws.
     */
    fun key(name: NotBlankTrimmedString): Either<String, CounterpartyKey> =
        CounterpartyKey.from(name.value)

    /**
     * `KCB` is a name; `INC.` inside `GITHUB, INC. SAN FRANCISCO CA` is not. The distinction that
     * holds for both is whether the *whole* counterparty is one short all-caps token.
     */
    private fun List<String>.isAcronymOnly(): Boolean {
        val only = singleOrNull() ?: return false
        return only.length <= ACRONYM_MAX && only.all { it.isLetter() && it.isUpperCase() }
    }

    private fun titleCase(word: String): String = if (word.any(Char::isDigit)) {
        word
    } else {
        word.lowercase().replaceFirstChar(Char::titlecaseChar)
    }

    private companion object {
        const val ACRONYM_MAX = 4

        /** `5XXXXX5001`, `254****956` — a masked identifier is never a name. */
        val MASKED_FRAGMENT = Regex("[\\dX*]*[X*]{2,}[\\dX*]*")

        /** A trailing run of digits and punctuation: `Mwangi9.` -> `Mwangi`. */
        val TRAILING_NOISE = Regex("[\\d\\s\\-_/\\\\.]+$")

        val WHITESPACE = Regex("\\s+")
    }
}
