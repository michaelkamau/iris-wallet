# Phase 0 Research: Automatic SMS Transaction Capture

**Feature**: `001-sms-transaction-capture` | **Date**: 2026-07-26

**Input**: [spec.md](./spec.md), `.github/copilot-instructions.md`, `docs/Guidelines.md`, `docs/guidelines/*`

This document records the decisions taken before design, the reasoning behind each, and the
alternatives that were rejected. Everything here is grounded in the IrisWallet codebase as it
exists on branch `001-sms-transaction-capture` — no generic Android advice.

---

## Baseline facts established by reading the codebase

These are not decisions; they are constraints discovered by inspection and referenced throughout.

| Fact | Evidence |
|---|---|
| Every Gradle module — even non-Compose ones like `:shared:data:model` — applies `iris.feature` | `shared/data/model/build.gradle.kts`, `shared/base/build.gradle.kts` |
| Room DB is a single `IrisRoomDatabase` at **version 130**, `exportSchema = true`, schemas in `shared/data/core/schemas/com.iris.data.db.IrisRoomDatabase/` | `shared/data/core/src/main/java/com/iris/data/db/IrisRoomDatabase.kt`, `buildSrc/src/main/kotlin/iris.room.gradle.kts` |
| Newest migrations live in package `com.iris.data.db.migration` (older ones in the legacy `com.iris.domain.db.migration`) | `Migration129to130_LoanIncludeNote.kt` |
| `RoomTypeConverters` already converts `Instant ⇄ Long` and `UUID ⇄ String` | `shared/data/core/src/main/java/com/iris/data/db/RoomTypeConverters.kt` |
| Transactions are `PositiveValue(PositiveDouble, AssetCode)` — a zero amount is unrepresentable | `shared/data/model/.../Transaction.kt`, `Value.kt` |
| WorkManager + `HiltWorkerFactory` are already wired | `app/src/main/java/com/iris/wallet/IrisAndroidApp.kt`, `libs.androidx.work`, `libs.hilt.work` |
| The only existing notification code is `IrisNotificationChannel` / `NotificationService` in `:temp:legacy-code`, which we must not extend | `temp/legacy-code/src/main/java/com/iris/legacy/android/notification/` |
| There are **no** `BroadcastReceiver` subclasses in the repo today (widgets use Glance receivers) | `grep -rl BroadcastReceiver` returns nothing outside build output |
| `RECEIVE_SMS` / `READ_SMS` are absent from `app/src/main/AndroidManifest.xml`; `POST_NOTIFICATIONS` is present | `app/src/main/AndroidManifest.xml` |
| ViewModel tests use the Molecule-backed `viewModel.runTest(events) { }` from `:shared:ui:testing` | `shared/ui/testing/src/main/java/com/iris/ui/testing/ComposeViewModelTest.kt` |
| Paparazzi base class enforces Light/Dark via `PaparazziTheme` and a `Locale.US` `@Before` | `shared/ui/testing/.../PaparazziScreenshotTest.kt` |

---

## D1 — Where does the parsing engine live?

**Decision**: A new module **`:shared:sms:parser`** (namespace `com.iris.sms.parser`), applying
`iris.feature`, depending only on `projects.shared.data.model` and `projects.shared.base`.
It contains **no Android framework imports** — no `Context`, no `Telephony`, no `ContentResolver`.
Its public entry point is `SmsParser.parse(message: RawSmsMessage): Either<SmsParseError, ParsedMessage>`.

**Rationale**:

- The parser is the highest-risk, highest-churn part of the feature (SC-001 demands ≥95% accuracy
  over real messages, SC-004 demands zero false positives over ≥200 messages). Keeping it free of
  Android types means the whole corpus runs as plain JVM `testDebugUnitTest` in milliseconds,
  which is the gate CI already runs.
- It sits at the `data:model` layer of the existing stack (`model → data → domain → ui/screen`),
  so `:shared:domain` and `:shared:sms:capture` can both depend on it without a cycle.
- Placing it inside `:shared:data:core` was rejected: `data:core` already pulls Room, Ktor and
  DataStore, so parser tests would drag that graph along, and the module is large enough already.

