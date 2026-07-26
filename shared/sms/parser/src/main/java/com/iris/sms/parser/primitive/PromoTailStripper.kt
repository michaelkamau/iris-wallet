package com.iris.sms.parser.primitive

import kotlinx.collections.immutable.ImmutableList
import javax.inject.Inject

/**
 * Truncates a message at its first promotional or instructional tail — `Download My OneApp on
 * https://…`, `Dial *522#` and friends (FR-011).
 *
 * Stripping happens before matching *and* before description extraction, so promo text can reach
 * neither the counterparty nor the stored description.
 */
class PromoTailStripper @Inject constructor() {

    fun strip(body: String, markers: ImmutableList<Regex>): String {
        val cut = markers
            .mapNotNull { it.find(body)?.range?.first }
            .minOrNull()
            ?: return body.trim()
        return body.take(cut).trim()
    }
}
