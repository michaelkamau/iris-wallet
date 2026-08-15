package com.iris.data.sync

import com.iris.data.db.entity.SyncChangeLogEntity
import com.iris.data.db.sync.SyncOperation
import io.kotest.matchers.shouldBe
import org.junit.Test

class ConflictResolverTest {

    private val resolver = ConflictResolver()

    @Test
    fun `remote record unknown locally is applied`() {
        resolver.shouldApplyRemote(remote(updatedAt = 1), local = null) shouldBe true
    }

    @Test
    fun `newer remote wins`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 2),
            local = local(updatedAt = 1)
        ) shouldBe true
    }

    @Test
    fun `newer local wins`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1),
            local = local(updatedAt = 2)
        ) shouldBe false
    }

    @Test
    fun `newer deletion wins over local modification`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 2, deleted = true),
            local = local(updatedAt = 1)
        ) shouldBe true
    }

    @Test
    fun `newer modification resurrects a deleted record`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 2),
            local = local(updatedAt = 1, operation = SyncOperation.DELETE)
        ) shouldBe true
    }

    @Test
    fun `deletion wins over modification of the same instant`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1, deleted = true),
            local = local(updatedAt = 1)
        ) shouldBe true

        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1),
            local = local(updatedAt = 1, operation = SyncOperation.DELETE)
        ) shouldBe false
    }

    @Test
    fun `identical timestamps are broken by device id`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1, deviceId = "device-b"),
            local = local(updatedAt = 1, deviceId = "device-a")
        ) shouldBe true

        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1, deviceId = "device-a"),
            local = local(updatedAt = 1, deviceId = "device-b")
        ) shouldBe false
    }

    @Test
    fun `own change echoed back by the remote is not reapplied`() {
        resolver.shouldApplyRemote(
            remote = remote(updatedAt = 1, deviceId = "device-a"),
            local = local(updatedAt = 1, deviceId = "device-a")
        ) shouldBe false
    }

    private fun remote(
        updatedAt: Long,
        deleted: Boolean = false,
        deviceId: String = "device-b"
    ) = SyncRecord(
        entityType = "transactions",
        entityId = "id",
        updatedAt = updatedAt,
        deleted = deleted,
        deviceId = deviceId
    )

    private fun local(
        updatedAt: Long,
        operation: SyncOperation = SyncOperation.UPSERT,
        deviceId: String = "device-a"
    ) = SyncChangeLogEntity(
        entityType = "transactions",
        entityId = "id",
        operation = operation,
        updatedAt = updatedAt,
        deviceId = deviceId
    )
}