**Alternatives considered**:

- *Put it in `:shared:domain`* — rejected: `:shared:domain` depends on `:shared:data:core`, which
  would make the parser corpus tests depend on Room, and the parser is not "business logic combining
  repositories" (the stated role of that module in `docs/guidelines/Architecture.md`).
- *Put it directly in the screen module* — rejected: it is needed by a background receiver that has
  no UI, and by the historical-import worker.

---

## D2 — How are per-sender parser rules structured?

**Decision**: Rules are **data, not code**. The engine is a single generic matcher; each provider
contributes one immutable `SenderRuleSet` value, contributed into the graph with a Hilt
`@IntoSet` multibinding from `SmsRuleCatalogModule`. Adding a provider or a new wording =
add one file under `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/` and one
`@Provides @IntoSet` line. **The engine is never edited.**

Shape (full signatures in [contracts/sms-parser-contract.md](./contracts/sms-parser-contract.md)):

```
SenderRuleSet
├── id: RuleSetId                      // "mpesa", "dtb", "kcb"
├── senderPatterns: ImmutableList<Regex>   // matches MPESA, DTB-KENYA, KCB, 21456…
├── exclusions: ImmutableList<ExclusionRule>   // matched FIRST; short-circuits to Left
├── messageRules: ImmutableList<MessageRule>   // ordered; first match wins
└── feeRules: ImmutableList<FeeRule>           // run on the same body, independent of the match
```

Each `MessageRule` binds *named regex groups* to fields (`amount`, `counterparty`, `date`, `time`,
`reference`) plus the candidate `DateTimeFormat`s to try — it never contains parsing logic.
Shared primitive parsers (`AmountParser`, `SmsDateTimeParser`, `CounterpartyNormalizer`,
`PromoTailStripper`) are engine-level and reused by every rule set.

**Rationale**:

- The spec's Assumptions say recognition is "rule-driven and updatable"; providers change wording
  periodically. A data-shaped catalog means a wording fix is a one-line regex change with a new
  corpus row, reviewable in isolation.
- Ordering `exclusions` before `messageRules` is what makes FR-011/FR-012 and Edge Case
  "Non-transactional messages from the same sender" cheap: an OTP, a loan advert or a
  `FAILED`/`REVERSED` message is rejected before any field extraction is attempted.
- Named groups + declared date formats keep every rule readable at a glance and make the
  table-driven test corpus map 1:1 onto rules.

**Alternatives considered**:

- *One `MpesaParser` / `DtbParser` class per provider implementing a `SenderParser` interface* —
  rejected: each class would re-implement amount/date/counterparty handling, and the shared
  edge cases (two-digit years, `Ksh` vs `KES`, thousands separators, EAT) would drift between them.
- *A single mega-regex per provider* — rejected: unmaintainable and impossible to attribute a
  failure to a wording.
- *ML / heuristic parser* — explicitly out of scope per the spec's Assumptions.

**Deferred (documented, not built)**: the `SenderRuleSet` data shape is deliberately expressible as
`@Serializable` DTOs (`String` patterns rather than `Regex` literals) so a future
`AssetRuleCatalogSource` can load `assets/sms-rules/*.json` and ship pattern updates without a code
change. Phase 1 ships the compiled-in catalog only; the `RuleCatalogSource` interface is introduced
now so the swap is additive.

---

## D3 — Error type: `Either` all the way down

**Decision**: Every fallible step returns `Either<E, A>` with a purpose-built sealed error type.
Nothing in this feature throws. Three error ADTs:

- `SmsParseError` — everything the parser can reject (no rule for sender, no matching pattern,
  excluded by rule, missing required field, invalid amount, invalid date/time, invalid value).
- `SmsCaptureError` — everything `CaptureSmsUseCase` can reject (capture disabled, permission
  missing, sender not configured, sender disabled, already processed, parse failure wrapping
  `SmsParseError`, persistence failure).
- `ConfirmCaptureError` — everything blocking commit to the ledger (missing account, captured item
  gone, category creation failed, persistence failure).

