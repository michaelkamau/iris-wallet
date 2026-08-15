package com.iris.data.sync

/**
 * Remote storage of [SyncRecord]s of a single user.
 *
 * Implemented by the cloud backend (Firestore) and by fakes in tests. No backend
 * type may leak through this interface, so that the sync engine stays testable
 * and buildable without any cloud dependency.
 */
interface SyncRemoteDataSource {

    /**
     * Records stored after [sinceCursor], oldest first, at most [limit] of them.
     *
     * Paging uses a cursor assigned by the remote when a record is stored, not the
     * `updatedAt` of the record itself: a device that was offline for a while
     * uploads old changes, which a cursor based on device clocks would skip.
     */
    suspend fun fetchChanges(sinceCursor: Long, limit: Int): SyncPage

    /** Uploads [records], overwriting any remote record with the same identity. */
    suspend fun push(records: List<SyncRecord>)
}

/** A page of remote records, together with the cursor to continue from. */
data class SyncPage(
    val records: List<SyncRecord>,
    /** Cursor of the last record of the page, to be passed to the next fetch. */
    val cursor: Long
)
