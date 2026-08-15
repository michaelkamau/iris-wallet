package com.iris.data.sync

/**
 * In memory [SyncRemoteDataSource] holding one record per identity, like the
 * remote record collection does, and assigning a monotonic cursor on every write.
 */
class FakeSyncRemoteDataSource : SyncRemoteDataSource {

    private val stored = mutableMapOf<String, StoredRecord>()
    private var sequence = 0L

    var failNextPush: Exception? = null
    var pushCalls = 0
        private set

    /** Records currently stored, keyed by `entityType/entityId`. */
    val records: Map<String, SyncRecord> get() = stored.mapValues { it.value.record }

    override suspend fun fetchChanges(sinceCursor: Long, limit: Int): SyncPage {
        val page = stored.values
            .filter { it.cursor > sinceCursor }
            .sortedBy { it.cursor }
            .take(limit)
        return SyncPage(
            records = page.map { it.record },
            cursor = page.lastOrNull()?.cursor ?: sinceCursor
        )
    }

    /** Stores [record] as if another device had pushed it. */
    fun seed(record: SyncRecord) {
        sequence++
        stored["${record.entityType}/${record.entityId}"] =
            StoredRecord(record = record, cursor = sequence)
    }

    override suspend fun push(records: List<SyncRecord>) {
        pushCalls++
        failNextPush?.let { failure ->
            failNextPush = null
            throw failure
        }
        records.forEach { record ->
            val key = "${record.entityType}/${record.entityId}"
            val known = stored[key]?.record
            if (known == null || known.updatedAt <= record.updatedAt) {
                sequence++
                stored[key] = StoredRecord(record = record, cursor = sequence)
            }
        }
    }

    private data class StoredRecord(val record: SyncRecord, val cursor: Long)
}
