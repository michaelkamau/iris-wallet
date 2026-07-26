package com.iris.domain.usecase.sms

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.base.TestDispatchersProvider
import com.iris.data.DataObserver
import com.iris.data.db.IrisRoomDatabase
import com.iris.data.model.Account
import com.iris.data.model.AccountId
import com.iris.data.model.Expense
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.primitive.PositiveDouble
import com.iris.data.model.sms.CaptureOrigin
import com.iris.data.model.sms.CapturedEntry
import com.iris.data.model.sms.CapturedKind
import com.iris.data.model.sms.CapturedTransaction
import com.iris.data.model.sms.CapturedTransactionId
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CategoryRepository
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.data.repository.CurrencyRepository
import com.iris.data.repository.RepositoryMemoFactory
import com.iris.data.repository.TagRepository
import com.iris.data.repository.TransactionRepository
import com.iris.data.repository.mapper.AccountMapper
import com.iris.data.repository.mapper.CapturedTransactionMapper
import com.iris.data.repository.mapper.CategoryMapper
import com.iris.data.repository.mapper.TagMapper
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * User Story 2 against a real database.
 *
 * The JVM tests prove the use cases ask for the right things; only Room can prove the two rows a
 * transaction cost turns into actually survive a write, come back joined, and disappear together.
 * Everything here goes through the real repositories — the DAOs, the mappers and the transaction
 * boundaries are all under test, not stubbed around.
 */
@RunWith(AndroidJUnit4::class)
class CapturedTransactionCostIntegrationTest {

    private lateinit var db: IrisRoomDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var capturedTransactionRepository: CapturedTransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var ensureTransactionCostCategory: EnsureTransactionCostCategoryUseCase
    private lateinit var counterpartyCategoryRepository: CounterpartyCategoryRepository
    private lateinit var confirm: ConfirmCapturedTransactionUseCase

