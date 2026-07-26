# Contract: UI, Navigation and Android Platform Surfaces

Covers the two new `:screen:*` modules, the navigation entries, the broadcast/worker/notification
contracts in `:shared:sms:capture`, and the permission flow.

---

## 1. Navigation contract

Added to `shared/ui/navigation/src/main/java/com/iris/navigation/Screens.kt` — both leave
`Screen.isLegacy` at its `false` default, so neither gets the legacy `Surface` wrapper:

```kotlin
/** The pending-review list for SMS-captured transactions. */
data object SmsReviewScreen : Screen

/** Consent, master toggle, per-sender mapping and the one-time 30-day import. */
data object SmsCaptureSettingsScreen : Screen
```

Mapped in `app/src/main/java/com/iris/IrisNavGraph.kt` inside the existing `when (screen)`:

```kotlin
SmsReviewScreen -> SmsReviewScreenImpl()
SmsCaptureSettingsScreen -> SmsCaptureSettingsScreenImpl()
```

(The `…Impl` naming follows the existing convention for `data object` screens —
`FeaturesScreen -> FeaturesScreenImpl()`, `DisclaimerScreen -> DisclaimerScreenImpl()` — which
avoids the name clash between the navigation `data object` and the composable.)

`app/build.gradle.kts` gains `implementation(projects.screen.smsReview)` and
`implementation(projects.screen.smsSettings)`; `settings.gradle.kts` gains
`include(":screen:sms-review")`, `include(":screen:sms-settings")`, `include(":shared:sms:parser")`
and `include(":shared:sms:capture")`.

**Entry points into the feature**:

1. A new row in `screen/settings/src/main/java/com/iris/settings/SettingsScreen.kt` →
   `SmsCaptureSettingsScreen`, showing the pending count as a trailing badge when > 0 (FR-021a).
   This is the **only** edit made to an existing screen module.
2. The capture notification's content intent → `SmsReviewScreen` (FR-026).
3. `SmsCaptureSettingsScreen` has a "Review N captured transactions" action → `SmsReviewScreen`.

---

## 2. `:screen:sms-review` contract

Module `screen/sms-review/`, namespace `com.iris.sms.review`, plugin `iris.feature`.
Four files in `screen/sms-review/src/main/java/com/iris/sms/review/`.

### `SmsReviewState.kt`

```kotlin
@Immutable
sealed interface SmsReviewState {
    data object Loading : SmsReviewState
    data object Empty : SmsReviewState
    data class Content(
        val items: ImmutableList<CapturedItemUi>,
        val pendingCount: Int,
        val expandedItemId: String?,          // stable string id; null = list collapsed
        val categories: ImmutableList<CategoryPickUi>,
        val accounts: ImmutableList<AccountPickUi>,
    ) : SmsReviewState
}

@Immutable
data class CapturedItemUi(
    val id: String,                   // CapturedTransactionId.value.toString()
    val amountFormatted: String,      // pre-formatted via com.iris.ui.FormatMoneyUseCase
    val counterparty: String,
    val timeFormatted: String,        // pre-formatted via com.iris.ui.time.TimeFormatter
    val reference: String?,
    val accountName: String?,         // null renders the "choose an account" affordance
    val categoryName: String?,
    val description: String,
    val feeFormatted: String?,        // the linked transaction cost, when present
    val status: ReviewStatusUi,
)

@Immutable
sealed interface ReviewStatusUi {
    data object NeedsAccount : ReviewStatusUi
    data class PossibleDuplicate(val existingSummary: String) : ReviewStatusUi
    data object ReadyToConfirm : ReviewStatusUi
}

@Immutable data class CategoryPickUi(val id: String, val name: String, val colorArgb: Int)
@Immutable data class AccountPickUi(val id: String, val name: String)
```

**Stability contract**: only primitives, `@Immutable` classes and `ImmutableList`. No domain type
(`CapturedTransaction`, `Instant`, `PositiveDouble`, `CategoryId`) crosses into view-state — this is
what keeps `:ci-actions:compose-stability` green and Paparazzi snapshots deterministic.

