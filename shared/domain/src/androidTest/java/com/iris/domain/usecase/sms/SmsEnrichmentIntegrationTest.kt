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
import com.iris.data.model.Category
import com.iris.data.model.CategoryId
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
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.MoneyDirection
import com.iris.data.model.sms.ProviderReference
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.CategoryRepository
import com.iris.data.repository.CounterpartyCategoryRepository
import com.iris.data.repository.CurrencyRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.data.repository.RepositoryMemoFactory
import com.iris.data.repository.TagRepository
import com.iris.data.repository.TransactionRepository
import com.iris.data.repository.mapper.AccountMapper
import com.iris.data.repository.mapper.CapturedTransactionMapper
import com.iris.data.repository.mapper.CategoryMapper
import com.iris.data.repository.mapper.FinancialSenderMapper
import com.iris.data.repository.mapper.TagMapper
import com.iris.data.repository.mapper.TransactionMapper
import com.iris.sms.parser.primitive.CounterpartyNormalizer
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
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
 * User Stories 3 and 4 against a real database.
 *
 * The JVM tests prove the use cases ask for the right things. Only Room can prove that the
 * counterparty→category memory is genuinely *one row that gets replaced* rather than a pile of
 * rows where the newest happens to win a query, that a sender mapping survives being written and
 * read back, and that an unmapped sender's capture is refused by the real write path rather than
 * only by a view model that could be bypassed.
 */
@RunWith(AndroidJUnit4::class)
class SmsEnrichmentIntegrationTest {

    private lateinit var db: IrisRoomDatabase
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var capturedTransactionRepository: CapturedTransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var counterpartyCategoryRepository: CounterpartyCategoryRepository
    private lateinit var senderRepository: FinancialSenderRepository
    private lateinit var confirm: ConfirmCapturedTransactionUseCase
    private lateinit var suggest: SuggestCategoryUseCase

