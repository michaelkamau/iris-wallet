package com.iris.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.iris.data.db.entity.FinancialSenderEntity

@Dao
interface WriteFinancialSenderDao {
    @Upsert
    suspend fun save(value: FinancialSenderEntity)

    @Upsert
    suspend fun saveMany(value: List<FinancialSenderEntity>)

    @Query("DELETE FROM financial_senders WHERE senderId = :senderId")
    suspend fun deleteById(senderId: String)

    @Query("DELETE FROM financial_senders")
    suspend fun deleteAll()
}
