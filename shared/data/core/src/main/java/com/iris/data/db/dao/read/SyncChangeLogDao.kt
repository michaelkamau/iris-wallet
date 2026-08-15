package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.SyncChangeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncChangeLogDao {

    @Query(
        """
        SELECT * FROM sync_change_log
        WHERE pendingSync = 1 AND attemptCount < :maxAttempts
        ORDER BY updatedAt ASC LIMIT :limit
        """
    )
    suspend fun findPending(limit: Int, maxAttempts: Int): List<SyncChangeLogEntity>

    @Query("SELECT COUNT(*) FROM sync_change_log WHERE pendingSync = 1")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM sync_change_log WHERE pendingSync = 1")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_change_log WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun findByEntity(entityType: String, entityId: String): SyncChangeLogEntity?

    @Query("SELECT * FROM sync_change_log WHERE entityType = :entityType")
    suspend fun findByEntityType(entityType: String): List<SyncChangeLogEntity>
}
