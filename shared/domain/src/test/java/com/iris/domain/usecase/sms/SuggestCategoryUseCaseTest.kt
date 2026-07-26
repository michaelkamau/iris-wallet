package com.iris.domain.usecase.sms

import androidx.room.withTransaction
import com.iris.base.time.TimeProvider
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.CounterpartyCategory
import com.iris.data.model.sms.CounterpartyKey
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.data.repository.TransactionRepository
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * The counterparty→category memory, from both ends: what [SuggestCategoryUseCase] reads and what
 * [ConfirmCapturedTransactionUseCase] writes.
 *
 * They are tested together on purpose. A suggestion that is written under one key and read under
 * another is the exact failure Acceptance 3.3 describes — the user corrects a category, and the
 * next payment suggests the old one anyway — and neither half can catch it alone.
 */
class SuggestCategoryUseCaseTest {

    private val counterpartyCategoryRepository =
        mockk<CounterpartyCategoryRepository>(relaxUnitFun = true)
    private val capturedTransactionRepository =
        mockk<CapturedTransactionRepository>(relaxUnitFun = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxUnitFun = true)
    private val ensureTransactionCostCategory = mockk<EnsureTransactionCostCategoryUseCase>()
    private val db = mockk<IrisRoomDatabase>()
    private val normalizer = CounterpartyNormalizer()

    /** The `counterparty_categories` table, keyed exactly as the repository keys it. */
    private val memory = mutableMapOf<String, CategoryId>()

    private lateinit var useCase: SuggestCategoryUseCase
    private lateinit var confirm: ConfirmCapturedTransactionUseCase

    @Before
    fun setup() {
        memory.clear()
        mockkStatic(ROOM_DATABASE_KT)
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        // MockK hands a `@JvmInline value class` argument to `answers` as its underlying type.
        coEvery { counterpartyCategoryRepository.findCategory(any()) } answers {
            memory[firstArg<String>()]
        }
        coEvery { counterpartyCategoryRepository.remember(any()) } answers {
            val value = firstArg<CounterpartyCategory>()
            // An upsert, not an insert: the latest choice replaces the previous one.
            memory[value.counterparty.value] = value.category
        }
        useCase = SuggestCategoryUseCase(
            counterpartyNormalizer = normalizer,
            counterpartyCategoryRepository = counterpartyCategoryRepository,
        )
        confirm = ConfirmCapturedTransactionUseCase(
            db = db,
            capturedTransactionRepository = capturedTransactionRepository,
            transactionRepository = transactionRepository,
            counterpartyCategoryRepository = counterpartyCategoryRepository,
            counterpartyNormalizer = normalizer,
            ensureTransactionCostCategory = ensureTransactionCostCategory,
            timeProvider = FixedTimeProvider,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_KT)
    }

    @Test
    fun `a counterparty the user has filed before suggests what they chose`() = runTest {
        // given the user filed a payment to this shop under Food & Drinks
        remember("FRANK INN KIKUYU", Food)

        // when
        val suggestion = useCase.suggest(name("Frank Inn Kikuyu"))

        // then
        suggestion shouldBe Food
    }

    @Test
    fun `a counterparty nobody has categorised suggests nothing, rather than guessing`() = runTest {
        // given a memory holding somebody else entirely
        remember("FRANK INN KIKUYU", Food)

        // when
        val suggestion = useCase.suggest(name("Naivas Supermarket"))

        // then null, so the user is asked rather than shown a category they never picked
        suggestion shouldBe null
    }

    @Test
    fun `a message that named nobody suggests nothing`() = runTest {
        // given
        remember("FRANK INN KIKUYU", Food)

        // when / then no counterparty means no key, and no key means no memory to read
        useCase.suggest(null) shouldBe null
        coVerify(exactly = 0) { counterpartyCategoryRepository.findCategory(any()) }
    }

    @Test
    fun `the same payee written in a different case shares one memory row`() = runTest {
        // given the name as the parser normalised it on a good day
        remember("FRANK INN KIKUYU", Food)

        // when the same payee arrives shouted, spaced and punctuated (FR-024)
        val variants = listOf(
            "FRANK INN KIKUYU",
            "Frank Inn Kikuyu",
            "frank inn kikuyu",
            "Frank  Inn, Kikuyu",
        )

        // then every spelling reads the one row
        variants.forEach { variant ->
            useCase.suggest(name(variant)) shouldBe Food
        }
        memory.size shouldBe 1
    }