    @Before
    fun setup(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IrisRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dataStoreFile = File(context.cacheDir, "sms-enrich-${UUID.randomUUID()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { dataStoreFile }
        initialiseRepositories()
        initialiseUseCases()
        seedAccountsAndCategories()
    }

    private fun initialiseRepositories() {
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
        accountRepository = AccountRepository(
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
        counterpartyCategoryRepository = CounterpartyCategoryRepository(
            dao = db.counterpartyCategoryDao,
            writeDao = db.writeCounterpartyCategoryDao,
            dispatchersProvider = TestDispatchersProvider,
        )
        senderRepository = FinancialSenderRepository(
            mapper = FinancialSenderMapper(),
            dao = db.financialSenderDao,
            writeDao = db.writeFinancialSenderDao,
            dispatchersProvider = TestDispatchersProvider,
        )
    }

    private fun initialiseUseCases() {
        confirm = ConfirmCapturedTransactionUseCase(
            db = db,
            capturedTransactionRepository = capturedTransactionRepository,
            transactionRepository = transactionRepository,
            ensureTransactionCostCategory = EnsureTransactionCostCategoryUseCase(
                dataStore = dataStore,
                categoryRepository = categoryRepository,
            ),
            counterpartyCategoryRepository = counterpartyCategoryRepository,
            counterpartyNormalizer = CounterpartyNormalizer(),
            timeProvider = TestTimeProvider,
        )
        suggest = SuggestCategoryUseCase(
            counterpartyCategoryRepository = counterpartyCategoryRepository,
            counterpartyNormalizer = CounterpartyNormalizer(),
        )
    }

    private suspend fun seedAccountsAndCategories() {
        accountRepository.save(account(ACCOUNT, "DTB Current", orderNum = 0.0))
        accountRepository.save(account(SAVINGS, "KCB Savings", orderNum = 1.0))
        categoryRepository.save(category(FOOD, "Food & Drinks"))
        categoryRepository.save(category(GROCERIES, "Groceries"))
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreFile.delete()
    }

    // --- User Story 3: the counterparty→category memory ----------------------------------------

    @Test
    fun aPayeeNobodyHasFiledSuggestsNothing(): Unit = runBlocking {
        // when / then — a guess here would be worse than no answer (FR-024)
        suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")).shouldBeNull()
    }

    @Test
    fun filingAPayeeOnceIsWhatMakesItSuggestedNextTime(): Unit = runBlocking {
        // given a payment the user files under Food & Drinks
        val first = entry(counterparty = "Frank Inn Kikuyu")
        capturedTransactionRepository.saveEntry(first)

        // when
        confirm.confirm(id = first.principal.id, category = CategoryId(FOOD)).shouldBeRight()

        // then a second payment to the same payee arrives already categorised (Acceptance 3.2)
        suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")) shouldBe CategoryId(FOOD)
    }

    @Test
    fun overridingASuggestionReplacesTheMemoryRatherThanAddingToIt(): Unit = runBlocking {
        // given the payee is remembered as Food & Drinks
        val first = entry(counterparty = "Frank Inn Kikuyu")
        capturedTransactionRepository.saveEntry(first)
        confirm.confirm(id = first.principal.id, category = CategoryId(FOOD)).shouldBeRight()

        // when the user overrides it on the next payment (Acceptance 3.3)
        val second = entry(counterparty = "Frank Inn Kikuyu")
        capturedTransactionRepository.saveEntry(second)
        confirm.confirm(id = second.principal.id, category = CategoryId(GROCERIES)).shouldBeRight()

        // then the newest choice is what is suggested from now on
        suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")) shouldBe
            CategoryId(GROCERIES)
        // and it replaced the old answer rather than stacking a second row on top of it
        db.counterpartyCategoryDao.findAll().size shouldBe 1
    }

    @Test
    fun thePayeeIsRememberedRegardlessOfHowItWasSpelled(): Unit = runBlocking {
        // given the provider shouts the name in one message
        val first = entry(counterparty = "FRANK INN KIKUYU")
        capturedTransactionRepository.saveEntry(first)
        confirm.confirm(id = first.principal.id, category = CategoryId(FOOD)).shouldBeRight()

        // when the next one is punctuated differently
        val suggestion = suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu."))

        // then it is still the same shop as far as the memory is concerned
        suggestion shouldBe CategoryId(FOOD)
        db.counterpartyCategoryDao.findAll().size shouldBe 1
    }

    @Test
    fun correctingThePayeeRemembersTheCorrectedName(): Unit = runBlocking {
        // given a payment whose payee the parser got as an untidy provider string
        val captured = entry(counterparty = "JAMES KINYUA MWANGI9")
        capturedTransactionRepository.saveEntry(captured)

        // when the user fixes it and files it (FR-023 + FR-024 together)
        confirm.confirm(
            id = captured.principal.id,
            category = CategoryId(FOOD),
            counterparty = NotBlankTrimmedString.unsafe("James Kinyua Mwangi"),
        ).shouldBeRight()

        // then the name they chose is the one that gets remembered
        suggest.suggest(NotBlankTrimmedString.unsafe("James Kinyua Mwangi")) shouldBe
            CategoryId(FOOD)
        // and the untidy original is not also remembered
        db.counterpartyCategoryDao.findAll().size shouldBe 1
    }

    @Test
    fun filingWithoutACategoryTeachesNothing(): Unit = runBlocking {
        // given
        val captured = entry(counterparty = "Frank Inn Kikuyu")
        capturedTransactionRepository.saveEntry(captured)

        // when the user confirms without picking anything
        confirm.confirm(id = captured.principal.id).shouldBeRight()

        // then no memory is invented from the absence of a choice
        db.counterpartyCategoryDao.findAll().shouldBeEmpty()
        suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")).shouldBeNull()
    }

    @Test
    fun twoDifferentPayeesAreRememberedIndependently(): Unit = runBlocking {
        // given
        val first = entry(counterparty = "Frank Inn Kikuyu")
        val second = entry(counterparty = "Naivas Supermarket")
        capturedTransactionRepository.saveEntry(first)
        capturedTransactionRepository.saveEntry(second)

        // when
        confirm.confirm(id = first.principal.id, category = CategoryId(FOOD)).shouldBeRight()
        confirm.confirm(id = second.principal.id, category = CategoryId(GROCERIES)).shouldBeRight()

        // then
        suggest.suggest(NotBlankTrimmedString.unsafe("Frank Inn Kikuyu")) shouldBe CategoryId(FOOD)
        suggest.suggest(NotBlankTrimmedString.unsafe("Naivas Supermarket")) shouldBe
            CategoryId(GROCERIES)
        db.counterpartyCategoryDao.findAll().size shouldBe 2
    }

    @Test
    fun theCorrectionsTheUserMakesAreWhatReachTheLedger(): Unit = runBlocking {
        // given a capture with the parser's values
        val captured = entry(counterparty = "JAMES KINYUA MWANGI9")
        capturedTransactionRepository.saveEntry(captured)

        // when the user corrects all three before committing (FR-023)
        val correctedTime = Instant.parse("2026-07-24T10:15:00Z")
        confirm.confirm(
            id = captured.principal.id,
            category = CategoryId(FOOD),
            description = NotBlankTrimmedString.unsafe("Rent for July"),
            amount = PositiveDouble.unsafe(1_530.50),
            counterparty = NotBlankTrimmedString.unsafe("James Kinyua Mwangi"),
            time = correctedTime,
        ).shouldBeRight()

        // then the row in the ledger is the corrected one, read back out of SQLite
        val recorded = transactionRepository.findAll().single().shouldBeInstanceOf<Expense>()
        recorded.value.amount.value shouldBe 1_530.50
        recorded.title?.value shouldBe "James Kinyua Mwangi"
        recorded.time shouldBe correctedTime
        recorded.category shouldBe CategoryId(FOOD)
        recorded.description?.value?.contains("Rent for July") shouldBe true
    }

    // --- User Story 4: sender mappings ---------------------------------------------------------

    @Test
    fun aSenderMappingSurvivesBeingWrittenAndReadBack(): Unit = runBlocking {
        // given a seeded sender with no account (FR-027a)
        senderRepository.save(mpesa(account = null))
        senderRepository.findAll().single().account.shouldBeNull()

        // when the user maps it (FR-027)
        senderRepository.save(mpesa(account = AccountId(ACCOUNT)))

        // then
        senderRepository.findAll().single().account shouldBe AccountId(ACCOUNT)
    }

    @Test
    fun remappingASenderReplacesTheRowRatherThanAddingASecond(): Unit = runBlocking {
        // given
        senderRepository.save(mpesa(account = AccountId(ACCOUNT)))

        // when
        senderRepository.save(mpesa(account = AccountId(SAVINGS)))

        // then the sender is keyed by its address, so there can only ever be one of it
        senderRepository.findAll().size shouldBe 1
        senderRepository.findAll().single().account shouldBe AccountId(SAVINGS)
    }

    @Test
    fun aDisabledSenderIsNotOfferedForCaptureButKeepsItsMapping(): Unit = runBlocking {
        // given a mapped sender the user switches off (FR-003)
        senderRepository.save(mpesa(account = AccountId(ACCOUNT), enabled = false))

        // then the capture path refuses it outright
        senderRepository.findEnabled(SenderId.unsafe("MPESA")).shouldBeNull()
        // but the mapping is still there for when they switch it back on
        senderRepository.findAll().single().account shouldBe AccountId(ACCOUNT)
    }

    @Test
    fun sendersAreEnabledAndMappedIndependentlyOfEachOther(): Unit = runBlocking {
        // given two senders (Acceptance 4.5)
        senderRepository.save(mpesa(account = AccountId(ACCOUNT)))
        senderRepository.save(
            FinancialSender(
                id = SenderId.unsafe("KCB"),
                displayName = NotBlankTrimmedString.unsafe("KCB Bank"),
                ruleSet = RuleSetId.unsafe("kcb"),
                account = null,
                enabled = true,
            ),
        )

        // when one is switched off
        senderRepository.save(mpesa(account = AccountId(ACCOUNT), enabled = false))

        // then the other is untouched in both respects
        val kcb = senderRepository.findEnabled(SenderId.unsafe("KCB")).shouldNotBeNull()
        kcb.enabled shouldBe true
        kcb.account.shouldBeNull()
    }

    // --- FR-027a: an unmapped sender's captures are held, not defaulted ------------------------

    @Test
    fun aCaptureFromAnUnmappedSenderCannotBeConfirmed(): Unit = runBlocking {
        // given a sender nobody has mapped, and a capture from it
        senderRepository.save(mpesa(account = null))
        val captured = entry(counterparty = "Frank Inn Kikuyu", account = null)
        capturedTransactionRepository.saveEntry(captured)

        // when the user tries to file it anyway
        val result = confirm.confirm(id = captured.principal.id, category = CategoryId(FOOD))

        // then the real write path refuses it — no account is invented (FR-027a)
        result.shouldBeLeft().shouldBeInstanceOf<ConfirmCaptureError.MissingAccount>()
        transactionRepository.findAll().shouldBeEmpty()
        // and it is still waiting, not lost
        capturedTransactionRepository.findAllPending().size shouldBe 1
    }

    @Test
    fun aHeldCaptureIsCommittedOnceAnAccountIsChosen(): Unit = runBlocking {
        // given a held capture
        val captured = entry(counterparty = "Frank Inn Kikuyu", account = null)
        capturedTransactionRepository.saveEntry(captured)
        confirm.confirm(id = captured.principal.id).shouldBeLeft()

        // when the user picks the account at review time
        confirm.confirm(
            id = captured.principal.id,
            account = AccountId(SAVINGS),
            category = CategoryId(FOOD),
        ).shouldBeRight()

        // then it lands in the account they chose, and nowhere else
        val recorded = transactionRepository.findAll().single().shouldBeInstanceOf<Expense>()
        recorded.account shouldBe AccountId(SAVINGS)
        capturedTransactionRepository.findAllPending().shouldBeEmpty()
    }

    @Test
    fun aRefusedConfirmLeavesNoHalfWrittenState(): Unit = runBlocking {
        // given
        val captured = entry(counterparty = "Frank Inn Kikuyu", account = null)
        capturedTransactionRepository.saveEntry(captured)

        // when
        confirm.confirm(id = captured.principal.id, category = CategoryId(FOOD)).shouldBeLeft()

        // then nothing at all was written — not the ledger row, and not the category memory
        transactionRepository.findAll().shouldBeEmpty()
        db.counterpartyCategoryDao.findAll().shouldBeEmpty()
    }

    private fun mpesa(account: AccountId?, enabled: Boolean = true) = FinancialSender(
        id = SenderId.unsafe("MPESA"),
        displayName = NotBlankTrimmedString.unsafe("M-PESA"),
        ruleSet = RuleSetId.unsafe("mpesa"),
        account = account,
        enabled = enabled,
    )

    private fun entry(
        counterparty: String,
        account: UUID? = ACCOUNT,
    ) = CapturedEntry(
        principal = CapturedTransaction(
            id = CapturedTransactionId(UUID.randomUUID()),
            sender = SenderId.unsafe("MPESA"),
            kind = CapturedKind.Principal(MoneyDirection.MoneyOut),
            amount = PositiveDouble.unsafe(1_350.00),
            asset = AssetCode.unsafe("KES"),
            time = PAID_AT,
            counterparty = NotBlankTrimmedString.unsafe(counterparty),
            reference = ProviderReference.unsafe("UGP7B0ITE4"),
            account = account?.let(::AccountId),
            category = null,
            description = null,
            duplicateOf = null,
            capturedAt = PAID_AT,
            origin = CaptureOrigin.LiveBroadcast,
        ),
        fee = null,
    )

    private fun account(id: UUID, name: String, orderNum: Double) = Account(
        id = AccountId(id),
        name = NotBlankTrimmedString.unsafe(name),
        asset = AssetCode.unsafe("KES"),
        color = ColorInt(0),
        icon = null,
        includeInBalance = true,
        orderNum = orderNum,
    )

    private fun category(id: UUID, name: String) = Category(
        id = CategoryId(id),
        name = NotBlankTrimmedString.unsafe(name),
        color = ColorInt(0),
        icon = null,
        orderNum = 0.0,
    )

    private companion object {
        private val ACCOUNT: UUID = UUID.fromString("0f6d1e2a-1111-4c9b-9d0e-2b7a1c3d4e5f")
        private val SAVINGS: UUID = UUID.fromString("0f6d1e2a-2222-4c9b-9d0e-2b7a1c3d4e5f")
        private val FOOD: UUID = UUID.fromString("0f6d1e2a-3333-4c9b-9d0e-2b7a1c3d4e5f")
        private val GROCERIES: UUID = UUID.fromString("0f6d1e2a-4444-4c9b-9d0e-2b7a1c3d4e5f")
        private val PAID_AT: Instant = Instant.parse("2026-07-25T16:50:00Z")
    }
}