### `SmsReviewEvent.kt`

```kotlin
sealed interface SmsReviewEvent {
    data class OnItemExpanded(val id: String?) : SmsReviewEvent
    data class OnCategorySelected(val id: String, val categoryId: String) : SmsReviewEvent
    data class OnDescriptionChanged(val id: String, val description: String) : SmsReviewEvent
    data class OnAccountSelected(val id: String, val accountId: String) : SmsReviewEvent   // FR-027a
    data class OnAmountEdited(val id: String, val amount: String) : SmsReviewEvent          // FR-023
    data class OnCounterpartyEdited(val id: String, val counterparty: String) : SmsReviewEvent
    data class OnTimeEdited(val id: String, val epochMillis: Long) : SmsReviewEvent
    data class OnConfirm(val id: String) : SmsReviewEvent                                    // FR-021
    data class OnDismiss(val id: String, val alsoRemoveFee: Boolean) : SmsReviewEvent        // FR-018, FR-025
    data class OnKeepDespiteDuplicate(val id: String) : SmsReviewEvent                       // FR-029
    data object OnClose : SmsReviewEvent
}
```

### `SmsReviewViewModel.kt`

```kotlin
@Stable
@HiltViewModel
class SmsReviewViewModel @Inject constructor(
    private val capturedTransactionRepository: CapturedTransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val confirmCapturedTransaction: ConfirmCapturedTransactionUseCase,
    private val dismissCapturedTransaction: DismissCapturedTransactionUseCase,
    private val suggestCategory: SuggestCategoryUseCase,
    private val formatMoney: FormatMoneyUseCase,
    private val timeFormatter: TimeFormatter,
    private val navigation: Navigation,
) : ComposeViewModel<SmsReviewState, SmsReviewEvent>() {

    @Composable override fun uiState(): SmsReviewState { /* LaunchedEffect(Unit) { load() } */ }
    override fun onEvent(event: SmsReviewEvent) = when (event) { /* exhaustive */ }
}
```

**Behavioural contract asserted by `SmsReviewViewModelTest` (`viewModel.runTest(events = …) { }`)**:

| Given / When | Then |
|---|---|
| No pending items | state is `SmsReviewState.Empty` |
| An item whose sender has no account | `status == ReviewStatusUi.NeedsAccount` and `OnConfirm` is a no-op (FR-027a) |
| `OnAccountSelected` then `OnConfirm` on that item | `ConfirmCapturedTransactionUseCase` invoked once; the item leaves the list |
| An item whose counterparty has a remembered category | `categoryName` is pre-filled before any user input (FR-024) |
| `OnCategorySelected` overriding the suggestion, then `OnConfirm` | the chosen category is what is passed to confirm (Acceptance 3.3) |
| An item flagged `duplicateOf` | `status == PossibleDuplicate`; `OnKeepDespiteDuplicate` clears the flag without committing |
| `OnDismiss(alsoRemoveFee = true)` on a principal with a fee | both rows are removed (FR-018) |
| `OnConfirm` on a principal with a fee | two transactions are committed, the fee under the transaction-cost category (FR-016/FR-017) |

### Screenshot contract

`screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewScreenshotTest.kt`, extending
`PaparazziScreenshotTest`, `@RunWith(TestParameterInjector::class)`, `@TestParameter theme:
PaparazziTheme`. Snapshots: `empty`, `single item ready to confirm`, `item needing an account`,
`item with a fee expanded`, `item flagged as a possible duplicate`.
Fixtures are **static**: hard-coded `amountFormatted = "KES 1,350.00"` and
`timeFormatted = "25 Jul 2026, 19:50"` strings — never `Instant.now()` or a live formatter.

---

## 3. `:screen:sms-settings` contract

Module `screen/sms-settings/`, namespace `com.iris.sms.settings`, plugin `iris.feature`.

