package com.iris.domain.usecase.sms

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.iris.data.datastore.DatastoreKeys
import com.iris.data.model.Category
import com.iris.data.model.CategoryId
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.repository.CategoryRepository
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * FR-017 in one sentence: **one** transaction-cost category, ever.
 *
 * The interesting cases are not the happy path but the two ways a naive implementation gets it
 * wrong — looking the category up by name (so renaming it spawns a duplicate) and trusting a
 * stored id blindly (so deleting it files fees against nothing).
 */
class EnsureTransactionCostCategoryUseCaseTest {

    private val categoryRepository = mockk<CategoryRepository>(relaxUnitFun = true)
    private val dataStore = FakePreferencesDataStore()

    private lateinit var useCase: EnsureTransactionCostCategoryUseCase

    @Before
    fun setup() {
        coEvery { categoryRepository.findMaxOrderNum() } returns 4.0
        coEvery { categoryRepository.findById(any()) } returns null
        useCase = EnsureTransactionCostCategoryUseCase(
            dataStore = dataStore,
            categoryRepository = categoryRepository,
        )
    }

    @Test
    fun `the first fee creates the category and remembers which one it is`() = runTest {
        // given nothing stored
        val saved = slot<Category>()
        coEvery { categoryRepository.save(capture(saved)) } returns Unit

        // when
        val result = useCase.ensure()

        // then
        result.shouldBeRight() shouldBe saved.captured.id
        saved.captured.name.value shouldBe "Transaction costs"
        // Ordered after everything the user already had, not ahead of it.
        saved.captured.orderNum shouldBe 5.0
        dataStore.stored() shouldBe saved.captured.id.value.toString()
    }

    @Test
    fun `the second fee reuses the category rather than creating another`() = runTest {
        // given the first fee already created one
        val saved = slot<Category>()
        coEvery { categoryRepository.save(capture(saved)) } returns Unit
        val first = useCase.ensure().shouldBeRight()
        coEvery { categoryRepository.findById(first) } returns category(first, "Transaction costs")

        // when a second fee asks
        val second = useCase.ensure()

        // then
        second.shouldBeRight() shouldBe first
        coVerify(exactly = 1) { categoryRepository.save(any()) }
    }

    @Test
    fun `a category the user renamed is still the one fees go into`() = runTest {
        // given the user renamed and recoloured it — it is an ordinary category, after all
        val existing = CategoryId(UUID.randomUUID())
        dataStore.put(existing.value.toString())
        coEvery { categoryRepository.findById(existing) } returns category(existing, "Bank fees")

        // when
        val result = useCase.ensure()

        // then the lookup never mentions the name, so the rename costs nothing
        result.shouldBeRight() shouldBe existing
        coVerify(exactly = 0) { categoryRepository.save(any()) }
    }

    @Test
    fun `a category the user deleted is recreated once, not on every fee`() = runTest {
        // given a stored id that no longer resolves
        val gone = CategoryId(UUID.randomUUID())
        dataStore.put(gone.value.toString())
        val saved = slot<Category>()
        coEvery { categoryRepository.save(capture(saved)) } returns Unit

        // when the fee arrives, and then another one
        val first = useCase.ensure().shouldBeRight()
        coEvery { categoryRepository.findById(first) } returns category(first, "Transaction costs")
        val second = useCase.ensure()

        // then
        first shouldBe saved.captured.id
        first shouldBe second.shouldBeRight()
        coVerify(exactly = 1) { categoryRepository.save(any()) }
    }

    @Test
    fun `a stored value that is not an id is replaced rather than crashed on`() = runTest {
        // given a preference corrupted by something outside this use case
        dataStore.put("not-a-uuid")
        coEvery { categoryRepository.save(any()) } returns Unit

        // when
        val result = useCase.ensure()

        // then
        result.shouldBeRight()
        dataStore.stored() shouldBe result.getOrNull()?.value?.toString()
    }

    @Test
    fun `a repository that cannot write is reported, never thrown`() = runTest {
        // given
        coEvery { categoryRepository.save(any()) } throws IllegalStateException("disk full")

        // when
        val result = useCase.ensure()

        // then the caller rolls its transaction back rather than crashing a confirm
        result.isLeft() shouldBe true
    }

    private fun category(id: CategoryId, name: String) = Category(
        id = id,
        name = NotBlankTrimmedString.unsafe(name),
        color = ColorInt(0),
        icon = null,
        orderNum = 5.0,
    )

    /**
     * A real `DataStore` needs a file and a scope; this needs neither, and unlike a mock it
     * actually round-trips what was written, which is the whole thing under test.
     */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }

        suspend fun put(value: String) {
            updateData {
                it.toMutablePreferences()
                    .apply { this[DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID] = value }
                    .toPreferences()
            }
        }

        suspend fun stored(): String? =
            data.first()[DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID]
    }
}