    @Before
    fun setup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IrisRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreFile = File(context.cacheDir, "sms-cost-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { dataStoreFile }

        val memoFactory = RepositoryMemoFactory(DataObserver(), TestDispatchersProvider)
        capturedTransactionRepository = CapturedTransactionRepository(
            mapper = CapturedTransactionMapper(),
            dao = db.capturedTransactionDao,
            writeDao = db.writeCapturedTransactionDao,
            db = db,
            dispatchersProvider = TestDispatchersProvider,
        )
        categoryRepository = CategoryRepository(
            mapper = CategoryMapper(),
            writeCategoryDao = db.writeCategoryDao,
            categoryDao = db.categoryDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = memoFactory,
        )
        val accountRepository = AccountRepository(
            mapper = AccountMapper(
                CurrencyRepository(
                    settingsDao = db.settingsDao,
                    writeSettingsDao = db.writeSettingsDao,
                    dispatchersProvider = TestDispatchersProvider,
                ),
            ),
            accountDao = db.accountDao,
            writeAccountDao = db.writeAccountDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = memoFactory,
        )
        transactionRepository = TransactionRepository(
            mapper = TransactionMapper(accountRepository),
            transactionDao = db.transactionDao,
            writeTransactionDao = db.writeTransactionDao,
            dispatchersProvider = TestDispatchersProvider,
            tagRepository = TagRepository(
                mapper = TagMapper(),
                tagDao = db.tagDao,
                tagAssociationDao = db.tagAssociationDao,
                writeTagDao = db.writeTagDao,
                writeTagAssociationDao = db.writeTagAssociationDao,
                dispatchersProvider = TestDispatchersProvider,
                memoFactory = memoFactory,
            ),
        )
        ensureTransactionCostCategory = EnsureTransactionCostCategoryUseCase(
            dataStore = dataStore,
            categoryRepository = categoryRepository,
        )
        counterpartyCategoryRepository = CounterpartyCategoryRepository(
            dao = db.counterpartyCategoryDao,
            writeDao = db.writeCounterpartyCategoryDao,
            dispatchersProvider = TestDispatchersProvider,
        )
        confirm = ConfirmCapturedTransactionUseCase(
            db = db,
            capturedTransactionRepository = capturedTransactionRepository,
            transactionRepository = transactionRepository,
            ensureTransactionCostCategory = ensureTransactionCostCategory,
            counterpartyCategoryRepository = counterpartyCategoryRepository,
            counterpartyNormalizer = CounterpartyNormalizer(),
            timeProvider = TestTimeProvider,
        )

        accountRepository.save(
            Account(
                id = AccountId(ACCOUNT),
                name = NotBlankTrimmedString.unsafe("DTB Current"),
                asset = AssetCode.unsafe("KES"),
                color = ColorInt(0),
                icon = null,
                includeInBalance = true,
                orderNum = 0.0,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreFile.delete()
    }

    @Test
    fun paymentAndItsChargeRoundTripAsOneEntry(): Unit = runBlocking {
        // given the DTB transfer and the 59.76 charge it reported
        val entry = entryWithFee()

        // when
        capturedTransactionRepository.saveEntry(entry)

        // then they come back joined, not as two unrelated pending items
        val pending = capturedTransactionRepository.findAllPending()
        pending.size shouldBe 1
        pending.single().principal.id shouldBe entry.principal.id
        pending.single().fee?.amount?.value shouldBe 59.76

        // and the link and the tax survived the mapper in both directions
        val fee = pending.single().fee.shouldNotBeNull()
        val kind = fee.kind.shouldBeInstanceOf<CapturedKind.Fee>()
        kind.parent shouldBe entry.principal.id
        kind.tax?.value shouldBe 8.25
    }

    @Test
    fun deletingThePaymentTakesItsChargeWithIt(): Unit = runBlocking {
        // given
        val entry = entryWithFee()
        capturedTransactionRepository.saveEntry(entry)

        // when
        capturedTransactionRepository.deleteEntry(entry.principal.id)

        // then no orphaned charge is left pointing at a row that is gone (FR-018)
        capturedTransactionRepository.findAllPending() shouldBe emptyList()
        db.capturedTransactionDao.findAll() shouldBe emptyList()
    }

    @Test
    fun confirmingWritesTwoLedgerRowsAndClearsTheInbox(): Unit = runBlocking {
        // given
        val entry = entryWithFee()
        capturedTransactionRepository.saveEntry(entry)

        // when
        confirm.confirm(entry.principal.id).shouldBeRight()

        // then the payment and the charge are two separate expenses (FR-016)
        val ledger = transactionRepository.findAll()
        ledger.size shouldBe 2
        ledger.map { it.shouldBeInstanceOf<Expense>().value.amount.value }
            .sorted() shouldBe listOf(59.76, 6_000.00)

        // and nothing is left waiting to be reviewed a second time
        capturedTransactionRepository.findAllPending() shouldBe emptyList()
    }

    @Test
    fun theChargeIsFiledUnderTheDedicatedCategoryAndTheTaxRidesOnIt(): Unit = runBlocking {
        // given
        val entry = entryWithFee()
        capturedTransactionRepository.saveEntry(entry)

        // when
        confirm.confirm(entry.principal.id).shouldBeRight()

        // then
        val costCategory = ensureTransactionCostCategory.ensure().shouldBeRight()
        val fee = transactionRepository.findAll()
            .single { it is Expense && it.value.amount.value == 59.76 }
        fee.category shouldBe costCategory
        categoryRepository.findById(costCategory)?.name?.value shouldBe "Transaction costs"
        // The tax is detail on the charge, never a transaction of its own (FR-019).
        fee.description?.value?.contains("Incl. tax KES 8.25") shouldBe true
    }

    @Test
    fun theTransactionCostCategoryIsCreatedOnceAndReused(): Unit = runBlocking {
        // given two fee-bearing messages
        val first = entryWithFee()
        val second = entryWithFee()
        capturedTransactionRepository.saveEntry(first)
        capturedTransactionRepository.saveEntry(second)

        // when both are confirmed
        confirm.confirm(first.principal.id).shouldBeRight()
        confirm.confirm(second.principal.id).shouldBeRight()

        // then the user is left with one transaction-cost category, not one per fee (FR-017)
        categoryRepository.findAll().count { it.name.value == "Transaction costs" } shouldBe 1
    }

    @Test
    fun aZeroCostMessageProducesExactlyOneLedgerRow(): Unit = runBlocking {
        // given the M-PESA wording that reports `Transaction cost, Ksh0.00` (Acceptance 2.3)
        val entry = CapturedEntry(principal = principal(), fee = null)
        capturedTransactionRepository.saveEntry(entry)

        // when
        confirm.confirm(entry.principal.id).shouldBeRight()

        // then no zero-valued charge, and no category invented to hold one
        transactionRepository.findAll().size shouldBe 1
        categoryRepository.findAll() shouldBe emptyList()
    }

    @Test
    fun aChargeWithNoPaymentIsReviewableAndConfirmableOnItsOwn(): Unit = runBlocking {
        // given a bank charge advice (FR-020)
        val standalone = CapturedEntry(
            principal = fee(parent = null, amount = 33.00, tax = null),
            fee = null,
        )
        capturedTransactionRepository.saveEntry(standalone)

        // when
        val pending = capturedTransactionRepository.findAllPending()
        confirm.confirm(standalone.principal.id).shouldBeRight()

        // then it heads its own entry and lands under the transaction-cost category
        pending.size shouldBe 1
        pending.single().principal.id shouldBe standalone.principal.id
        val recorded = transactionRepository.findAll().single()
        recorded.shouldBeInstanceOf<Expense>().value.amount.value shouldBe 33.00
        recorded.category shouldBe ensureTransactionCostCategory.ensure().shouldBeRight()
    }

    @Test
    fun aParentedChargeIsNeverReviewedOnItsOwn(): Unit = runBlocking {
        // given a payment with a charge, plus an unrelated payment with none
        capturedTransactionRepository.saveEntry(entryWithFee())
        capturedTransactionRepository.saveEntry(CapturedEntry(principal = principal(), fee = null))

        // when
        val pending = capturedTransactionRepository.findAllPending()

        // then the inbox shows two items, not three — the charge is part of one of them
        pending.size shouldBe 2
        db.capturedTransactionDao.findAll().size shouldBe 3
    }

    private fun entryWithFee(): CapturedEntry {
        val head = principal()
        return CapturedEntry(
            principal = head,
            fee = fee(parent = head.id, amount = 59.76, tax = 8.25),
        )
    }

    private fun principal() = row(
        amount = 6_000.00,
        kind = CapturedKind.Principal(MoneyDirection.MoneyOut),
    )

    private fun fee(
        parent: CapturedTransactionId?,
        amount: Double,
        tax: Double?,
    ) = row(
        amount = amount,
        kind = CapturedKind.Fee(parent = parent, tax = tax?.let(PositiveDouble::unsafe)),
    )

    private fun row(amount: Double, kind: CapturedKind) = CapturedTransaction(
        id = CapturedTransactionId(UUID.randomUUID()),
        sender = SenderId.unsafe("DTB-KENYA"),
        kind = kind,
        amount = PositiveDouble.unsafe(amount),
        asset = AssetCode.unsafe("KES"),
        time = PAID_AT,
        counterparty = NotBlankTrimmedString.unsafe("Michael Kamau Njuguna"),
        reference = ProviderReference.unsafe("AD3EA389C13A7"),
        account = AccountId(ACCOUNT),
        category = null,
        description = null,
        duplicateOf = null,
        capturedAt = PAID_AT,
        origin = CaptureOrigin.LiveBroadcast,
    )

    private companion object {
        private val ACCOUNT: UUID = UUID.fromString("0f6d1e2a-1111-4c9b-9d0e-2b7a1c3d4e5f")
        private val PAID_AT: Instant = Instant.parse("2026-07-25T14:41:00Z")
    }
}
