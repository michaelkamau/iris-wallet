package com.iris.sms.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.iris.base.time.TimeProvider
import com.iris.data.datastore.DatastoreKeys
import com.iris.data.model.Account
import com.iris.data.model.AccountId
import com.iris.data.model.primitive.AssetCode
import com.iris.data.model.primitive.ColorInt
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.RuleSetId
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.domain.usecase.sms.SmsCaptureGate
import com.iris.navigation.Navigation
import com.iris.navigation.SmsReviewScreen
import com.iris.sms.capture.DefaultFinancialSenderCatalog
import com.iris.sms.capture.SmsImportScheduler
import com.iris.sms.capture.SmsImportWorkState
import com.iris.ui.testing.ComposeViewModelTest
import com.iris.ui.testing.runTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * The settings screen is where consent lives, so these tests are mostly about what the app is
 * *not* allowed to do: enable itself, prompt before explaining, guess an account, or lose the
 * user's work when they switch it off.
 */
class SmsCaptureSettingsViewModelTest : ComposeViewModelTest() {

    private val dataStore = FakePreferencesDataStore()
    private val senderRepository = mockk<FinancialSenderRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val capturedTransactionRepository = mockk<CapturedTransactionRepository>()
    private val senderCatalog = mockk<DefaultFinancialSenderCatalog>(relaxed = true)
    private val importScheduler = FakeSmsImportScheduler()
    private val timeProvider = mockk<TimeProvider>()
    private val navigation = mockk<Navigation>(relaxed = true)

    /** `financial_senders`, so a save or a delete is observable through a reload. */
    private val stored = mutableListOf<FinancialSender>()
    private var receivePermission = false
    private var inboxPermission = false

    private val captureGate = object : SmsCaptureGate {
        override suspend fun isEnabled(): Boolean =
            dataStore.snapshot()[DatastoreKeys.SMS_CAPTURE_ENABLED] == true

        override fun hasReceivePermission(): Boolean = receivePermission
        override fun canReadInbox(): Boolean = inboxPermission
    }

    @Before
    fun setup() {
        stored.clear()
        stored += Mpesa
        stored += Kcb
        receivePermission = false
        inboxPermission = false
        coEvery { senderRepository.findAll() } answers { stored.toList() }
        coEvery { senderRepository.save(any()) } answers {
            val saved = firstArg<FinancialSender>()
            stored.removeAll { it.id == saved.id }
            stored += saved
        }
        coEvery { senderRepository.deleteById(any()) } answers {
            // MockK hands a `@JvmInline value class` argument over as its underlying type.
            stored.removeAll { it.id.value == firstArg<String>() }
            Unit
        }
        coEvery { accountRepository.findAll() } returns listOf(Current, Savings)
        coEvery { capturedTransactionRepository.pendingCount() } returns flowOf(0)
        // Seeding is the catalog's own job and is tested there; here all that matters is that
        // enabling is what triggers it.
        coEvery { senderCatalog.seed() } returns Unit
        every { timeProvider.utcNow() } returns Instant.parse("2026-07-27T10:25:34Z")
    }

    // --- Off by default ------------------------------------------------------------------------

    @Test
    fun `capture is off before anyone has asked for it`() {
        // given nothing has been stored — which is a fresh install and every upgrade alike

        // when / then (FR-001, SC-010)
        viewModel().runTest {
            captureEnabled shouldBe false
            permissionState shouldBe SmsPermissionUi.NotRequested
            rationaleVisible shouldBe false
        }
    }

    @Test
    fun `a stored opt-in is what turns it on, and nothing else`() {
        // given the user opted in on a previous run
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        receivePermission = true

        // when / then
        viewModel().runTest {
            captureEnabled shouldBe true
            permissionState shouldBe SmsPermissionUi.Granted
        }
    }

    // --- The consent path ----------------------------------------------------------------------

    @Test
    fun `asking to enable shows the explanation and enables nothing`() {
        // when the user flips the switch on (FR-002)
        viewModel().runTest(events = listOf(SmsCaptureSettingsEvent.OnEnableRequested)) {
            // then they are told what will be read before the system has asked them anything
            rationaleVisible shouldBe true
            captureEnabled shouldBe false
            permissionState shouldBe SmsPermissionUi.NotRequested
        }
    }

