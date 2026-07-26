package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.iris.data.db.entity.CounterpartyCategoryEntity

@Dao
interface WriteCounterpartyCategoryDao {
    @Upsert
    suspend fun save(value: CounterpartyCategoryEntity)

    @Upsert
    suspend fun saveMany(value: List<CounterpartyCategoryEntity>)

    @Query("DELETE FROM counterparty_categories WHERE counterpartyKey = :key")
    suspend fun deleteById(key: String)

    @Query("DELETE FROM counterparty_categories")
    suspend fun deleteAll()
}
