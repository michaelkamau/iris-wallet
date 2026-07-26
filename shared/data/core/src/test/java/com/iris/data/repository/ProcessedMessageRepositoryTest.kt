package com.iris.data.repository

import com.iris.base.TestDispatchersProvider
import com.iris.data.db.dao.read.ProcessedMessageDao
import com.iris.data.db.dao.write.WriteProcessedMessageDao
import com.iris.data.db.entity.ProcessedMessageEntity
import com.iris.data.model.sms.MessageFingerprint
import com.iris.data.model.sms.ProcessedMessage
import com.iris.data.model.sms.ProcessedOutcome
import com.iris.data.model.sms.SenderId
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ProcessedMessageRepositoryTest {
    private val dao = mockk<ProcessedMessageDao>()
    private val writeDao = mockk<WriteProcessedMessageDao>()

    private lateinit var repository: ProcessedMessageRepository

    @Before
    fun setup() {
        repository = ProcessedMessageRepository(
            dao = dao,
            writeDao = writeDao,
            dispatchersProvider = TestDispatchersProvider,
        )
    }

    @Test
    fun `is processed - true when the fingerprint is already recorded`() = runTest {
        // given
        coEvery { dao.exists(FINGERPRINT) } returns true

        // when
        val res = repository.isProcessed(MessageFingerprint.unsafe(FINGERPRINT))

        // then
        res shouldBe true
    }

    @Test
    fun `is processed - false for a fingerprint that has never been seen`() = runTest {
        // given
        coEvery { dao.exists(FINGERPRINT) } returns false

        // when
        val res = repository.isProcessed(MessageFingerprint.unsafe(FINGERPRINT))

        // then
        res shouldBe false
    }

    @Test
    fun `record - writes a captured outcome as its column value`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.record(processed(ProcessedOutcome.Captured))

        // then
        coVerify(exactly = 1) { writeDao.save(row("CAPTURED")) }
    }

    @Test
    fun `record - writes an ignored outcome as its column value`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.record(processed(ProcessedOutcome.Ignored))

        // then
        coVerify(exactly = 1) { writeDao.save(row("IGNORED")) }
    }

    @Test
    fun `record - writes a dismissed outcome as its column value`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.record(processed(ProcessedOutcome.Dismissed))

        // then
        coVerify(exactly = 1) { writeDao.save(row("DISMISSED")) }
    }

    companion object {
        private const val FINGERPRINT = "mpesa:UGP7B0ITE4"
        private val PROCESSED_AT: Instant = Instant.parse("2026-07-25T16:51:00Z")

        private fun processed(outcome: ProcessedOutcome) = ProcessedMessage(
            fingerprint = MessageFingerprint.unsafe(FINGERPRINT),
            sender = SenderId.unsafe("MPESA"),
            outcome = outcome,
            processedAt = PROCESSED_AT,
        )

        private fun row(outcome: String) = ProcessedMessageEntity(
            senderId = "MPESA",
            outcome = outcome,
            processedAt = PROCESSED_AT,
            fingerprint = FINGERPRINT,
        )
    }
}