    @Test
    fun `backing out of the explanation leaves everything as it was`() {
        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnEnableRequested,
                SmsCaptureSettingsEvent.OnRationaleDismissed,
            ),
        ) {
            // then
            rationaleVisible shouldBe false
            captureEnabled shouldBe false
        }
    }

    @Test
    fun `accepting the explanation still does not enable anything on its own`() {
        // given accepting only opens the door to the system prompt

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnEnableRequested,
                SmsCaptureSettingsEvent.OnRationaleAccepted,
            ),
        ) {
            // then the feature waits for the operating system's answer
            rationaleVisible shouldBe false
            captureEnabled shouldBe false
        }
    }

    @Test
    fun `granting the permission is what turns capture on`() {
        // when (Acceptance 4.1)
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnEnableRequested,
                SmsCaptureSettingsEvent.OnRationaleAccepted,
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = true,
                    permanentlyDenied = false,
                ),
            ),
        ) {
            captureEnabled shouldBe true
            permissionState shouldBe SmsPermissionUi.Granted
        }

        // then the choice survives the screen, because it is the same flag the receiver reads
        dataStore.snapshot()[DatastoreKeys.SMS_CAPTURE_ENABLED] shouldBe true
    }

    @Test
    fun `granting the permission seeds the senders the parser already understands`() {
        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = true,
                    permanentlyDenied = false,
                ),
            ),
        ) {
            captureEnabled shouldBe true
        }

        // then, otherwise `financial_senders` would be empty and nothing would ever match (FR-004)
        coVerify(exactly = 1) { senderCatalog.seed() }
    }

    @Test
    fun `refusing the permission leaves the switch off and offers another go`() {
        // when (Acceptance 4.2)
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnEnableRequested,
                SmsCaptureSettingsEvent.OnRationaleAccepted,
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = false,
                    permanentlyDenied = false,
                ),
            ),
        ) {
            // then
            captureEnabled shouldBe false
            permissionState shouldBe SmsPermissionUi.Denied
        }

        // and nothing was seeded on the back of a refusal
        coVerify(exactly = 0) { senderCatalog.seed() }
        dataStore.snapshot()[DatastoreKeys.SMS_CAPTURE_ENABLED] shouldBe false
    }

    @Test
    fun `a refusal Android will not re-ask is a different offer entirely`() {
        // when the system says "don't ask again"
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = false,
                    permanentlyDenied = true,
                ),
            ),
        ) {
            // then, because a "try again" button here would do nothing at all
            permissionState shouldBe SmsPermissionUi.PermanentlyDenied
            captureEnabled shouldBe false
        }
    }

    @Test
    fun `a second attempt after a refusal starts at the explanation again`() {
        // when (Acceptance 4.2 — the retry route)
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = false,
                    permanentlyDenied = false,
                ),
                SmsCaptureSettingsEvent.OnEnableRequested,
            ),
        ) {
            // then
            rationaleVisible shouldBe true
            captureEnabled shouldBe false
        }
    }

    @Test
    fun `granting after an earlier refusal works`() {
        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = false,
                    permanentlyDenied = false,
                ),
                SmsCaptureSettingsEvent.OnEnableRequested,
                SmsCaptureSettingsEvent.OnRationaleAccepted,
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = true,
                    permanentlyDenied = false,
                ),
            ),
        ) {
            // then
            captureEnabled shouldBe true
            permissionState shouldBe SmsPermissionUi.Granted
        }
    }

    // --- Turning it off ------------------------------------------------------------------------

    @Test
    fun `turning it off flips the flag and touches nothing else`() {
        // given a running feature with a configured mapping
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        receivePermission = true
        stored[0] = Mpesa.copy(account = AccountId(CURRENT))

        // when (FR-003, Acceptance 4.4)
        viewModel().runTest(events = listOf(SmsCaptureSettingsEvent.OnDisable)) {
            captureEnabled shouldBe false
        }

        // then the senders and their mappings are exactly where the user left them
        stored.map { it.id.value } shouldContainExactlyInAnyOrder listOf("MPESA", "KCB")
        stored.first { it.id.value == "MPESA" }.account shouldBe AccountId(CURRENT)
        // and nothing was deleted
        coVerify(exactly = 0) { senderRepository.deleteById(any()) }
    }

    @Test
    fun `turning it off and on again resumes rather than restarts`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        stored[0] = Mpesa.copy(account = AccountId(CURRENT))

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnDisable,
                SmsCaptureSettingsEvent.OnPermissionResult(
                    granted = true,
                    permanentlyDenied = false,
                ),
            ),
        ) {
            // then the mapping the user made before is still the one in force
            captureEnabled shouldBe true
            senders.single { it.senderId == "MPESA" }.accountName shouldBe "DTB Current"
        }
    }

    // --- Senders -------------------------------------------------------------------------------

    @Test
    fun `the configured senders arrive unmapped rather than guessed at`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when / then (FR-027a)
        viewModel().runTest {
            senders.map { it.displayName } shouldContainExactly listOf("M-PESA", "KCB Bank")
            senders.forEach { it.accountName shouldBe null }
        }
    }

    @Test
    fun `mapping a sender to an account affects only that sender`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when (FR-027, Acceptance 4.5)
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnSenderAccountSelected("MPESA", CURRENT.toString()),
            ),
        ) {
            // then
            senders.single { it.senderId == "MPESA" }.accountName shouldBe "DTB Current"
            senders.single { it.senderId == "KCB" }.accountName shouldBe null
        }
    }

    @Test
    fun `remapping a sender replaces the account rather than adding one`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnSenderAccountSelected("MPESA", CURRENT.toString()),
                SmsCaptureSettingsEvent.OnSenderAccountSelected("MPESA", SAVINGS.toString()),
            ),
        ) {
            // then
            senders.single { it.senderId == "MPESA" }.accountName shouldBe "KCB Savings"
        }
        stored.count { it.id.value == "MPESA" } shouldBe 1
    }

    @Test
    fun `switching one sender off leaves the others running`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when (FR-003, Acceptance 4.5)
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnSenderEnabledChanged("MPESA", enabled = false),
            ),
        ) {
            // then
            senders.single { it.senderId == "MPESA" }.enabled shouldBe false
            senders.single { it.senderId == "KCB" }.enabled shouldBe true
            // and the feature as a whole is still on
            captureEnabled shouldBe true
        }
    }

    @Test
    fun `switching a sender off keeps its account mapping for when it comes back`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnSenderAccountSelected("MPESA", CURRENT.toString()),
                SmsCaptureSettingsEvent.OnSenderEnabledChanged("MPESA", enabled = false),
                SmsCaptureSettingsEvent.OnSenderEnabledChanged("MPESA", enabled = true),
            ),
        ) {
            // then
            val sender = senders.single { it.senderId == "MPESA" }
            sender.enabled shouldBe true
            sender.accountName shouldBe "DTB Current"
        }
    }

    @Test
    fun `a sender the user adds starts unmapped and enabled`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnAddSender(
                    rawSender = "Equity Bank",
                    displayName = "Equity",
                ),
            ),
        ) {
            // then it is present, switched on, and pointed at nothing (FR-027a)
            val added = senders.single { it.displayName == "Equity" }
            added.enabled shouldBe true
            added.accountName shouldBe null
            // and normalised the way an incoming message's sender is, or it would never match
            added.senderId shouldBe "EQUITY BANK"
        }
    }

    @Test
    fun `adding a sender that is already configured does not unmap it`() {
        // given a sender the user has already pointed at an account
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when they add it again by hand
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnSenderAccountSelected("MPESA", CURRENT.toString()),
                SmsCaptureSettingsEvent.OnAddSender(rawSender = "mpesa", displayName = "M-PESA"),
            ),
        ) {
            // then
            senders.single { it.senderId == "MPESA" }.accountName shouldBe "DTB Current"
        }
        stored.count { it.id.value == "MPESA" } shouldBe 1
    }

    @Test
    fun `a blank sender is not added`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when
        viewModel().runTest(
            events = listOf(
                SmsCaptureSettingsEvent.OnAddSender(rawSender = "   ", displayName = "Nothing"),
            ),
        ) {
            // then
            senders.size shouldBe 2
        }
    }

    @Test
    fun `removing a sender removes only that one`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when
        viewModel().runTest(
            events = listOf(SmsCaptureSettingsEvent.OnRemoveSender("MPESA")),
        ) {
            // then
            senders.map { it.senderId } shouldContainExactly listOf("KCB")
        }
    }

    // --- The review route ----------------------------------------------------------------------

    @Test
    fun `the waiting count comes from the pending items themselves`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        coEvery { capturedTransactionRepository.pendingCount() } returns flowOf(3)

        // when / then (FR-021a)
        viewModel().runTest {
            pendingCount shouldBe 3
        }
    }

    @Test
    fun `opening the review inbox navigates rather than doing anything itself`() {
        // when
        viewModel().runTest(events = listOf(SmsCaptureSettingsEvent.OnOpenReview)) {
            captureEnabled shouldBe false
        }

        // then
        coVerify(exactly = 1) { navigation.navigateTo(SmsReviewScreen) }
    }

    // --- The historical import -----------------------------------------------------------------

    @Test
    fun `the import is offered only when the inbox can actually be read`() {
        // given the permission to read old messages was granted
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        inboxPermission = true

        // when / then (FR-030)
        viewModel().runTest {
            importState shouldBe HistoricalImportUi.Available
        }
    }

    @Test
    fun `the import action is hidden until the user grants read inbox access`() {
        // given `READ_SMS` has not been requested
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)

        // when / then the import action itself is unavailable, but the opt-in permission affordance is not
        viewModel().runTest {
            importState shouldBe HistoricalImportUi.AlreadyRun
            historicalImportPermissionRequired shouldBe true
        }
    }

    @Test
    fun `an import that has already run is never offered again`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        inboxPermission = true
        dataStore.put(DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT, 1_785_000_000_000L)

        // when / then
        viewModel().runTest {
            importState shouldBe HistoricalImportUi.AlreadyRun
            historicalImportPermissionRequired shouldBe false
        }
    }

    @Test
    fun `starting an import reports worker progress and finishes with its captured count`() {
        // given
        dataStore.put(DatastoreKeys.SMS_CAPTURE_ENABLED, true)
        inboxPermission = true
        importScheduler.states = listOf(
            SmsImportWorkState.Running(processed = 200, captured = 3),
            SmsImportWorkState.Finished(captured = 3),
        )

        // when / then
        viewModel().runTest(events = listOf(SmsCaptureSettingsEvent.OnStartHistoricalImport)) {
            importState shouldBe HistoricalImportUi.Finished(captured = 3)
        }
        importScheduler.enqueuedAt shouldBe Instant.parse("2026-07-27T10:25:34Z")
    }

    private fun viewModel() = SmsCaptureSettingsViewModel(
        dataStore = dataStore,
        senderRepository = senderRepository,
        accountRepository = accountRepository,
        capturedTransactionRepository = capturedTransactionRepository,
        senderCatalog = senderCatalog,
        captureGate = captureGate,
        importScheduler = importScheduler,
        timeProvider = timeProvider,
        navigation = navigation,
    )

    private class FakeSmsImportScheduler : SmsImportScheduler {
        var enqueuedAt: Instant? = null
        var states: List<SmsImportWorkState> = emptyList()

        override suspend fun enqueue(now: Instant) {
            enqueuedAt = now
        }

        override fun observe(): Flow<SmsImportWorkState> = states.asFlow()
    }

    /**
     * An in-memory [DataStore].
     *
     * A real one writes to a file and reads back asynchronously, which under immediate
     * recomposition turns "did the flag change?" into a race. This keeps the same contract —
     * read your writes, observable through `data` — without the disk.
     */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }

        fun snapshot(): Preferences = state.value

        fun <T> put(key: Preferences.Key<T>, value: T) {
            val next = mutablePreferencesOf()
            state.value.asMap().forEach { (existing, existingValue) ->
                @Suppress("UNCHECKED_CAST")
                next[existing as Preferences.Key<Any>] = existingValue
            }
            @Suppress("UNCHECKED_CAST")
            next[key as Preferences.Key<Any>] = value as Any
            state.value = next
        }
    }

    private companion object {
        private val CURRENT = UUID.fromString("cccccccc-0000-0000-0000-000000000001")
        private val SAVINGS = UUID.fromString("cccccccc-0000-0000-0000-000000000002")

        private val Mpesa = FinancialSender(
            id = SenderId.unsafe("MPESA"),
            displayName = NotBlankTrimmedString.unsafe("M-PESA"),
            ruleSet = RuleSetId.unsafe("mpesa"),
            account = null,
            enabled = true,
        )

        private val Kcb = FinancialSender(
            id = SenderId.unsafe("KCB"),
            displayName = NotBlankTrimmedString.unsafe("KCB Bank"),
            ruleSet = RuleSetId.unsafe("kcb"),
            account = null,
            enabled = true,
        )

        private val Current = account(CURRENT, "DTB Current", orderNum = 0.0)
        private val Savings = account(SAVINGS, "KCB Savings", orderNum = 1.0)

        private fun account(id: UUID, name: String, orderNum: Double) = Account(
            id = AccountId(id),
            name = NotBlankTrimmedString.unsafe(name),
            asset = AssetCode.unsafe("KES"),
            color = ColorInt(0),
            icon = null,
            includeInBalance = true,
            orderNum = orderNum,
        )
    }
}
