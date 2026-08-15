package com.iris.data.sync

import com.iris.base.time.TimeProvider
import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.sync.SyncEntityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs sync cycles: applies remote changes locally, then uploads local ones.
 *
 * A cycle is idempotent and resumable. The pull cursor is only advanced after a
 * page was fully applied, and pushed changes are only marked as synced after the
 * remote accepted them, so an interrupted cycle is repeated rather than lost.
 *
 * The engine is transport agnostic, [SyncRemoteDataSource] is the only seam
 * towards the cloud backend.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val local: SyncLocalDataSource,
    private val remote: SyncRemoteDataSource,
    private val conflictResolver: ConflictResolver,
    private val timeProvider: TimeProvider
) {

    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)

    /** Current state of the engine. */
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /**
     * Runs a single sync cycle.
     *
     * Concurrent calls do not queue up, they return [SyncResult.AlreadyRunning].
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun sync(): SyncResult {
        if (!mutex.tryLock()) return SyncResult.AlreadyRunning
        return try {
            _status.value = SyncStatus.Syncing
            val pulled = pull()
            val pushed = push()
            val now = timeProvider.utcNow().toEpochMilli()
            local.setLastSyncedAt(now)
            _status.value = SyncStatus.Synced(now)
            SyncResult.Success(pulled = pulled, pushed = pushed)
        } catch (cancellation: CancellationException) {
            _status.value = SyncStatus.Idle
            throw cancellation
        } catch (error: Exception) {
            val reason = error.message ?: error::class.java.simpleName
            _status.value = SyncStatus.Failed(reason)
            SyncResult.Failure(reason)
        } finally {
            mutex.unlock()
        }
    }

    /** Applies every remote change stored after the pull cursor, page by page. */
    private suspend fun pull(): Int {
        var cursor = local.pullCursor()
        var applied = 0
        while (true) {
            val page = remote.fetchChanges(sinceCursor = cursor, limit = PAGE_SIZE)
            if (page.records.isEmpty()) break

            applied += apply(page.records)

            // Guards against a remote that does not advance its cursor, which would
            // otherwise make this loop fetch the same page forever.
            if (page.cursor <= cursor) break
            cursor = page.cursor
            local.setPullCursor(cursor)

            if (page.records.size < PAGE_SIZE) break
        }
        return applied
    }

    /**
     * Applies [page] in dependency order: rows are created before the rows
     * referencing them, and deleted in the opposite order.
     */
    private suspend fun apply(page: List<SyncRecord>): Int {
        val known = page.filter { it.type != null }
        val ordered = known.filterNot { it.deleted }.sortedBy { it.type?.dependencyOrder } +
            known.filter { it.deleted }.sortedByDescending { it.type?.dependencyOrder }

        var applied = 0
        ordered.forEach { record ->
            val localChange = local.localChange(record.entityType, record.entityId)
            if (conflictResolver.shouldApplyRemote(record, localChange)) {
                local.applyRemote(record)
                applied++
            }
        }
        return applied
    }

    /** Uploads every pending local change, in batches. */
    private suspend fun push(): Int {
        var pushed = 0
        while (true) {
            val pending = local.pendingChanges(limit = BATCH_SIZE, maxAttempts = MAX_ATTEMPTS)
            if (pending.isEmpty()) break

            pushed += push(pending)

            if (pending.size < BATCH_SIZE) break
        }
        return pushed
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun push(pending: List<SyncChangeLogEntity>): Int {
        val records = pending.mapNotNull { local.readRecord(it) }
        if (records.isEmpty()) {
            // Nothing readable in this batch, count the attempt so it cannot loop forever.
            pending.forEach { local.recordFailure(it.entityType, it.entityId, MISSING_ROW_ERROR) }
            return 0
        }

        try {
            remote.push(records)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val message = error.message ?: error::class.java.simpleName
            pending.forEach { local.recordFailure(it.entityType, it.entityId, message) }
            throw error
        }

        records.forEach { local.markSynced(it) }
        return records.size
    }

    private companion object {
        /** Records fetched per pull request, kept well below the Firestore batch limits. */
        const val PAGE_SIZE = 200

        /** Records uploaded per batch, the Firestore limit is 500 operations. */
        const val BATCH_SIZE = 200

        /** Push attempts after which a record is quarantined so it cannot stall the queue. */
        const val MAX_ATTEMPTS = 5

        const val MISSING_ROW_ERROR = "Row no longer exists"
    }
}
