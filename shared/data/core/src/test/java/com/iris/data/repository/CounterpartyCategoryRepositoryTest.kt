package com.iris.data.repository

import com.iris.base.TestDispatchersProvider
import com.iris.data.db.dao.read.CounterpartyCategoryDao
import com.iris.data.db.dao.write.WriteCounterpartyCategoryDao
import com.iris.data.db.entity.CounterpartyCategoryEntity
import com.iris.data.model.CategoryId
import com.iris.data.model.sms.CounterpartyCategory
import com.iris.data.model.sms.CounterpartyKey
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
import java.util.UUID

class CounterpartyCategoryRepositoryTest {
    private val dao = mockk<CounterpartyCategoryDao>()
    private val writeDao = mockk<WriteCounterpartyCategoryDao>()

    private lateinit var repository: CounterpartyCategoryRepository

    @Before
    fun setup() {
        repository = CounterpartyCategoryRepository(
            dao = dao,
            writeDao = writeDao,
            dispatchersProvider = TestDispatchersProvider,
        )
    }

    @Test
    fun `find category - returns the category last chosen for the counterparty`() = runTest {
        // given
        coEvery { dao.findByKey(KEY) } returns Row

        // when
        val res = repository.findCategory(CounterpartyKey.unsafe("Frank Inn Kikuyu"))

        // then
        res shouldBe CATEGORY_ID
    }

    @Test
    fun `find category - null when the counterparty has never been categorised`() = runTest {
        // given
        coEvery { dao.findByKey(KEY) } returns null

        // when
        val res = repository.findCategory(CounterpartyKey.unsafe("Frank Inn Kikuyu"))

        // then
        res shouldBe null
    }

    @Test
    fun `find category - looks up the normalised key, so punctuation and case do not matter`() =
        runTest {
            // given
            coEvery { dao.findByKey(any()) } returns null

            // when
            repository.findCategory(CounterpartyKey.unsafe("frank-inn, kikuyu"))

            // then
            coVerify(exactly = 1) { dao.findByKey(KEY) }
        }

    @Test
    fun `remember - upserts the choice under the normalised key`() = runTest {
        // given
        coEvery { writeDao.save(any()) } just runs

        // when
        repository.remember(
            CounterpartyCategory(
                counterparty = CounterpartyKey.unsafe("Frank Inn Kikuyu"),
                category = CATEGORY_ID,
                updatedAt = UPDATED_AT,
            ),
        )

        // then
        coVerify(exactly = 1) { writeDao.save(Row) }
    }

    companion object {
        private const val KEY = "FRANKINNKIKUYU"
        private val CATEGORY_ID = CategoryId(UUID.randomUUID())
        private val UPDATED_AT: Instant = Instant.parse("2026-07-25T16:51:00Z")

        private val Row = CounterpartyCategoryEntity(
            categoryId = CATEGORY_ID.value,
            updatedAt = UPDATED_AT,
            counterpartyKey = KEY,
        )
    }
}
