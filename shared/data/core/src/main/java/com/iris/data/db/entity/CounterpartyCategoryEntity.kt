package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Remembers the category the user last chose for a counterparty, so the same shop is pre-filled
 * next time (FR-024).
 *
 * The key is already case- and punctuation-insensitive by construction (`CounterpartyKey`), which
 * is why one row per counterparty is enough.
 *
 * Not `@Serializable` — see [FinancialSenderEntity].
 */
@Keep
@Entity(tableName = "counterparty_categories")
data class CounterpartyCategoryEntity(
    val categoryId: UUID,
    val updatedAt: Instant,

    @PrimaryKey
    val counterpartyKey: String,
)
