package com.iris.data.repository

import com.iris.base.threading.DispatchersProvider
import com.iris.data.db.dao.read.ProcessedMessageDao
import com.iris.data.db.dao.write.WriteProcessedMessageDao
import com.iris.data.db.entity.ProcessedMessageEntity
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The record of what has already been handled. It is what makes re-delivery and historical import
 * idempotent (FR-028) and what stops a dismissed message coming back (FR-025).
 *
 * The outcome is stored as plain `TEXT` and translated here rather than through a Room type
 * converter, so the migration DDL reads exactly as written; the mapping is small enough that a
 * dedicated mapper class would only hide it.
 */
@Singleton
class ProcessedMessageRepository @Inject constructor(
    private val dao: ProcessedMessageDao,
    private val writeDao: WriteProcessedMessageDao,
    private val dispatchersProvider: DispatchersProvider,
) {

    suspend fun isProcessed(fingerprint: MessageFingerprint): Boolean =
        withContext(dispatchersProvider.io) { dao.exists(fingerprint.value) }

    suspend fun record(value: ProcessedMessage): Unit = withContext(dispatchersProvider.io) {
        writeDao.save(
            ProcessedMessageEntity(
                senderId = value.sender.value,
                outcome = value.outcome.column(),
                processedAt = value.processedAt,
                fingerprint = value.fingerprint.value,
            ),
        )
    }

    private fun ProcessedOutcome.column(): String = when (this) {
        ProcessedOutcome.Captured -> CAPTURED
        ProcessedOutcome.Ignored -> IGNORED
        ProcessedOutcome.Dismissed -> DISMISSED
    }

    companion object {
        const val CAPTURED = "CAPTURED"
        const val IGNORED = "IGNORED"
        const val DISMISSED = "DISMISSED"
    }
}