    @Test
    fun `a counterparty that is nothing but punctuation is not looked up at all`() = runTest {
        // given / when a name with no alphanumeric characters, which has no key
        val suggestion = useCase.suggest(name("---"))

        // then it reads as unknown rather than throwing (FR-024 never fails a review)
        suggestion shouldBe null
        coVerify(exactly = 0) { counterpartyCategoryRepository.findCategory(any()) }
    }

    // --- The write half: what confirming actually leaves behind ---------------------------------

    @Test
    fun `confirming with a category is what teaches the app the payee`() = runTest {
        // given a payee nobody has categorised
        useCase.suggest(name("Frank Inn Kikuyu")) shouldBe null
        val id = pending(counterparty = "Frank Inn Kikuyu")

        // when the user files it under Food & Drinks
        confirm.confirm(id, category = Food).shouldBeRight()

        // then the next payment to the same payee is suggested it (Acceptance 3.2)
        useCase.suggest(name("Frank Inn Kikuyu")) shouldBe Food
    }

    @Test
    fun `overriding a suggestion replaces it, so the most recent choice is the one suggested`() =
        runTest {
            // given the payee is remembered as Food & Drinks
            confirm.confirm(pending(counterparty = "Frank Inn Kikuyu"), category = Food)
                .shouldBeRight()
            useCase.suggest(name("Frank Inn Kikuyu")) shouldBe Food

            // when the user overrides the suggestion on the next payment
            confirm.confirm(pending(counterparty = "Frank Inn Kikuyu"), category = Groceries)
                .shouldBeRight()

            // then the override is what is suggested from now on — an update, not a second row
            // (Acceptance 3.3)
            useCase.suggest(name("Frank Inn Kikuyu")) shouldBe Groceries
            memory.size shouldBe 1
        }

    @Test
    fun `a corrected payee is remembered under the corrected name, not the misread one`() =
        runTest {
            // given the parser read the payee as something the user did not recognise
            val id = pending(counterparty = "Frnk Inn Kikuy")

            // when the user fixes the name and files it (FR-023)
            confirm.confirm(
                id = id,
                category = Food,
                counterparty = name("Frank Inn Kikuyu"),
            ).shouldBeRight()

            // then the memory follows the correction, so it is useful next time
            useCase.suggest(name("Frank Inn Kikuyu")) shouldBe Food
            useCase.suggest(name("Frnk Inn Kikuy")) shouldBe null
        }

    @Test
    fun `confirming without choosing a category remembers nothing`() = runTest {
        // given
        val id = pending(counterparty = "Frank Inn Kikuyu")

        // when the user just confirms what was extracted
        confirm.confirm(id).shouldBeRight()

        // then no row means "uncategorised" is not suggested back to them later
        memory shouldBe emptyMap()
        coVerify(exactly = 0) { counterpartyCategoryRepository.remember(any()) }
    }

    @Test
    fun `confirming a payment that named nobody remembers nothing`() = runTest {
        // given a message with no payee to key a memory on
        val id = pending(counterparty = null)

        // when
        confirm.confirm(id, category = Food).shouldBeRight()

        // then
        memory shouldBe emptyMap()
    }

    /** Puts one pending principal in front of [confirm] and returns its id. */
    private fun pending(counterparty: String?): CapturedTransactionId {
        val id = CapturedTransactionId(UUID.randomUUID())
        val entry = CapturedEntry(
            principal = SmsFixtures.captured(id = id, counterparty = counterparty),
            fee = null,
        )
        coEvery { capturedTransactionRepository.findById(id) } returns entry
        return id
    }

    private suspend fun remember(counterparty: String, category: CategoryId) {
        counterpartyCategoryRepository.remember(
            CounterpartyCategory(
                counterparty = CounterpartyKey.unsafe(counterparty),
                category = category,
                updatedAt = SmsFixtures.CapturedAt,
            ),
        )
    }

    private fun name(value: String) = NotBlankTrimmedString.unsafe(value)

    private object FixedTimeProvider : TimeProvider {
        override fun getZoneId(): ZoneId = ZoneId.of("UTC")
        override fun utcNow(): Instant = SmsFixtures.CapturedAt
        override fun localNow(): LocalDateTime = LocalDateTime.ofInstant(utcNow(), getZoneId())
        override fun localDateNow(): LocalDate = localNow().toLocalDate()
        override fun localTimeNow(): LocalTime = localNow().toLocalTime()
    }

    private companion object {
        private const val ROOM_DATABASE_KT = "androidx.room.RoomDatabaseKt"

        private val Food = CategoryId(UUID.fromString("00000000-0000-0000-0000-0000000000c1"))
        private val Groceries = CategoryId(UUID.fromString("00000000-0000-0000-0000-0000000000c2"))
    }
}
