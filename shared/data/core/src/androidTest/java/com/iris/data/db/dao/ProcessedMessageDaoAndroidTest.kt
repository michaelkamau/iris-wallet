package com.iris.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.db.entity.ProcessedMessageEntity
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * `processed_messages` against real SQLite — the table that makes re-delivery and historical
 * import idempotent (FR-028) and keeps a dismissed message dismissed (FR-025).
 */
@RunWith(AndroidJUnit4::class)
class ProcessedMessageDaoAndroidTest {

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
    fun existsIsTrueAfterTheMessageIsRecorded(): Unit = runBlocking {
        // given
        db.writeProcessedMessageDao.save(CapturedRow)

        // when
        val res = db.processedMessageDao.exists(FINGERPRINT)

        // then
        res shouldBe true
    }

    @Test
    fun existsIsFalseForAFingerprintThatWasNeverRecorded(): Unit = runBlocking {
        // given
        db.writeProcessedMessageDao.save(CapturedRow)

        // when
        val res = db.processedMessageDao.exists("mpesa:NEVERSEEN1")

        // then
        res shouldBe false
    }

    @Test
    fun theSameFingerprintTwiceDoesNotCreateASecondRow(): Unit = runBlocking {
        // given a message that was captured and then dismissed by the user
        db.writeProcessedMessageDao.save(CapturedRow)

        // when
        db.writeProcessedMessageDao.save(CapturedRow.copy(outcome = "DISMISSED"))

        // then the dedupe key holds one row, carrying the latest outcome
        countProcessedMessages() shouldBe 1
        outcomeOf(FINGERPRINT) shouldBe "DISMISSED"
    }

    @Test
    fun redeliveryOfABatchDoesNotCreateDuplicates(): Unit = runBlocking {
        // given the same batch is processed twice, as a re-import would (FR-028)
        val other = CapturedRow.copy(fingerprint = "mpesa:AD3EA389C13A")
        db.writeProcessedMessageDao.saveMany(listOf(CapturedRow, other))

        // when
        db.writeProcessedMessageDao.saveMany(listOf(CapturedRow, other))

        // then
        countProcessedMessages() shouldBe 2
    }

    @Test
    fun differentFingerprintsFromTheSameSenderAreBothKept(): Unit = runBlocking {
        // given
        val other = CapturedRow.copy(fingerprint = "mpesa:AD3EA389C13A", outcome = "IGNORED")
        db.writeProcessedMessageDao.saveMany(listOf(CapturedRow, other))

        // when
        val res = db.processedMessageDao.exists(other.fingerprint)

        // then
        res shouldBe true
        countProcessedMessages() shouldBe 2
    }

    @Test
    fun deleteByIdForgetsOnlyThatFingerprint(): Unit = runBlocking {
        // given
        val other = CapturedRow.copy(fingerprint = "mpesa:AD3EA389C13A")
        db.writeProcessedMessageDao.saveMany(listOf(CapturedRow, other))

        // when
        db.writeProcessedMessageDao.deleteById(FINGERPRINT)

        // then
        db.processedMessageDao.exists(FINGERPRINT) shouldBe false
        db.processedMessageDao.exists(other.fingerprint) shouldBe true
    }

    @Test
    fun deleteAllEmptiesTheTable(): Unit = runBlocking {
        // given
        db.writeProcessedMessageDao.save(CapturedRow)

        // when
        db.writeProcessedMessageDao.deleteAll()

        // then
        countProcessedMessages() shouldBe 0
    }

    /** The read DAO exposes only `EXISTS`, so row counting goes straight to SQLite. */
    private fun countProcessedMessages(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM processed_messages").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun outcomeOf(fingerprint: String): String =
        db.openHelper.writableDatabase.query(
            "SELECT outcome FROM processed_messages WHERE fingerprint = '$fingerprint'",
        ).use {
            it.moveToFirst()
            it.getString(0)
        }

    companion object {
        private const val FINGERPRINT = "mpesa:UGP7B0ITE4"

        private val CapturedRow = ProcessedMessageEntity(
            senderId = "MPESA",
            outcome = "CAPTURED",
            processedAt = Instant.parse("2026-07-25T16:51:00Z"),
            fingerprint = FINGERPRINT,
        )
    }
}