**Rationale**: `docs/guidelines/Error-Handling.md` is unambiguous ("we **do not throw**"). A parser
is the canonical `Either` use case: a message that does not match is a *normal, expected* outcome
(most SMS on a Kenyan phone are not transactions), not an exception. Distinguishing
`ExcludedByRule` from `NoMatchingPattern` also gives us the observability to hit SC-001/SC-004 —
we can count "rejected on purpose" separately from "we do not understand this yet" in Timber logs
(hashes only, never bodies).

**Alternatives considered**: nullable returns (`ParsedMessage?`) — rejected, loses the reason, and
FR-013 requires "skipped rather than guessed at" to be *auditable*. Kotlin `Result` — rejected,
`Throwable`-constrained, and the repo standard is Arrow.

---

## D4 — Pending items: separate Room tables, never the `transactions` table

**Decision**: Captured items live in **four new tables of the existing `IrisRoomDatabase`**
(`financial_senders`, `captured_transactions`, `processed_messages`, `counterparty_categories`),
bumping the DB to **version 131** with a hand-written
`Migration130to131_SmsCapture`. Nothing is written to `transactions` until the user confirms.

**Rationale**:

- Resolved design decision #1 requires pending items to have **zero** effect on balances, budgets,
  reports and search. Every one of those readers goes through `TransactionDao` /
  `TransactionRepository` over the `transactions` table. Keeping captured rows in their own tables
  gives that guarantee *structurally* — there is no flag anyone can forget to filter on. This is
  the "impossible states unrepresentable" rule from `docs/guidelines/Data-Modeling.md` applied to
  the schema.
- It also satisfies FR-033 for free: once confirmed, the row inserted into `transactions` is
  byte-identical in shape to a manually entered one — there is no `source = SMS` column anywhere,
  so reports/export/search cannot tell them apart.
- Reusing the single existing database (rather than a second `RoomDatabase`) keeps the confirm
  step — delete captured rows + insert transactions — inside one Room transaction, which is what
  makes SC-003 (zero duplicates) achievable.

**Alternatives considered**:

- *Add `isPending`/`source` columns to `TransactionEntity`* — rejected. It would require auditing
  and editing every existing query in `TransactionDao`, `CalcWalletBalanceAct`, budgets, reports,
  search and CSV export; one missed query silently corrupts a user's balance. It also violates
  FR-033 by making captured transactions distinguishable forever.
- *A second `RoomDatabase` file* — rejected: no cross-database transaction, so confirm could
  half-apply; and `iris.room` exports schemas per module, adding a second schema surface for no gain.
- *DataStore/JSON for pending items* — rejected: needs querying, ordering and dedupe lookups.

**Backup**: the new tables are deliberately **not** added to `BackupDataUseCase`
(`shared/data/core/src/main/java/com/iris/data/backup/`). Un-reviewed captured data is transient and
privacy-sensitive; only confirmed transactions belong in an export.

---

## D5 — Live capture while the app is closed: `goAsync()` receiver, not WorkManager

**Decision**: `SmsCaptureReceiver : BroadcastReceiver` (`@AndroidEntryPoint`) declared in
`shared/sms/capture/src/main/AndroidManifest.xml` for `android.provider.Telephony.SMS_RECEIVED`,
guarded with `android:permission="android.permission.BROADCAST_SMS"`. It performs **no parsing
itself**: it calls `goAsync()`, hands `(sender, body, receivedAt)` to an application-scoped
`SmsCaptureCoordinator` coroutine, and calls `PendingResult.finish()` when the use case returns.
Message bodies are **never written to disk**.

**Rationale**:

- FR-005 requires capture with the app not running; a manifest-registered receiver is the only
  mechanism that starts the process for an incoming SMS on min-sdk 28.
- SC-005 requires the payment to be available within 10 seconds. Parse + 2–3 Room writes is
  single-digit milliseconds, comfortably inside a receiver's window; enqueuing WorkManager would
  add scheduling latency for no benefit.
