package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ProcessedMessageDao {
    /** `EXISTS` rather than a row read: the row's only purpose is to have been written. */
    @Query("SELECT EXISTS(SELECT 1 FROM processed_messages WHERE fingerprint = :fingerprint)")
    suspend fun exists(fingerprint: String): Boolean
}
