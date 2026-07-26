package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.CapturedTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface CapturedTransactionDao {
    @Query("SELECT * FROM captured_transactions ORDER BY dateTime DESC")
    suspend fun findAll(): List<CapturedTransactionEntity>

    @Query("SELECT * FROM captured_transactions WHERE id = :id")
    suspend fun findById(id: UUID): CapturedTransactionEntity?

    @Query("SELECT * FROM captured_transactions WHERE parentId = :parentId")
    suspend fun findByParentId(parentId: UUID): List<CapturedTransactionEntity>

    /**
     * Counts principals only, so a message that produced a transaction plus its cost reads as one
     * thing to review — which is what the badge and the settings row promise (FR-021a).
     */
    @Query("SELECT COUNT(*) FROM captured_transactions WHERE kind = 'PRINCIPAL'")
    fun pendingCount(): Flow<Int>
}