- FR-006 / the "everything stays on the device" assumption: handing the body to WorkManager would
  serialise the **raw SMS text** into `androidx.work`'s own SQLite database, where it persists until
  pruned. Processing in-memory means only extracted facts are ever persisted.
- Multipart messages are reassembled with `Telephony.Sms.Intents.getMessagesFromIntent(intent)` and
  joined per originating address before being handed on.

**Alternatives considered**:

- *Enqueue an expedited `OneTimeWorkRequest` carrying the body* — rejected on the privacy ground
  above; the durability it buys is already covered by the 30-day import as a backstop.
- *A foreground `Service`* — rejected: unnecessary, user-visible, and battery-hostile for work
  measured in milliseconds.
- *`ContentObserver` on the SMS provider* — rejected: requires a live process, so it fails FR-005.

**Failure mode accepted and documented**: if the process is killed mid-parse, that one message is
lost until the user runs the historical import. This is acceptable because nothing is silently
wrong — the ledger simply has no entry, exactly as before the feature existed.

---

## D6 — Historical import: a bounded, chunked `CoroutineWorker`

**Decision**: `SmsImportWorker : CoroutineWorker` (`@HiltWorker`, enqueued as unique work
`sms-historical-import`) queries `Telephony.Sms.Inbox` through `ContentResolver` with a selection of
`date >= :windowStart`, where `windowStart = now - 30 days` (constant
`HistoricalImportWindow.DAYS = 30`). It reads in pages of 200 rows, calls `CaptureSmsUseCase` per
row, publishes `setProgress()` after each page, and posts a completion notification with the count.

**Rationale**:

- FR-030 caps the window at 30 days and forbids reading older messages — enforcing it in the
  **SQL selection**, not in a Kotlin filter, means older messages are never even read into the
  process, which is the strongest privacy posture available.
- FR-031 / SC-009 (5,000 messages, app stays responsive): WorkManager runs off the main thread and
  survives the user leaving the screen; paging with `yield()` between pages keeps memory flat and
  lets cancellation land promptly.
- Unlike live capture, the worker's input is just two timestamps — **no message content goes into
  the WorkManager database**, so D5's privacy objection does not apply here.
- Dedupe against live capture (Edge Case "Duplicate delivery", SC-003) is free: both paths funnel
  through the same `CaptureSmsUseCase` and therefore the same `processed_messages` check.

**Alternatives considered**: doing the import in the ViewModel's `viewModelScope` — rejected, dies
with the screen and violates FR-031. A `Paging3` source — rejected as over-engineering for a
one-time job.

---

## D7 — Duplicate detection: two distinct, independently testable mechanisms

**Decision**:

1. **Hard dedupe (FR-028, silent)** — a `processed_messages` row keyed by `fingerprint TEXT
   PRIMARY KEY`, computed by `MessageFingerprint.of(message, parsed)`:
   - if a provider reference was extracted → `"${ruleSetId}:${reference}"` (uppercased);
   - otherwise → `"h:" + sha256(normalisedSender + '\u0000' + normalisedBody)`, truncated to 32 hex
     chars. The body is hashed, never stored.
   A row exists for every processed message including *ignored* and *dismissed* ones, with an
   `outcome` column, so FR-025 ("dismissed items MUST NOT reappear") falls out of the same table.
2. **Soft duplicate flag (FR-029, user-visible)** — `DetectDuplicateTransactionUseCase` looks for an
   existing `transactions` row with the same account, the same amount, and a `dateTime` on the same
   local calendar day; if found, the captured item carries `duplicateOf: TransactionId?` and the
   review screen shows a "possible duplicate" affordance. It never auto-discards.

**Rationale**: the two requirements are genuinely different — FR-028 is machine-certain and must be
silent, FR-029 is a heuristic over user-entered data and must therefore be a *decision the user
makes*. Conflating them would either drop real transactions or nag on every legitimate repeat
payment. Hashing rather than storing the body honours FR-006 while still making re-delivery of a
reference-less message detectable.

---

## D8 — Date/time: fixed `Africa/Nairobi`, resolved at parse time

