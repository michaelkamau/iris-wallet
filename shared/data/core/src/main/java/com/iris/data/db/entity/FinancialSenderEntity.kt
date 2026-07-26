package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A financial sender the user has allowed capture from (FR-003, FR-004, FR-027).
 *
 * Deliberately **not** `@Serializable`: un-reviewed capture configuration and data are excluded
 * from backup and CSV export (persistence-contract.md §6).
 *
 * @param ruleSetId the parser rule set responsible for this sender, or null when the sender is
 *   known but no rules cover it yet.
 * @param accountId null means every captured item from this sender needs an account chosen at
 *   review time (FR-027a). There is deliberately no fallback account.
 */
@Keep
@Entity(tableName = "financial_senders")
data class FinancialSenderEntity(
    val displayName: String,
    val ruleSetId: String?,
    val accountId: UUID?,
    val enabled: Boolean,

    @PrimaryKey
    val senderId: String,
)
