package com.iris.data.repository

import androidx.room.withTransaction
import com.iris.base.TestDispatchersProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.dao.read.CapturedTransactionDao
import com.iris.data.db.dao.write.WriteCapturedTransactionDao
import com.iris.data.db.entity.CapturedTransactionEntity
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.mapper.CapturedTransactionMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CapturedTransactionRepositoryTest {
    private val dao = mockk<CapturedTransactionDao>()
    private val writeDao = mockk<WriteCapturedTransactionDao>()
    private val db = mockk<IrisRoomDatabase>()

    /** Stands in for the table, so a rolled-back transaction is observable. */
    private val rows = mutableListOf<CapturedTransactionEntity>()

    private lateinit var repository: CapturedTransactionRepository

    @Before
    fun setup() {
        rows.clear()
        mockkStatic(ROOM_DATABASE_KT)
        rollBackOnFailure()
        repository = CapturedTransactionRepository(
            mapper = CapturedTransactionMapper(),
            dao = dao,
            writeDao = writeDao,
            db = db,
            dispatchersProvider = TestDispatchersProvider,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_KT)
    }

    @Test
    fun `find all pending - pairs each principal with the fee from the same message`() = runTest {
        // given
        coEvery { dao.findAll() } returns listOf(PrincipalRow, FeeRow)

        // when
        val res = repository.findAllPending()

        // then
        res shouldBe listOf(CapturedEntry(principal = principal(), fee = fee()))
    }

    @Test
    fun `find all pending - a fee with no principal heads its own entry`() = runTest {
        // given a charge advice that reported nothing else (FR-020), and an unrelated principal
        coEvery { dao.findAll() } returns listOf(
            PrincipalRow,
            FeeRow.copy(parentId = null, id = STANDALONE_FEE_ID.value),
        )

        // when
        val res = repository.findAllPending()

        // then it is reviewable rather than invisible — it is the whole of its message
        res shouldBe listOf(
            CapturedEntry(principal = principal(), fee = null),
            CapturedEntry(principal = standaloneFee(), fee = null),
        )
    }

    @Test
    fun `find all pending - a fee that belongs to a principal is never listed twice`() = runTest {
        // given
        coEvery { dao.findAll() } returns listOf(PrincipalRow, FeeRow)

        // when
        val res = repository.findAllPending()

        // then the charge appears once, inside the entry it belongs to
        res.size shouldBe 1
        res.single().fee?.id shouldBe FEE_ID
    }

    @Test
    fun `find all pending - a fee orphaned by a missing principal is still reviewable`() = runTest {
        // given a fee whose parent row is gone, which no delete path should ever leave behind
        coEvery { dao.findAll() } returns listOf(FeeRow)

        // when
        val res = repository.findAllPending()

        // then it surfaces rather than being silently swallowed, so the user can deal with it
        res.size shouldBe 1
        res.single().principal.id shouldBe FEE_ID
    }

    @Test
    fun `find all pending - drops a row that cannot be mapped`() = runTest {
        // given a principal whose stored amount is not a value the domain admits
        coEvery { dao.findAll() } returns listOf(PrincipalRow.copy(amount = 0.0))

        // when
        val res = repository.findAllPending()

        // then it is skipped rather than crashing the whole inbox
        res.shouldBeEmpty()
    }

    @Test
    fun `find all pending - an unmappable principal does not take its fee down with it`() =
        runTest {
            // given
            coEvery { dao.findAll() } returns listOf(PrincipalRow.copy(amount = 0.0), FeeRow)

            // when
            val res = repository.findAllPending()

            // then the charge — which is perfectly good data about the user's money — survives
            res.size shouldBe 1
            res.single().principal.id shouldBe FEE_ID
        }

    @Test
    fun `find all pending - no captured rows`() = runTest {
        // given
        coEvery { dao.findAll() } returns emptyList()

        // when
        val res = repository.findAllPending()

        // then
        res.shouldBeEmpty()
    }

    @Test
    fun `find by id - returns the principal together with its fee`() = runTest {
        // given
        coEvery { dao.findById(PRINCIPAL_ID.value) } returns PrincipalRow
        coEvery { dao.findByParentId(PRINCIPAL_ID.value) } returns listOf(FeeRow)

        // when
        val res = repository.findById(PRINCIPAL_ID)

        // then
        res shouldBe CapturedEntry(principal = principal(), fee = fee())
    }

    @Test
    fun `find by id - null when there is no such row`() = runTest {
        // given
        coEvery { dao.findById(PRINCIPAL_ID.value) } returns null

        // when
        val res = repository.findById(PRINCIPAL_ID)

        // then
        res shouldBe null
    }

    @Test
    fun `find by id - null when the principal row cannot be mapped`() = runTest {
        // given
        coEvery { dao.findById(PRINCIPAL_ID.value) } returns PrincipalRow.copy(direction = "UP")

        // when
        val res = repository.findById(PRINCIPAL_ID)

        // then
        res shouldBe null
    }

    @Test
    fun `pending count - comes straight from the dao`() = runTest {
        // given
        coEvery { dao.pendingCount() } returns flowOf(3)

        // when
        val res = repository.pendingCount().first()

        // then
        res shouldBe 3
    }

    @Test
    fun `save - translates the domain enums to their column values`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.save(principal())

        // then
        coVerify(exactly = 1) { writeDao.save(PrincipalRow) }
    }

    @Test
    fun `save entry - writes the principal and the fee in one transaction`() = runTest {
        // given
        writesLandInRows()

        // when
        repository.saveEntry(CapturedEntry(principal = principal(), fee = fee()))

        // then
        rows shouldBe listOf(PrincipalRow, FeeRow)
        coVerify(exactly = 1) { db.withTransaction(any<suspend () -> Unit>()) }
    }

    @Test
    fun `save entry - a failed write leaves nothing behind`() = runTest {
        // given the write half-applies and then fails
        coEvery { writeDao.saveMany(any()) } answers {
            rows.addAll(firstArg<List<CapturedTransactionEntity>>())
            error("disk full")
        }

        // when
        val res = runCatching {
            repository.saveEntry(CapturedEntry(principal = principal(), fee = fee()))
        }

        // then
        res.isFailure shouldBe true
        rows.shouldBeEmpty()
        coVerify(exactly = 1) { writeDao.saveMany(any()) }
    }

    @Test
    fun `delete entry - deletes the fee and the principal in one transaction`() = runTest {
        // given
        rows.addAll(listOf(PrincipalRow, FeeRow))
        writesLandInRows()

        // when
        repository.deleteEntry(PRINCIPAL_ID)

        // then
        rows.shouldBeEmpty()
        coVerify(ordering = Ordering.SEQUENCE) {
            writeDao.deleteByParentId(PRINCIPAL_ID.value)
            writeDao.deleteById(PRINCIPAL_ID.value)
        }
    }

    @Test
    fun `delete entry - a failed delete leaves the fee behind`() = runTest {
        // given the fee is deleted but the principal delete fails
        rows.addAll(listOf(PrincipalRow, FeeRow))
        writesLandInRows()
        coEvery { writeDao.deleteById(any()) } throws IllegalStateException("db is locked")

        // when
        val res = runCatching { repository.deleteEntry(PRINCIPAL_ID) }

        // then
        res.isFailure shouldBe true
        rows shouldBe listOf(PrincipalRow, FeeRow)
        coVerify(exactly = 1) { writeDao.deleteByParentId(PRINCIPAL_ID.value) }
    }

    @Test
    fun `delete all`() = runTest {
        // given
        coEvery { writeDao.deleteAll() } just runs

        // when
        repository.deleteAll()

        // then
        coVerify(exactly = 1) { writeDao.deleteAll() }
    }

    /** Makes the mocked `withTransaction` commit on success and restore [rows] on failure. */
    private fun rollBackOnFailure() {
        coEvery { db.withTransaction(any<suspend () -> Unit>()) } coAnswers {
            val snapshot = rows.toList()
            // The receiver is arg 0 for a statically mocked extension function; the block is arg 1.
            val block = secondArg<suspend () -> Unit>()
            try {
                block()
            } catch (failure: Throwable) {
                rows.clear()
                rows.addAll(snapshot)
                throw failure
            }
        }
    }

    private fun writesLandInRows() {
        coEvery { writeDao.saveMany(any()) } answers {
            rows.addAll(firstArg<List<CapturedTransactionEntity>>())
            Unit
        }
        coEvery { writeDao.deleteById(any()) } answers {
            val id = firstArg<UUID>()
            rows.removeAll { row -> row.id == id }
            Unit
        }
        coEvery { writeDao.deleteByParentId(any()) } answers {
            val parentId = firstArg<UUID>()
            rows.removeAll { row -> row.parentId == parentId }
            Unit
        }
    }

    companion object {
        private const val ROOM_DATABASE_KT = "androidx.room.RoomDatabaseKt"

        private val PRINCIPAL_ID = CapturedTransactionId(UUID.randomUUID())
        private val FEE_ID = CapturedTransactionId(UUID.randomUUID())
        private val STANDALONE_FEE_ID = CapturedTransactionId(UUID.randomUUID())
        private val TIME: Instant = Instant.parse("2026-07-25T16:50:00Z")
        private val CAPTURED_AT: Instant = Instant.parse("2026-07-25T16:51:00Z")

        private val PrincipalRow = CapturedTransactionEntity(
            senderId = "MPESA",
            kind = "PRINCIPAL",
            direction = "MONEY_OUT",
            parentId = null,
            taxAmount = null,
            amount = 1350.0,
            assetCode = "KES",
            dateTime = TIME,
            counterparty = null,
            reference = null,
            accountId = null,
            categoryId = null,
            description = null,
            duplicateOfTransactionId = null,
            capturedAt = CAPTURED_AT,
            origin = "LIVE_BROADCAST",
            id = PRINCIPAL_ID.value,
        )

        private val FeeRow = PrincipalRow.copy(
            kind = "FEE",
            direction = null,
            parentId = PRINCIPAL_ID.value,
            amount = 23.0,
            id = FEE_ID.value,
        )

        /** The domain form of [PrincipalRow], written out rather than mapped, to stay honest. */
        private fun principal() = CapturedTransaction(
            id = PRINCIPAL_ID,
            sender = SenderId.unsafe("MPESA"),
            kind = CapturedKind.Principal(MoneyDirection.MoneyOut),
            amount = PositiveDouble.unsafe(1350.0),
            asset = AssetCode.unsafe("KES"),
            time = TIME,
            counterparty = null,
            reference = null,
            account = null,
            category = null,
            description = null,
            duplicateOf = null,
            capturedAt = CAPTURED_AT,
            origin = CaptureOrigin.LiveBroadcast,
        )

        /** The same charge with nothing to attach to: the whole of a charge-advice message. */
        private fun standaloneFee() = fee().copy(
            id = STANDALONE_FEE_ID,
            kind = CapturedKind.Fee(parent = null, tax = null),
        )

        /** The domain form of [FeeRow]. */
        private fun fee() = principal().copy(
            id = FEE_ID,
            kind = CapturedKind.Fee(parent = PRINCIPAL_ID, tax = null),
            amount = PositiveDouble.unsafe(23.0),
        )
    }
}
