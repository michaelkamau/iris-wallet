package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE id = ${SyncStateEntity.SINGLETON_ID}")
    suspend fun find(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = ${SyncStateEntity.SINGLETON_ID}")
    fun state(): Flow<SyncStateEntity?>
}
