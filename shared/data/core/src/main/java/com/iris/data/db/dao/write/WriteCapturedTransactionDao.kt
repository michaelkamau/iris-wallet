package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.iris.data.db.entity.CapturedTransactionEntity
import java.util.UUID

@Dao
interface WriteCapturedTransactionDao {
    @Upsert
    suspend fun save(value: CapturedTransactionEntity)

    @Upsert
    suspend fun saveMany(value: List<CapturedTransactionEntity>)

    @Query("DELETE FROM captured_transactions WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /** Deleting a principal must take its fee with it — a fee alone is not reviewable (FR-018). */
    @Query("DELETE FROM captured_transactions WHERE parentId = :parentId")
    suspend fun deleteByParentId(parentId: UUID)

    @Query("DELETE FROM captured_transactions")
    suspend fun deleteAll()
}
