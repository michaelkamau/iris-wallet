package com.iris.sms.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.iris.data.datastore.DatastoreKeys
import com.iris.data.model.AccountId
import com.iris.data.model.primitive.NotBlankTrimmedString
import com.iris.data.model.sms.FinancialSender
import com.iris.data.model.sms.SenderId
import com.iris.data.repository.AccountRepository
import com.iris.data.repository.CapturedTransactionRepository
import com.iris.data.repository.FinancialSenderRepository
import com.iris.domain.usecase.sms.SmsCaptureGate
import com.iris.navigation.Navigation
import com.iris.navigation.SmsReviewScreen
import com.iris.sms.capture.DefaultFinancialSenderCatalog
import com.iris.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * The screen where the feature is turned on, explained and taken apart again.
 *
 * Nothing here reads a message. This view model's entire job is consent and configuration: which
 * senders may be looked at, where their money goes, and whether the whole thing is running at
 * all. Everything it changes is a preference or a row in `financial_senders`; it never touches a
 * captured item or a committed transaction, which is why turning the feature off is safe
 * (Acceptance 4.4).
 *
 * Like the review screen it runs on the Compose runtime rather than on flows, and every piece of
 * asynchronous work is a [Command] executed inside a `LaunchedEffect`. Going through the
 * composition rather than `viewModelScope` gives the work one ordering and makes the screen
 * drivable from a list of events in a test.
 */
