package com.iris.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.entity.CapturedTransactionEntity
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * `captured_transactions` against real SQLite: the `@Upsert` semantics the repository relies on,
 * and the deliberate absence of foreign keys (persistence-contract.md §1) which is why deleting an
 * entry has to delete the fee explicitly.
 */
@RunWith(AndroidJUnit4::class)
class CapturedTransactionDaoAndroidTest {

    private lateinit var db: IrisRoomDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IrisRoomDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertsAPrincipalAndReadsItBack(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.save(PrincipalRow)

        // when
        val res = db.capturedTransactionDao.findById(PRINCIPAL_ID)

        // then
        res shouldBe PrincipalRow
    }

    @Test
    fun findAllReturnsTheNewestFirst(): Unit = runBlocking {
        // given
        val older = PrincipalRow.copy(
            dateTime = TIME.minusSeconds(3600),
            id = UUID.randomUUID(),
        )
        db.writeCapturedTransactionDao.saveMany(listOf(older, PrincipalRow))

        // when
        val res = db.capturedTransactionDao.findAll()

        // then
        res.map { it.id } shouldBe listOf(PRINCIPAL_ID, older.id)
    }

    @Test
    fun findsAFeeByItsParent(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.saveMany(listOf(PrincipalRow, FeeRow, StandaloneFeeRow))

        // when
        val res = db.capturedTransactionDao.findByParentId(PRINCIPAL_ID)

        // then
        res shouldBe listOf(FeeRow)
    }

    @Test
    fun savingTheSameIdTwiceUpdatesTheSingleRow(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.save(PrincipalRow)

        // when
        db.writeCapturedTransactionDao.save(PrincipalRow.copy(amount = 99.0))

        // then
        db.capturedTransactionDao.findAll().size shouldBe 1
        db.capturedTransactionDao.findById(PRINCIPAL_ID)?.amount shouldBe 99.0
    }

    @Test
    fun pendingCountCountsPrincipalsOnly(): Unit = runBlocking {
        // given a principal with its fee, plus a second principal
        val second = PrincipalRow.copy(id = UUID.randomUUID())
        db.writeCapturedTransactionDao.saveMany(listOf(PrincipalRow, FeeRow, second))

        // when
        val res = db.capturedTransactionDao.pendingCount().first()

        // then
        res shouldBe 2
    }

    @Test
    fun pendingCountIsZeroWhenNothingIsCaptured(): Unit = runBlocking {
        // when
        val res = db.capturedTransactionDao.pendingCount().first()

        // then
        res shouldBe 0
    }

    @Test
    fun acceptsAFeeWhoseParentRowDoesNotExist(): Unit = runBlocking {
        // given no principal is inserted — there are deliberately no foreign keys
        db.writeCapturedTransactionDao.save(FeeRow)

        // when
        val res = db.capturedTransactionDao.findByParentId(PRINCIPAL_ID)

        // then
        res shouldBe listOf(FeeRow)
    }

    @Test
    fun deletingAPrincipalAloneLeavesItsFeeBehind(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.saveMany(listOf(PrincipalRow, FeeRow))

        // when
        db.writeCapturedTransactionDao.deleteById(PRINCIPAL_ID)

        // then there is no cascade in SQLite; the repository has to do it
        db.capturedTransactionDao.findAll() shouldBe listOf(FeeRow)
    }

    @Test
    fun deleteByParentIdRemovesTheFeeAndKeepsThePrincipal(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.saveMany(listOf(PrincipalRow, FeeRow, StandaloneFeeRow))

        // when
        db.writeCapturedTransactionDao.deleteByParentId(PRINCIPAL_ID)

        // then
        db.capturedTransactionDao.findAll().map { it.id } shouldBe
            listOf(PRINCIPAL_ID, StandaloneFeeRow.id)
    }

    @Test
    fun deleteAllEmptiesTheTable(): Unit = runBlocking {
        // given
        db.writeCapturedTransactionDao.saveMany(listOf(PrincipalRow, FeeRow))

        // when
        db.writeCapturedTransactionDao.deleteAll()

        // then
        db.capturedTransactionDao.findAll().shouldBeEmpty()
    }

    companion object {
        private val PRINCIPAL_ID: UUID = UUID.randomUUID()
        private val TIME: Instant = Instant.parse("2026-07-25T16:50:00Z")

        private val PrincipalRow = CapturedTransactionEntity(
            senderId = "MPESA",
            kind = "PRINCIPAL",
            direction = "MONEY_OUT",
            parentId = null,
            taxAmount = null,
            amount = 1350.0,
            assetCode = "KES",
            dateTime = TIME,
            counterparty = "James Mwangi",
            reference = "UGP7B0ITE4",
            accountId = null,
            categoryId = null,
            description = null,
            duplicateOfTransactionId = null,
            capturedAt = TIME,
            origin = "LIVE_BROADCAST",
            id = PRINCIPAL_ID,
        )

        private val FeeRow = PrincipalRow.copy(
            kind = "FEE",
            direction = null,
            parentId = PRINCIPAL_ID,
            taxAmount = 3.0,
            amount = 23.0,
            id = UUID.randomUUID(),
        )

        /** A cost reported without its principal (FR-020). */
        private val StandaloneFeeRow = FeeRow.copy(
            parentId = null,
            dateTime = TIME.minusSeconds(60),
            id = UUID.randomUUID(),
        )
    }
}
