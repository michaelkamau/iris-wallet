package com.iris.data.repository

import com.iris.base.threading.DispatchersProvider
import com.iris.data.db.dao.read.FinancialSenderDao
import com.iris.data.db.dao.write.WriteFinancialSenderDao
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.mapper.FinancialSenderMapper
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialSenderRepository @Inject constructor(
    private val mapper: FinancialSenderMapper,
    private val dao: FinancialSenderDao,
    private val writeDao: WriteFinancialSenderDao,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun findAll(): List<FinancialSender> = withContext(dispatchersProvider.io) {
        dao.findAll().mapNotNull { with(mapper) { it.toDomain() }.getOrNull() }
    }

    /**
     * Returns null for an unknown *or* disabled sender, so "may I read this message?" is a single
     * question with a single answer (FR-003, FR-004).
     */
    suspend fun findEnabled(sender: SenderId): FinancialSender? =
        withContext(dispatchersProvider.io) {
            dao.findEnabledById(sender.value)
                ?.let { with(mapper) { it.toDomain() }.getOrNull() }
        }

    suspend fun save(value: FinancialSender): Unit = withContext(dispatchersProvider.io) {
        writeDao.save(with(mapper) { value.toEntity() })
    }

    suspend fun deleteById(id: SenderId): Unit = withContext(dispatchersProvider.io) {
        writeDao.deleteById(id.value)
    }
}
