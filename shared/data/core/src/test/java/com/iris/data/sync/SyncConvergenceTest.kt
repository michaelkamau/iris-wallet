package com.iris.data.sync

import com.iris.data.db.sync.SyncEntityType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * Simulates several devices editing the same data while going online and offline
 * in random order, and asserts that they all end up with the same content.
 *
 * This is the property that matters for cloud sync: whatever the interleaving of
 * edits and sync cycles, every device converges to the same state once they all
 * synced twice (once to publish their own changes, once to receive the others').
 */
class SyncConvergenceTest {

    @Test
    fun `devices converge on the same data`() = runTest {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val remote = FakeSyncRemoteDataSource()
            val devices = List(DEVICE_COUNT) { index -> device(index, remote) }

            repeat(ROUNDS) { round ->
                val device = devices.random(random)
                val accountId = accountId(random)
                when (random.nextInt(OPERATION_COUNT)) {
                    0 -> device.local.write(
                        type = SyncEntityType.ACCOUNT,
                        entityId = accountId,
                        payload = mapOf("id" to accountId, "name" to "round-$round"),
                        updatedAt = round.toLong()
                    )

                    1 -> device.local.delete(
                        type = SyncEntityType.ACCOUNT,
                        entityId = accountId,
                        updatedAt = round.toLong()
                    )

                    else -> device.engine.sync()
                }
            }

            // Two full rounds: publish everything, then receive everything.
            repeat(times = 2) { devices.forEach { it.engine.sync() } }

            devices.forEach { device ->
                device.local.rows shouldBe devices.first().local.rows
            }
        }
    }

    private fun device(index: Int, remote: FakeSyncRemoteDataSource): Device {
        val local = FakeSyncLocalDataSource(id = "device-$index")
        return Device(
            local = local,
            engine = SyncEngine(
                local = local,
                remote = remote,
                conflictResolver = ConflictResolver(),
                timeProvider = FixedTimeProvider(Instant.EPOCH)
            )
        )
    }

    private fun accountId(random: Random) = "account-${random.nextInt(ACCOUNT_COUNT)}"

    private class Device(val local: FakeSyncLocalDataSource, val engine: SyncEngine)

    private companion object {
        const val SEEDS = 25
        const val DEVICE_COUNT = 3
        const val ROUNDS = 60
        const val ACCOUNT_COUNT = 5
        const val OPERATION_COUNT = 3
    }
}