**Decision**: `SmsDateTimeParser` interprets every timestamp in the fixed zone
`ZoneId.of("Africa/Nairobi")` (UTC+03:00, no DST, historically stable), whether or not the message
says `EAT`, and returns a `java.time.Instant`. Two-digit years resolve via
`DateTimeFormatterBuilder.appendValueReduced(YEAR, 2, 2, baseYear)` with `baseYear = 2000`, so
`25/7/26` → 2026. When a message carries a date but no time, the parser uses the **message receipt
time-of-day** if the receipt falls on the same Nairobi calendar day, otherwise `12:00` local, and
records which was used.

Supported formats, all present in the spec's real samples:
`25/7/26`, `25/07/2026`, `25 Jul 2026`, `2026-07-24 07:19:54 PM`, times `7:50 PM`, `17:41`, `17:41 EAT`.

**Rationale**: the spec's Edge Case "Time zones" requires the transaction to land on the correct
*local* day "regardless of device time-zone settings" — deriving the zone from the device would put
a 7:50 PM Nairobi payment on the previous day for a traveller. All senders in scope are Kenyan, so a
fixed zone is both correct and simpler than a per-rule-set zone. The `Instant` return type matches
`Transaction.time` exactly, so no conversion happens later.

**Alternatives considered**: `TimeProvider.getZoneId()` (device zone) — rejected per the above.
Storing a `LocalDateTime` — rejected: the core model uses `Instant`, and
`docs/guidelines/Data-Modeling.md` explicitly calls out `Instant // <-- always in UTC`.

---

## D9 — Amounts: parse to `PositiveDouble`, so zero cannot survive

**Decision**: `AmountParser.parse(raw): Either<SmsParseError.InvalidAmount, PositiveDouble>` strips
the currency marker (`Ksh`, `KSh`, `KES`, before or after the number) and thousands separators, then
goes through the existing `PositiveDouble.from(...)` `Exact` companion. Currency is always
`AssetCode.unsafe("KES")` for this release, exposed as `KenyanShilling` in the parser module.

**Rationale**: FR-012 and the spec's "Amounts are positive by construction" assumption are enforced
by the *type*, not by an `if`. `Ksh0.00` and `Transaction cost, Ksh0.00` produce
`Either.Left(InvalidAmount)` from `PositiveDouble` itself, which the fee extractor treats as
"no fee" — so FR-016's ">0" condition and Acceptance Scenario 2.3 need no extra code path.

---

## D10 — Fees: one row shape, an ADT discriminator, one Room table

**Decision**: A captured fee is a `CapturedTransaction` whose `kind` is
`CapturedKind.Fee(parent: CapturedTransactionId?, tax: PositiveDouble?)`; a principal is
`CapturedKind.Principal(direction: MoneyDirection)`. Both live in `captured_transactions`.
`parent == null` is exactly the FR-020 standalone-fee case. The tax figure (Acceptance Scenario 2.2,
FR-019) is a field **on the fee**, never a third row.

Confirming a principal that has a linked fee commits **two** `Expense` rows in one Room
transaction: the principal with the user's category, the fee with the transaction-cost category.
Dismissing or deleting a principal offers to remove the linked fee (FR-018).

**Rationale**: modelling `Fee` as a *variant of* the captured item (rather than a separate
`captured_fees` table) means the review screen, the dedupe path, the account-mapping rule and the
confirm path all handle it with the same code, and a standalone fee is not a special case. The
sealed `kind` keeps the impossible combination "principal with a tax component" unrepresentable.

**Alternatives considered**: a separate `captured_fees` table mirroring the spec's Key Entities
1:1 — rejected: it duplicates every column, doubles the DAO surface, and makes FR-020 (a fee with no
principal) an orphan row requiring its own review UI.

---

## D11 — The transaction-cost category: created once, id remembered in DataStore

**Decision**: `EnsureTransactionCostCategoryUseCase` returns `Either<String, CategoryId>`. It reads
`DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID` (a new `stringPreferencesKey`); if present and the
category still exists via `CategoryRepository.findById`, it returns it; otherwise it creates a
`Category` named "Transaction costs" via `CategoryRepository.save` and stores the new id. It is
called lazily — on the **first fee confirmation**, not at feature-enable time.

