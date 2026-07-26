package com.iris.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.entity.FinancialSenderEntity
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * `financial_senders` against real SQLite. The point of interest is `findEnabledById`: the
 * `enabled = 1` predicate lives in SQL, so a disabled sender is unreachable by any caller (FR-004).
 */
@RunWith(AndroidJUnit4::class)
class FinancialSenderDaoAndroidTest {

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
    fun insertsASenderAndReadsItBack(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow)

        // when
        val res = db.financialSenderDao.findAll()

        // then
        res shouldBe listOf(MpesaRow)
    }

    @Test
    fun findAllIsOrderedByDisplayName(): Unit = runBlocking {
        // given
        val dtb = MpesaRow.copy(displayName = "DTB", senderId = "DTB")
        val kcb = MpesaRow.copy(displayName = "KCB", senderId = "KCB")
        db.writeFinancialSenderDao.saveMany(listOf(kcb, MpesaRow, dtb))

        // when
        val res = db.financialSenderDao.findAll()

        // then
        res.map { it.displayName } shouldBe listOf("DTB", "KCB", "M-PESA")
    }

    @Test
    fun findEnabledByIdReturnsAnEnabledSender(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow)

        // when
        val res = db.financialSenderDao.findEnabledById("MPESA")

        // then
        res shouldBe MpesaRow
    }

    @Test
    fun findEnabledByIdIsNullForADisabledSender(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow.copy(enabled = false))

        // when
        val res = db.financialSenderDao.findEnabledById("MPESA")

        // then
        res shouldBe null
    }

    @Test
    fun findEnabledByIdIsNullForAnUnknownSender(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow)

        // when
        val res = db.financialSenderDao.findEnabledById("EQUITY")

        // then
        res shouldBe null
    }

    @Test
    fun savingTheSameSenderIdTwiceUpdatesTheSingleRow(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow)

        // when the user turns the sender off
        db.writeFinancialSenderDao.save(MpesaRow.copy(enabled = false))

        // then
        db.financialSenderDao.findAll() shouldBe listOf(MpesaRow.copy(enabled = false))
    }

    @Test
    fun deleteByIdRemovesOnlyThatSender(): Unit = runBlocking {
        // given
        val dtb = MpesaRow.copy(displayName = "DTB", senderId = "DTB")
        db.writeFinancialSenderDao.saveMany(listOf(MpesaRow, dtb))

        // when
        db.writeFinancialSenderDao.deleteById("MPESA")

        // then
        db.financialSenderDao.findAll() shouldBe listOf(dtb)
    }

    @Test
    fun deleteAllEmptiesTheTable(): Unit = runBlocking {
        // given
        db.writeFinancialSenderDao.save(MpesaRow)

        // when
        db.writeFinancialSenderDao.deleteAll()

        // then
        db.financialSenderDao.findAll().shouldBeEmpty()
    }

    companion object {
        private val MpesaRow = FinancialSenderEntity(
            displayName = "M-PESA",
            ruleSetId = "mpesa",
            accountId = UUID.randomUUID(),
            enabled = true,
            senderId = "MPESA",
        )
    }
}
