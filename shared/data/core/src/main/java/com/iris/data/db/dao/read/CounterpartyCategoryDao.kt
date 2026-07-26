package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.CounterpartyCategoryEntity

@Dao
interface CounterpartyCategoryDao {
    @Query("SELECT * FROM counterparty_categories WHERE counterpartyKey = :key")
    suspend fun findByKey(key: String): CounterpartyCategoryEntity?

    /**
     * The whole memory.
     *
     * Nothing in the app renders this — the suggestion is always a single-key lookup. It exists
     * so that "the user's correction *replaced* what we remembered" can be asserted against the
     * table itself rather than inferred from the answer a query happened to return.
     */
    @Query("SELECT * FROM counterparty_categories")
    suspend fun findAll(): List<CounterpartyCategoryEntity>
}