```kotlin
@Immutable
data class SmsCaptureSettingsState(
    val captureEnabled: Boolean,
    val permissionState: SmsPermissionUi,
    val senders: ImmutableList<SenderMappingUi>,
    val accounts: ImmutableList<AccountPickUi>,
    val pendingCount: Int,
    val importState: HistoricalImportUi,
    val rationaleVisible: Boolean,          // the pre-permission explanation (FR-002)
)

@Immutable sealed interface SmsPermissionUi {
    data object Granted : SmsPermissionUi
    data object NotRequested : SmsPermissionUi
    data object Denied : SmsPermissionUi             // offers a retry (Acceptance 4.2)
    data object PermanentlyDenied : SmsPermissionUi  // deep-links to app settings
}

@Immutable data class SenderMappingUi(
    val senderId: String, val displayName: String,
    val accountName: String?,               // null => "Not mapped" (FR-027a)
    val enabled: Boolean,                   // per-sender switch (FR-003)
)

@Immutable sealed interface HistoricalImportUi {
    data object Available : HistoricalImportUi       // opt-in, 30 days (FR-030)
    data class Running(val processed: Int) : HistoricalImportUi
    data class Finished(val captured: Int) : HistoricalImportUi   // FR-031
    data object AlreadyRun : HistoricalImportUi
}

sealed interface SmsCaptureSettingsEvent {
    data object OnEnableRequested : SmsCaptureSettingsEvent       // shows the rationale first
    data object OnRationaleAccepted : SmsCaptureSettingsEvent     // triggers the system prompt
    data class OnPermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) : …
    data object OnDisable : SmsCaptureSettingsEvent
    data class OnSenderEnabledChanged(val senderId: String, val enabled: Boolean) : …
    data class OnSenderAccountSelected(val senderId: String, val accountId: String) : …
    data class OnAddSender(val rawSender: String, val displayName: String) : …
    data class OnRemoveSender(val senderId: String) : …
    data object OnStartHistoricalImport : SmsCaptureSettingsEvent
    data object OnOpenReview : SmsCaptureSettingsEvent
    data object OnClose : SmsCaptureSettingsEvent
}
```

**Behavioural contract**:

- Default state on a fresh install and on upgrade is `captureEnabled = false` (FR-001).
- `OnEnableRequested` never triggers the system dialog directly; it sets `rationaleVisible = true`.
  The rationale text must state *what is read*, *what is extracted* and *that nothing leaves the
  device* (FR-002).
- Denying leaves `captureEnabled = false` and shows a retry (Acceptance 4.2).
- `OnDisable` flips the DataStore flag only — it never deletes captured items or committed
  transactions (Acceptance 4.4, Edge "Permission revoked mid-stream").
- Each sender is mapped and toggled independently (Acceptance 4.5).
- `OnStartHistoricalImport` is only offered when `READ_SMS` is granted and
  `SMS_HISTORICAL_IMPORT_COMPLETED_AT` is unset.

---

## 4. `:shared:sms:capture` — Android platform contract

Module `shared/sms/capture/`, namespace `com.iris.sms.capture`, plugin `iris.feature`.
Depends on `projects.shared.sms.parser`, `projects.shared.domain`, `projects.shared.data.core`,
`projects.shared.base`, plus `libs.androidx.work` and `libs.hilt.work`.

### 4.1 Broadcast receiver

