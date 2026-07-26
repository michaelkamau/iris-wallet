package com.iris.data.db.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A transaction extracted from one message and waiting for the user to review it.
 *
 * This table is the *entire* mechanism behind FR-021a: a pending item lives only here, so it
 * cannot reach balances, budgets, reports, search or export until confirm moves it into
 * `transactions`.
 *
 * [kind], [direction] and [origin] are stored as plain `TEXT` rather than through a type converter
 * so the DDL in the migration reads exactly as written and `pendingCount()` can compare
 * `kind = 'PRINCIPAL'` literally. Reconstructing the `CapturedKind` ADT is the mapper's job.
 *
 * Not `@Serializable` — see [FinancialSenderEntity].
 *
 * @param direction `'MONEY_OUT'`/`'MONEY_IN'` for a principal; null for a fee.
 * @param parentId the principal a fee belongs to; null for a standalone fee (FR-020).
 * @param taxAmount the tax portion inside a transaction cost; fee rows only (FR-019).
 * @param duplicateOfTransactionId an existing ledger transaction that looks like the same event
 *   (FR-029). Advisory only — the user decides.
 */
@Keep
@Entity(
    tableName = "captured_transactions",
    indices = [
        Index("parentId"),
        Index("dateTime"),
    ],
)
data class CapturedTransactionEntity(
    val senderId: String,
    val kind: String,
    val direction: String?,
    val parentId: UUID?,
    val taxAmount: Double?,
    val amount: Double,
    val assetCode: String,
    val dateTime: Instant,
    val counterparty: String?,
    val reference: String?,
    val accountId: UUID?,
    val categoryId: UUID?,
    val description: String?,
    val duplicateOfTransactionId: UUID?,
    val capturedAt: Instant,
    val origin: String,

    @PrimaryKey
    val id: UUID,
)