**Rationale**: FR-017 requires exactly one category, created once and reused. Keying off the id
(not the name) means the user can rename and recolour it — which the spec's Assumptions explicitly
promise — without the app creating a duplicate. Lazy creation means a user who never incurs a fee
never gets a stray category, and it keeps `EnsureTransactionCostCategoryUseCase` off the hot
capture path.

**Alternatives considered**: look up by name each time — rejected, breaks on rename and on
localisation. Seed it during onboarding/migration — rejected, pollutes every existing user's
category list including those who never enable the feature (FR-032/SC-010).

---

## D12 — No account mapping: capture, hold, block confirm

**Decision**: `CapturedTransaction.account` is `AccountId?`. A message from a configured financial
sender with `financial_senders.accountId IS NULL` is still captured, stored with a null account, and
surfaced with `ReviewStatus.NeedsAccount`. `ConfirmCapturedTransactionUseCase` returns
`Either.Left(ConfirmCaptureError.MissingAccount)` for such an item, and the review screen disables
its confirm affordance until an account is picked. **There is no default-account fallback anywhere
in the code.**

**Rationale**: this is resolved design decision #2 and FR-027a, verbatim. The nullable field plus a
derived `ReviewStatus` ADT keeps a single source of truth; a `defaultAccountId` setting would be a
foot-gun that silently posts a bank payment to the wrong balance.

**Note on the ADT**: `ReviewStatus { NeedsAccount | PossibleDuplicate(TransactionId) | ReadyToConfirm }`
is **derived** by a pure function `CapturedTransaction.reviewStatus()`, not stored. Storing it would
allow the stored status and the stored `account` to disagree.

---

## D13 — Two new screen modules, not one, and no changes to legacy screens

**Decision**:

- **`:screen:sms-review`** (`com.iris.sms.review`) — the pending-review list and per-item
  enrichment (US1, US2, US3). Reached from `SmsReviewScreen` (a new `data object` in
  `shared/ui/navigation/.../Screens.kt`) and from the capture notification.
- **`:screen:sms-settings`** (`com.iris.sms.settings`) — the explanation/consent surface, master
  toggle, permission request, per-sender mapping and enable/disable, and the one-time import
  trigger (US4, US5). Reached from `SmsCaptureSettingsScreen`.

Both leave `Screen.isLegacy` at its `false` default. The only edit to an existing screen is one new
row in `screen/settings/src/main/java/com/iris/settings/SettingsScreen.kt` navigating to
`SmsCaptureSettingsScreen`.

**Rationale**: the repo convention is strictly one Gradle module per screen
(`settings.gradle.kts` lists 21 `:screen:*` modules). Review and settings have disjoint
view-states, disjoint dependencies (settings needs the permission launcher and `AccountRepository`;
review needs `CategoryRepository` and the confirm use cases) and land in different user stories at
different priorities — merging them would make US1's MVP drag in US4's permission plumbing.
Keeping `:screen:settings` almost untouched protects SC-010.

**Explicitly deferred**: a pending-count badge on the home screen. `:screen:home` and `:screen:main`
are legacy (`isLegacy = true`, backed by `:temp:old-design`); adding to them conflicts with the
"don't add to `:temp:*`" rule. FR-021a's "show how many items are awaiting review" is satisfied for
P1 by the notification and the count shown in the Settings row; a home badge is listed as a
follow-up once `:screen:home` is migrated.

---

## D14 — Permissions: declared in the app manifest, requested from the settings screen

**Decision**:

- `<uses-permission android:name="android.permission.RECEIVE_SMS" />` and
  `<uses-permission android:name="android.permission.READ_SMS" />` are added to
  `app/src/main/AndroidManifest.xml`, alongside the existing permission block.
- The `<receiver>` is declared in the new library manifest
  `shared/sms/capture/src/main/AndroidManifest.xml` and merges into the app.
