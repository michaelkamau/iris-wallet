package com.iris.data.sync

import com.iris.data.db.sync.SyncEntityType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.time.Instant

class SyncEngineTest {

    private val remote = FakeSyncRemoteDataSource()
    private val local = FakeSyncLocalDataSource(id = DEVICE)
    private val now = Instant.ofEpochMilli(NOW)

    private val engine = SyncEngine(
        local = local,
        remote = remote,
        conflictResolver = ConflictResolver(),
        timeProvider = FixedTimeProvider(now)
    )

    @Test
    fun `pushes local changes and remembers the last sync`() = runTest {
        local.write(SyncEntityType.ACCOUNT, "account-1", payload("Cash"), updatedAt = 10)

        val result = engine.sync()

        result shouldBe SyncResult.Success(pulled = 0, pushed = 1)
        remote.records.values.map { it.entityId } shouldContainExactly listOf("account-1")
        local.pendingChanges(limit = 10, maxAttempts = 5).shouldBeEmpty()
        local.lastSyncedAt shouldBe NOW
        engine.status.value shouldBe SyncStatus.Synced(NOW)
    }

    @Test
    fun `pulls remote changes and advances the cursor`() = runTest {
        remote.seed(record("accounts", "account-1", updatedAt = 7))

        val result = engine.sync()

        result shouldBe SyncResult.Success(pulled = 1, pushed = 0)
        local.rows.keys shouldContainExactly listOf("accounts/account-1")
        local.pullCursor() shouldBe 1
    }

    @Test
    fun `remote changes are not pushed back`() = runTest {
        remote.seed(record("accounts", "account-1", updatedAt = 7))

        engine.sync()

        remote.pushCalls shouldBe 0
    }

    @Test
    fun `remote deletion removes the local row`() = runTest {
        local.write(SyncEntityType.ACCOUNT, "account-1", payload("Cash"), updatedAt = 1)
        engine.sync()
        remote.seed(
            record(
                entityType = "accounts",
                entityId = "account-1",
                updatedAt = 20,
                deleted = true
            )
        )

        engine.sync()

        local.rows.keys.shouldBeEmpty()
    }

    @Test
    fun `local change newer than the remote one is kept`() = runTest {
        local.write(SyncEntityType.ACCOUNT, "account-1", payload("Local"), updatedAt = 30)
        remote.seed(record("accounts", "account-1", updatedAt = 20))

        engine.sync()

        local.rows.getValue("accounts/account-1")["name"] shouldBe "Local"
        remote.records.getValue("accounts/account-1").payload["name"] shouldBe "Local"
    }

    @Test
    fun `failed push is reported and retried later`() = runTest {
        local.write(SyncEntityType.ACCOUNT, "account-1", payload("Cash"), updatedAt = 10)
        remote.failNextPush = IOException("offline")

        val failure = engine.sync()

        failure.shouldBeInstanceOf<SyncResult.Failure>()
        engine.status.value.shouldBeInstanceOf<SyncStatus.Failed>()
        local.pendingChanges(limit = 10, maxAttempts = 5).size shouldBe 1

        engine.sync() shouldBe SyncResult.Success(pulled = 0, pushed = 1)
    }

    @Test
    fun `records that keep failing are quarantined`() = runTest {
        local.write(SyncEntityType.ACCOUNT, "account-1", payload("Cash"), updatedAt = 10)

        repeat(times = 5) {
            remote.failNextPush = IOException("offline")
            engine.sync()
        }

        local.pendingChanges(limit = 10, maxAttempts = 5).shouldBeEmpty()
    }

    @Test
    fun `referenced rows are applied before the rows referencing them`() = runTest {
        remote.seed(record("transactions", "transaction-1", updatedAt = 5))
        remote.seed(record("accounts", "account-1", updatedAt = 6))

        engine.sync()

        local.appliedOrder shouldContainExactly listOf(
            "accounts/account-1",
            "transactions/transaction-1"
        )
    }

    private fun payload(name: String) = mapOf("id" to "account-1", "name" to name)

    private fun record(
        entityType: String,
        entityId: String,
        updatedAt: Long,
        deleted: Boolean = false
    ) = SyncRecord(
        entityType = entityType,
        entityId = entityId,
        updatedAt = updatedAt,
        deleted = deleted,
        deviceId = "other-device",
        payload = if (deleted) emptyMap() else mapOf("id" to entityId, "name" to "Remote")
    )

    private companion object {
        const val DEVICE = "device-a"
        const val NOW = 1_000L
    }
}
