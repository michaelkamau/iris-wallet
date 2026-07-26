package com.iris.data.repository

import com.iris.base.TestDispatchersProvider
import com.iris.data.db.dao.read.FinancialSenderDao
import com.iris.data.db.dao.write.WriteFinancialSenderDao
import com.iris.data.db.entity.FinancialSenderEntity
import com.iris.data.model.AccountId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.mapper.FinancialSenderMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FinancialSenderRepositoryTest {
    private val dao = mockk<FinancialSenderDao>()
    private val writeDao = mockk<WriteFinancialSenderDao>()

    private lateinit var repository: FinancialSenderRepository

    @Before
    fun setup() {
        repository = FinancialSenderRepository(
            mapper = FinancialSenderMapper(),
            dao = dao,
            writeDao = writeDao,
            dispatchersProvider = TestDispatchersProvider,
        )
    }

    @Test
    fun `find all - maps every enrolled sender`() = runTest {
        // given
        coEvery { dao.findAll() } returns listOf(MpesaRow, MpesaRow.copy(senderId = "DTB"))

        // when
        val res = repository.findAll()

        // then
        res shouldBe listOf(mpesa(), mpesa().copy(id = SenderId.unsafe("DTB")))
    }

    @Test
    fun `find all - drops a row that cannot be mapped`() = runTest {
        // given
        coEvery { dao.findAll() } returns listOf(MpesaRow.copy(displayName = " "))

        // when
        val res = repository.findAll()

        // then
        res.shouldBeEmpty()
    }

    @Test
    fun `find enabled - returns the sender when it is enrolled and enabled`() = runTest {
        // given
        coEvery { dao.findEnabledById("MPESA") } returns MpesaRow

        // when
        val res = repository.findEnabled(SenderId.unsafe("MPESA"))

        // then
        res shouldBe mpesa()
    }

    @Test
    fun `find enabled - null for an unknown or disabled sender`() = runTest {
        // given the dao filters `enabled = 1` itself, so both cases arrive as no row
        coEvery { dao.findEnabledById("MPESA") } returns null

        // when
        val res = repository.findEnabled(SenderId.unsafe("MPESA"))

        // then
        res shouldBe null
    }

    @Test
    fun `find enabled - normalises the sender before asking the dao`() = runTest {
        // given
        coEvery { dao.findEnabledById(any()) } returns null

        // when
        repository.findEnabled(SenderId.unsafe(" m-pesa "))

        // then
        coVerify(exactly = 1) { dao.findEnabledById("M-PESA") }
    }

    @Test
    fun `save - writes the flat form`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.save(mpesa())

        // then
        coVerify(exactly = 1) { writeDao.save(MpesaRow) }
    }

    @Test
    fun `delete by id`() = runTest {
        // given
        coEvery { writeDao.deleteById(any()) } just runs

        // when
        repository.deleteById(SenderId.unsafe("MPESA"))

        // then
        coVerify(exactly = 1) { writeDao.deleteById("MPESA") }
    }

    companion object {
        private val ACCOUNT_ID = AccountId(UUID.randomUUID())

        private val MpesaRow = FinancialSenderEntity(
            displayName = "M-PESA",
            ruleSetId = "mpesa",
            accountId = ACCOUNT_ID.value,
            enabled = true,
            senderId = "MPESA",
        )

        private fun mpesa() = FinancialSender(
            id = SenderId.unsafe("MPESA"),
            displayName = NotBlankTrimmedString.unsafe("M-PESA"),
            ruleSet = RuleSetId.unsafe("mpesa"),
            account = ACCOUNT_ID,
            enabled = true,
        )
    }
}