- Both are requested at runtime from `:screen:sms-settings` via
  `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`,
  **after** a rationale panel that states what is read, what is extracted, and that nothing leaves
  the device (FR-002). `READ_SMS` is requested only when the user opts into the historical import
  (US5), so a P1-only user grants `RECEIVE_SMS` alone.
- `SmsCaptureGate.isCapturing()` = feature toggle ON **and** `RECEIVE_SMS` granted. The receiver's
  first statement consults the gate and returns immediately when it is closed (FR-003, FR-032).
  Revocation mid-stream leaves already-captured items intact (Edge Case) because they are ordinary
  rows in `captured_transactions`.

**Rationale**: keeping permissions in the app manifest matches the existing single, greppable
permission list; keeping the receiver in the feature's own library manifest keeps the module
self-contained and lets `:app` stay thin wiring. Splitting the two requests limits the blast radius
of the more sensitive `READ_SMS`.

**Risk recorded (not a blocker)**: `RECEIVE_SMS`/`READ_SMS` are Google Play *restricted*
permissions. IrisWallet ships as an APK via GitHub Releases
(`.github/workflows/internal_release.yml`), so no store exemption is needed today. If Play
distribution is ever pursued, this feature must either be gated out of the store build variant or
go through the Permissions Declaration Form. Carried into plan.md's risk table.

---

## D15 — Test strategy

**Decision**, mapped onto the gates CI already runs:

| Layer | Where | How |
|---|---|---|
| Parser corpus | `shared/sms/parser/src/test/.../SmsParserCorpusTest.kt` | Table-driven over `SmsCorpus` — every real sample message from spec.md plus every negative (OTP, balance enquiry, loan advert, `FAILED`, `REVERSED`, `Ksh0.00`, promo tail) — asserting the full `Either` result with Kotest `shouldBe` |
| Parser units | same module | `AmountParserTest`, `SmsDateTimeParserTest` (all six date formats + two-digit-year pivot + EAT), `CounterpartyNormalizerTest` (`james Kinyua Mwangi9.` → `James Kinyua Mwangi`), `PromoTailStripperTest` |
| Fingerprint | same module | `MessageFingerprintTest` — same message twice → same fingerprint; reference-bearing vs reference-less paths |
| Use cases | `shared/domain/src/test/.../usecase/sms/` | MockK'd repositories, Given/When/Then, backticked names; covers capture gating, dedupe, fee linkage, confirm-with-fee, missing-account rejection |
| ViewModels | `screen/sms-review/src/test/`, `screen/sms-settings/src/test/` | `ComposeViewModelTest` + `viewModel.runTest(events = listOf(...)) { ... }` |
| Screenshots | `screen/sms-review/src/test/.../SmsReviewScreenshotTest.kt` | `PaparazziScreenshotTest` + `@RunWith(TestParameterInjector::class)` + `@TestParameter theme: PaparazziTheme`; **static fixtures with hard-coded `Instant`s and pre-formatted strings** — no `now()` anywhere |
| Room migration | `shared/data/core/src/androidTest/.../IrisRoomDatabaseMigrationTest.kt` | New `migrate130to131_SmsCapture()` following the existing `migrate129to130_LoanIncludeNote` pattern; run via `./scripts/integrationTests.sh` |
| Round-trip persistence | `shared/data/core/src/androidTest/` | Save → read → confirm a captured item with a fee; assert two `transactions` rows and zero `captured_transactions` rows |

**Rationale**: the parser corpus *is* SC-001 and SC-004 expressed as an executable gate — the
success criteria are numeric and only a table-driven corpus can hold the line as rules are edited.
Everything else follows `docs/guidelines/Unit-Testing.md` and the existing fixtures
(`FakeDao`, `TestTimeConverter`, `:shared:data:model-testing`) rather than mocking everything.

---

## Open items carried into the plan

None block Phase 1. Two things are consciously deferred and recorded in plan.md:

1. **Remote/asset-updatable rule catalog** — data shape ready, loader deferred (D2).
2. **Home-screen pending badge** — blocked on the `:screen:home` Material3 migration (D13).
