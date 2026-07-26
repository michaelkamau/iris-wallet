package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Proof that a message was already handled, so re-delivery and historical import can never
 * duplicate it (FR-028) and a dismissed item can never come back (FR-025).
 *
 * Holds **no** message content and no hash preimage — only the fingerprint (FR-006).
 *
 * Not `@Serializable` — see [FinancialSenderEntity].
 *
 * @param fingerprint `"<ruleSetId>:<REFERENCE>"` or `"h:<32 hex chars>"`.
 * @param outcome `'CAPTURED'` | `'IGNORED'` | `'DISMISSED'`.
 */
@Keep
@Entity(
    tableName = "processed_messages",
    indices = [Index("processedAt")],
)
data class ProcessedMessageEntity(
    val senderId: String,
    val outcome: String,
    val processedAt: Instant,

    @PrimaryKey
    val fingerprint: String,
)