@Stable
@HiltViewModel
class SmsCaptureSettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val senderRepository: FinancialSenderRepository,
    private val accountRepository: AccountRepository,
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val senderCatalog: DefaultFinancialSenderCatalog,
    private val captureGate: SmsCaptureGate,
    private val navigation: Navigation,
) : ComposeViewModel<SmsCaptureSettingsState, SmsCaptureSettingsEvent>() {

    private var captureEnabled by mutableStateOf(false)
    private var permissionState by mutableStateOf<SmsPermissionUi>(SmsPermissionUi.NotRequested)
    private var senders by mutableStateOf<ImmutableList<SenderMappingUi>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<AccountPickUi>>(persistentListOf())
    private var pendingCount by mutableStateOf(0)
    private var importState by mutableStateOf<HistoricalImportUi>(HistoricalImportUi.AlreadyRun)
    private var rationaleVisible by mutableStateOf(false)
    private var command by mutableStateOf<Command?>(null)
    private var issued = 0

    @Composable
    override fun uiState(): SmsCaptureSettingsState {
        // A null command is the initial load; every later value re-keys the effect, so the work
        // and the reload that follows it happen in one place and in one order.
        LaunchedEffect(command) { execute(command) }

        return SmsCaptureSettingsState(
            captureEnabled = captureEnabled,
            permissionState = permissionState,
            senders = senders,
            accounts = accounts,
            pendingCount = pendingCount,
            importState = importState,
            rationaleVisible = rationaleVisible,
        )
    }

    override fun onEvent(event: SmsCaptureSettingsEvent) {
        when (event) {
            // Deliberately does not enable anything and does not prompt. The user is told what
            // will be read while saying no still costs them a single tap (FR-002).
            SmsCaptureSettingsEvent.OnEnableRequested -> rationaleVisible = true

            // The screen launches the system prompt off the back of this; the answer comes back
            // as OnPermissionResult.
            SmsCaptureSettingsEvent.OnRationaleAccepted -> rationaleVisible = false

            SmsCaptureSettingsEvent.OnRationaleDismissed -> rationaleVisible = false

            is SmsCaptureSettingsEvent.OnPermissionResult -> issue(
                Command.ApplyPermissionResult(
                    granted = event.granted,
                    permanentlyDenied = event.permanentlyDenied,
                ),
            )

            SmsCaptureSettingsEvent.OnDisable -> issue(Command.Disable())

            is SmsCaptureSettingsEvent.OnSenderEnabledChanged -> issue(
                Command.SetSenderEnabled(event.senderId, event.enabled),
            )

            is SmsCaptureSettingsEvent.OnSenderAccountSelected -> issue(
                Command.MapSender(event.senderId, event.accountId),
            )

            is SmsCaptureSettingsEvent.OnAddSender -> issue(
                Command.AddSender(event.rawSender, event.displayName),
            )

            is SmsCaptureSettingsEvent.OnRemoveSender -> issue(Command.RemoveSender(event.senderId))

            // User Story 5 owns the import itself. Until then the action is not offered, so this
            // is unreachable from the UI rather than quietly doing nothing.
            SmsCaptureSettingsEvent.OnStartHistoricalImport -> Unit

            SmsCaptureSettingsEvent.OnOpenReview -> navigation.navigateTo(SmsReviewScreen)

            SmsCaptureSettingsEvent.OnClose -> navigation.back()
        }
    }

    private fun issue(next: Command) {
        command = next.withSequence(++issued)
    }

    private suspend fun execute(current: Command?) {
        when (current) {
            null -> permissionState = if (captureGate.hasReceivePermission()) {
                SmsPermissionUi.Granted
            } else {
                SmsPermissionUi.NotRequested
            }

            is Command.ApplyPermissionResult -> applyPermissionResult(current)
            // Flips one preference. Deleting anything here would lose work the user has already
            // done, and re-enabling would be a fresh start rather than a resumption.
            is Command.Disable -> setCaptureEnabled(false)
            is Command.SetSenderEnabled -> updateSender(current.senderId) {
                copy(enabled = current.enabled)
            }

            is Command.MapSender -> updateSender(current.senderId) {
                copy(account = current.accountId.toAccountId())
            }

            is Command.AddSender -> addSender(current)
            is Command.RemoveSender -> current.senderId.toSenderId()
                ?.let { senderRepository.deleteById(it) }
        }
        reload()
    }

    /**
     * Enabling is the only thing that depends on the system's answer, and it depends on it
     * completely: a denied permission leaves the switch exactly where it was (Acceptance 4.2).
     */
    private suspend fun applyPermissionResult(result: Command.ApplyPermissionResult) {
        if (result.granted) {
            permissionState = SmsPermissionUi.Granted
            // The first production call: without it `financial_senders` is empty and the three
            // formats the parser already understands would have nothing to match against.
            senderCatalog.seed()
            setCaptureEnabled(true)
        } else {
            permissionState = if (result.permanentlyDenied) {
                SmsPermissionUi.PermanentlyDenied
            } else {
                SmsPermissionUi.Denied
            }
            setCaptureEnabled(false)
        }
    }

    /**
     * Adds a sender the app has never heard of, with **no rule set and no account**.
     *
     * Neither omission is a gap to be filled in later. An unknown sender has no parser rules by
     * definition, and an account chosen on the user's behalf here would file real money against
     * an account nobody picked (FR-027a).
     */
    private suspend fun addSender(request: Command.AddSender) {
        val id = request.rawSender.toSenderId()
            // Never clobbers an existing sender: re-adding one the user has already configured
            // would silently unmap it.
            ?.takeIf { candidate -> senderRepository.findAll().none { it.id == candidate } }
            ?: return
        val name = NotBlankTrimmedString.from(request.displayName).getOrNull()
            ?: NotBlankTrimmedString.from(request.rawSender).getOrNull()
            ?: return
        senderRepository.save(
            FinancialSender(
                id = id,
                displayName = name,
                ruleSet = null,
                account = null,
                enabled = true,
            ),
        )
    }

    /** Reads, changes and saves exactly one sender, so the others cannot move (Acceptance 4.5). */
    private suspend fun updateSender(
        senderId: String,
        change: FinancialSender.() -> FinancialSender,
    ) {
        val id = senderId.toSenderId() ?: return
        val existing = senderRepository.findAll().firstOrNull { it.id == id } ?: return
        senderRepository.save(existing.change())
    }

    private suspend fun setCaptureEnabled(enabled: Boolean) {
        dataStore.edit { it[DatastoreKeys.SMS_CAPTURE_ENABLED] = enabled }
        captureEnabled = enabled
    }

    private suspend fun reload() {
        captureEnabled = dataStore.data.first()[DatastoreKeys.SMS_CAPTURE_ENABLED] == true
        val accountNames = accountRepository.findAll()
            .associate { it.id to it.name.value }
        accounts = accountNames
            .map { (id, name) -> AccountPickUi(id = id.value.toString(), name = name) }
            .toImmutableList()
        senders = senderRepository.findAll()
            .map {
                SenderMappingUi(
                    senderId = it.id.value,
                    displayName = it.displayName.value,
                    accountName = it.account?.let(accountNames::get),
                    enabled = it.enabled,
                )
            }
            .toImmutableList()
        pendingCount = capturedTransactionRepository.pendingCount().first()
        importState = importState()
    }

    /**
     * The import is offered only when the inbox can actually be read and it has never completed
     * (FR-030). Anything else is [HistoricalImportUi.AlreadyRun], which renders as nothing at all
     * rather than as a button that would fail.
     */
    private suspend fun importState(): HistoricalImportUi {
        val completedAt = dataStore.data.first()[DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT]
        return when {
            completedAt != null -> HistoricalImportUi.AlreadyRun
            captureGate.canReadInbox() -> HistoricalImportUi.Available
            else -> HistoricalImportUi.AlreadyRun
        }
    }

    private fun String.toSenderId(): SenderId? = SenderId.from(this).getOrNull()

    private fun String.toAccountId(): AccountId? =
        runCatching { AccountId(UUID.fromString(this)) }.getOrNull()

    /**
     * The outstanding piece of work.
     *
     * [seq] exists so that repeating the same action — switching one sender off twice after an
     * intervening change — produces a different value and therefore re-keys the `LaunchedEffect`.
     */
    private sealed interface Command {
        val seq: Int

        fun withSequence(value: Int): Command

        data class ApplyPermissionResult(
            val granted: Boolean,
            val permanentlyDenied: Boolean,
            override val seq: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }

        data class Disable(override val seq: Int = 0) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }

        data class SetSenderEnabled(
            val senderId: String,
            val enabled: Boolean,
            override val seq: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }

        data class MapSender(
            val senderId: String,
            val accountId: String,
            override val seq: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }

        data class AddSender(
            val rawSender: String,
            val displayName: String,
            override val seq: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }

        data class RemoveSender(
            val senderId: String,
            override val seq: Int = 0,
        ) : Command {
            override fun withSequence(value: Int) = copy(seq = value)
        }
    }
}
