package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iris.data.db.entity.SyncChangeLogEntity

@Dao
interface WriteSyncChangeLogDao {

    /**
     * Replaces the change log entry of a record.
     *
     * Used when a record received from the remote is applied locally: the entry
     * must describe the remote write, otherwise the next push would upload the
     * remote content again under an outdated timestamp.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: SyncChangeLogEntity)

    /** Marks a change as successfully pushed to the remote. */
    @Query(
        """
        UPDATE sync_change_log
        SET pendingSync = 0, attemptCount = 0, lastError = NULL
        WHERE entityType = :entityType AND entityId = :entityId AND updatedAt <= :syncedUpTo
        """
    )
    suspend fun markSynced(entityType: String, entityId: String, syncedUpTo: Long)

    /** Records a failed push attempt, used for backoff and poison record detection. */
    @Query(
        """
        UPDATE sync_change_log
        SET attemptCount = attemptCount + 1, lastError = :error
        WHERE entityType = :entityType AND entityId = :entityId
        """
    )
    suspend fun recordFailure(entityType: String, entityId: String, error: String?)

    /** Drops tombstones that were already synced and are older than [olderThan]. */
    @Query(
        """
        DELETE FROM sync_change_log
        WHERE operation = 'DELETE' AND pendingSync = 0 AND updatedAt < :olderThan
        """
    )
    suspend fun pruneSyncedTombstones(olderThan: Long)

    @Query("DELETE FROM sync_change_log")
    suspend fun deleteAll()
}
