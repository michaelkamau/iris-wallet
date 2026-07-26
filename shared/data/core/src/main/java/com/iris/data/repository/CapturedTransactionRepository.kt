package com.iris.data.repository

import androidx.room.withTransaction
import com.iris.base.threading.DispatchersProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.dao.read.CapturedTransactionDao
import com.iris.data.db.dao.write.WriteCapturedTransactionDao
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.repository.mapper.CapturedTransactionMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only door to `captured_transactions`.
 *
 * Deliberately **not** memoized like [CategoryRepository]: captured rows change from outside the
 * UI (a broadcast receiver, an import worker), so a cache here would show a stale review list.
 *
 * Rows that fail mapping are dropped with `getOrNull()` rather than propagated — a single corrupt
 * captured row must never stop the user reviewing the rest.
 */
@Singleton
class CapturedTransactionRepository @Inject constructor(
    private val mapper: CapturedTransactionMapper,
    private val dao: CapturedTransactionDao,
    private val writeDao: WriteCapturedTransactionDao,
    private val db: IrisRoomDatabase,
    private val dispatchersProvider: DispatchersProvider,
) {

    /**
     * Principals newest first, each with the fee captured from the same message.
     *
     * A fee that belongs to a principal is never listed on its own — it is reviewed as part of the
     * entry it belongs to. A fee with **no** parent is a different thing entirely: it is the whole
     * of its message, so it heads its own entry and stays confirmable (FR-020).
     */
    suspend fun findAllPending(): List<CapturedEntry> = withContext(dispatchersProvider.io) {
        val all = dao.findAll().mapNotNull { with(mapper) { it.toDomain() }.getOrNull() }
        val principals = all.mapTo(mutableSetOf(), CapturedTransaction::id)
        val feesByParent = all
            .mapNotNull { row -> (row.kind as? CapturedKind.Fee)?.parent?.let { it to row } }
            .groupBy({ it.first }, { it.second })

        all.mapNotNull { row ->
            when (val kind = row.kind) {
                is CapturedKind.Principal ->
                    CapturedEntry(principal = row, fee = feesByParent[row.id]?.firstOrNull())

                is CapturedKind.Fee ->
                    // A fee whose parent is still here is reviewed as part of that entry, so it is
                    // skipped. Anything else — never had a parent, or lost one — is the whole of
                    // what the user can see of its message, so it heads its own entry (FR-020).
                    // The alternative is a row that exists but can never be reviewed or dismissed.
                    if (kind.parent in principals) null else CapturedEntry(row, fee = null)
            }
        }
    }

    suspend fun findById(id: CapturedTransactionId): CapturedEntry? =
        withContext(dispatchersProvider.io) {
            val principal = dao.findById(id.value)
                ?.let { with(mapper) { it.toDomain() }.getOrNull() }
                ?: return@withContext null

            CapturedEntry(
                principal = principal,
                fee = dao.findByParentId(id.value)
                    .firstNotNullOfOrNull { with(mapper) { it.toDomain() }.getOrNull() },
            )
        }

    /** Cold flow straight off Room, so the review badge follows the table with no invalidation. */
    fun pendingCount(): Flow<Int> = dao.pendingCount()

    suspend fun save(value: CapturedTransaction): Unit = withContext(dispatchersProvider.io) {
        writeDao.save(with(mapper) { value.toEntity() })
    }

    /**
     * One transaction, so a fee can never exist without the principal it points at — which is the
     * state `findAllPending` has no way to represent.
     */
    suspend fun saveEntry(entry: CapturedEntry): Unit = withContext(dispatchersProvider.io) {
        db.withTransaction {
            writeDao.saveMany(
                listOfNotNull(entry.principal, entry.fee).map {
                    with(mapper) { it.toEntity() }
                },
            )
        }
    }

    /** Deletes the principal and its fee together (FR-018). */
    suspend fun deleteEntry(id: CapturedTransactionId): Unit =
        withContext(dispatchersProvider.io) {
            db.withTransaction {
                writeDao.deleteByParentId(id.value)
                writeDao.deleteById(id.value)
            }
        }

    /**
     * Deletes one row and nothing else, so a user who discards a payment but chooses to keep its
     * charge keeps a charge that is still reviewable (FR-018).
     *
     * Callers are responsible for detaching a fee first — a row left pointing at a deleted parent
     * is a state [findAllPending] would never show.
     */
    suspend fun deleteById(id: CapturedTransactionId): Unit =
        withContext(dispatchersProvider.io) {
            writeDao.deleteById(id.value)
        }

    suspend fun deleteAll(): Unit = withContext(dispatchersProvider.io) {
        writeDao.deleteAll()
    }
}
