package com.iris.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.entity.CounterpartyCategoryEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * `counterparty_categories` against real SQLite — the memory behind "the same shop gets the same
 * category next time" (FR-024), where the latest choice must win.
 */
@RunWith(AndroidJUnit4::class)
class CounterpartyCategoryDaoAndroidTest {

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
    fun insertsAChoiceAndReadsItBackByKey(): Unit = runBlocking {
        // given
        db.writeCounterpartyCategoryDao.save(GroceriesRow)

        // when
        val res = db.counterpartyCategoryDao.findByKey(KEY)

        // then
        res shouldBe GroceriesRow
    }

    @Test
    fun findByKeyIsNullForACounterpartyThatWasNeverCategorised(): Unit = runBlocking {
        // given
        db.writeCounterpartyCategoryDao.save(GroceriesRow)

        // when
        val res = db.counterpartyCategoryDao.findByKey("NAIVAS")

        // then
        res shouldBe null
    }

    @Test
    fun savingTheSameKeyTwiceReplacesTheChoice(): Unit = runBlocking {
        // given
        db.writeCounterpartyCategoryDao.save(GroceriesRow)
        val corrected = GroceriesRow.copy(
            categoryId = UUID.randomUUID(),
            updatedAt = UPDATED_AT.plusSeconds(60),
        )

        // when the user corrects the suggestion
        db.writeCounterpartyCategoryDao.save(corrected)

        // then
        countCounterpartyCategories() shouldBe 1
        db.counterpartyCategoryDao.findByKey(KEY) shouldBe corrected
    }

    @Test
    fun keepsOneRowPerCounterparty(): Unit = runBlocking {
        // given
        val other = GroceriesRow.copy(counterpartyKey = "NAIVAS")
        db.writeCounterpartyCategoryDao.saveMany(listOf(GroceriesRow, other))

        // when
        val res = db.counterpartyCategoryDao.findByKey("NAIVAS")

        // then
        res shouldBe other
        countCounterpartyCategories() shouldBe 2
    }

    @Test
    fun deleteByIdForgetsOnlyThatCounterparty(): Unit = runBlocking {
        // given
        val other = GroceriesRow.copy(counterpartyKey = "NAIVAS")
        db.writeCounterpartyCategoryDao.saveMany(listOf(GroceriesRow, other))

        // when
        db.writeCounterpartyCategoryDao.deleteById(KEY)

        // then
        db.counterpartyCategoryDao.findByKey(KEY) shouldBe null
        db.counterpartyCategoryDao.findByKey("NAIVAS") shouldBe other
    }

    @Test
    fun deleteAllEmptiesTheTable(): Unit = runBlocking {
        // given
        db.writeCounterpartyCategoryDao.save(GroceriesRow)

        // when
        db.writeCounterpartyCategoryDao.deleteAll()

        // then
        countCounterpartyCategories() shouldBe 0
    }

    /** The read DAO exposes only a single-key lookup, so row counting goes straight to SQLite. */
    private fun countCounterpartyCategories(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM counterparty_categories").use {
            it.moveToFirst()
            it.getInt(0)
        }

    companion object {
        private const val KEY = "FRANKINNKIKUYU"
        private val UPDATED_AT: Instant = Instant.parse("2026-07-25T16:51:00Z")

        private val GroceriesRow = CounterpartyCategoryEntity(
            categoryId = UUID.randomUUID(),
            updatedAt = UPDATED_AT,
            counterpartyKey = KEY,
        )
    }
}
