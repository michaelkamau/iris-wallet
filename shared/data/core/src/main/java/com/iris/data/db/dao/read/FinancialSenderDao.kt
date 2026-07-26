package com.iris.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.iris.data.db.entity.FinancialSenderEntity

@Dao
interface FinancialSenderDao {
    @Query("SELECT * FROM financial_senders ORDER BY displayName")
    suspend fun findAll(): List<FinancialSenderEntity>

    /**
     * The `enabled = 1` predicate lives in the query rather than in a caller's `filter`, so a
     * disabled sender cannot be captured from by any code path (FR-004).
     */
    @Query("SELECT * FROM financial_senders WHERE senderId = :senderId AND enabled = 1")
    suspend fun findEnabledById(senderId: String): FinancialSenderEntity?
}
