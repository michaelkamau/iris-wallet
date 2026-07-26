package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.iris.data.db.entity.ProcessedMessageEntity

@Dao
interface WriteProcessedMessageDao {
    @Upsert
    suspend fun save(value: ProcessedMessageEntity)

    @Upsert
    suspend fun saveMany(value: List<ProcessedMessageEntity>)

    @Query("DELETE FROM processed_messages WHERE fingerprint = :fingerprint")
    suspend fun deleteById(fingerprint: String)

    @Query("DELETE FROM processed_messages")
    suspend fun deleteAll()
}
