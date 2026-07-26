package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.CounterpartyCategoryEntity

@Dao
interface CounterpartyCategoryDao {
    @Query("SELECT * FROM counterparty_categories WHERE counterpartyKey = :key")
    suspend fun findByKey(key: String): CounterpartyCategoryEntity?
}
