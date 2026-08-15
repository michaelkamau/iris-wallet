package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.SyncStateEntity

@Dao
interface WriteSyncStateDao {

    @Query(
        """
        UPDATE sync_state SET changeLoggingEnabled = :enabled
        WHERE id = ${SyncStateEntity.SINGLETON_ID}
        """
    )
    suspend fun setChangeLoggingEnabled(enabled: Boolean)

    @Query(
        """
        UPDATE sync_state SET lastSyncedAt = :lastSyncedAt
        WHERE id = ${SyncStateEntity.SINGLETON_ID}
        """
    )
    suspend fun setLastSyncedAt(lastSyncedAt: Long)
}
