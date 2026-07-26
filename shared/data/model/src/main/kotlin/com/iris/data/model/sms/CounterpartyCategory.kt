package com.iris.data.model.sms

import com.iris.data.model.CategoryId
import java.time.Instant

/**
 * The user's most recent category choice for a counterparty (FR-024).
 *
 * Written on every confirm where a category was chosen; upsert semantics make
 * "the most recent choice is the one suggested" the natural behaviour.
 */
data class CounterpartyCategory(
    val counterparty: CounterpartyKey,
    val category: CategoryId,
    val updatedAt: Instant,
)
