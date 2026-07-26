package com.iris.data.repository

import com.iris.base.threading.DispatchersProvider
import com.iris.data.db.dao.read.CounterpartyCategoryDao
import com.iris.data.db.dao.write.WriteCounterpartyCategoryDao
import com.iris.data.db.entity.CounterpartyCategoryEntity
import com.iris.data.model.CategoryId
import com.iris.data.model.sms.CounterpartyCategory
import com.iris.data.model.sms.CounterpartyKey
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The counterparty→category memory behind "the same shop gets the same category next time"
 * (FR-024).
 *
 * [remember] is an upsert, so the *latest* choice always wins — which is the whole point: the user
 * correcting a suggestion must change what is suggested next.
 */
@Singleton
class CounterpartyCategoryRepository @Inject constructor(
    private val dao: CounterpartyCategoryDao,
    private val writeDao: WriteCounterpartyCategoryDao,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun findCategory(key: CounterpartyKey): CategoryId? =
        withContext(dispatchersProvider.io) {
            dao.findByKey(key.value)?.let { CategoryId(it.categoryId) }
        }

    suspend fun remember(value: CounterpartyCategory): Unit =
        withContext(dispatchersProvider.io) {
            writeDao.save(
                CounterpartyCategoryEntity(
                    categoryId = value.category.value,
                    updatedAt = value.updatedAt,
                    counterpartyKey = value.counterparty.value,
                ),
            )
        }
}