`shared/sms/capture/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <receiver
            android:name="com.iris.sms.capture.SmsCaptureReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="100">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

```kotlin
@AndroidEntryPoint
class SmsCaptureReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: SmsCaptureCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        coordinator.captureAsync(
            messages = Telephony.Sms.Intents.getMessagesFromIntent(intent),
            onFinished = pending::finish,
        )
    }
}
```

Contract:

- **Never parses inline and never touches the database directly** — it delegates to
  `SmsCaptureCoordinator`, which is `@Singleton` and owns an application-scoped
  `CoroutineScope(SupervisorJob() + dispatchersProvider.io)`.
- Multipart parts are joined per originating address before dispatch.
- The message body is **never written to disk** — not to WorkManager, not to logs, not to Room.
- First action inside the coordinator is `SmsCaptureGate.isCapturing()`; a closed gate returns
  immediately (FR-003, FR-032, Acceptance 1.5).
- `PendingResult.finish()` is always called, including on the error path.

### 4.2 `SmsCaptureGate`

```kotlin
@Singleton
class SmsCaptureGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun isCapturing(): Boolean          // SMS_CAPTURE_ENABLED && RECEIVE_SMS granted
    fun canReadInbox(): Boolean                 // READ_SMS granted
}
```

Absent `SMS_CAPTURE_ENABLED` reads as `false`. This one default is the whole of SC-010.

### 4.3 Historical import worker

```kotlin
@HiltWorker
class SmsImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importRecentSms: ImportRecentSmsUseCase,
    private val notifier: SmsCaptureNotifier,
) : CoroutineWorker(context, params) {
    companion object {
        const val UNIQUE_NAME = "sms-historical-import"
        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_CAPTURED = "captured"
    }
}
```

Contract:

- Enqueued as `ExistingWorkPolicy.KEEP` unique work, so a second tap cannot double-import.
- `SmsInboxDataSource.readWindow(from: Instant, to: Instant, page: Int)` queries
  `Telephony.Sms.Inbox` with `selection = "date >= ? AND date <= ?"` and
  `LIMIT/OFFSET` paging of 200 — the 30-day bound is enforced **in the SQL selection**, so older
  messages are never read into the process (FR-030).
- Publishes `setProgress()` per page and `yield()`s between pages (SC-009: 5,000 messages without
  the app becoming unresponsive).
- On completion posts a summary notification with the captured count (FR-031) and writes
  `SMS_HISTORICAL_IMPORT_COMPLETED_AT`.
- Runs every message through the same `CaptureSmsUseCase` as live capture, therefore through the
  same `processed_messages` dedupe (SC-003, Edge "Duplicate delivery").

### 4.4 Notification contract

```kotlin
@Singleton
class SmsCaptureNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun notifyCaptured(pendingCount: Int)
    fun notifyImportFinished(capturedCount: Int)
    companion object {
        const val CHANNEL_ID = "sms_capture"
        const val ACTION_REVIEW = "iris.wallet.intent.action.review_captured"
    }
}
```

- Its own channel, created in `:shared:sms:capture`. It does **not** extend
  `IrisNotificationChannel` in `:temp:legacy-code` (that module is frozen).
- Content intent targets `RootActivity` with `ACTION_REVIEW`; `RootActivity` maps that action to
  `Navigation.navigateTo(SmsReviewScreen)`, requiring a new `<intent-filter>` entry in
  `app/src/main/AndroidManifest.xml` alongside the existing
  `iris.wallet.intent.action.add_transaction` filter (FR-026).
- `POST_NOTIFICATIONS` is already declared; a denial degrades to "no notification", never to
  "no capture".

---

## 5. Permission contract

Added to `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
```

| Permission | Requested when | Needed for | Degradation when denied |
|---|---|---|---|
| `RECEIVE_SMS` | User accepts the rationale in `:screen:sms-settings` | Live capture (FR-005) | Feature stays off; explanation + retry (FR-032, Acceptance 4.2) |
| `READ_SMS` | Only when the user opts into the 30-day import | Historical import (FR-030) | Import hidden; live capture unaffected |
| `POST_NOTIFICATIONS` | Already declared and requested by the app | Capture notification (FR-026) | Captured items still appear in the review list |

Requested with `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`
from the settings screen composable; the result is delivered to the ViewModel as
`OnPermissionResult`. `shouldShowRequestPermissionRationale` distinguishes `Denied` from
`PermanentlyDenied`, and the latter offers a deep link to the system app-settings page (the
`SettingsViewModel` already does this for other settings and is the pattern to copy).

**Release risk (recorded, not a blocker)**: `RECEIVE_SMS` and `READ_SMS` are Google Play restricted
permissions. IrisWallet ships as an APK via GitHub Releases
(`.github/workflows/internal_release.yml`), so no exemption is required today. Play distribution
would require gating the feature out of that variant or filing a Permissions Declaration.
