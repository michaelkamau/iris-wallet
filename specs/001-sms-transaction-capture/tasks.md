# Tasks: Automatic SMS Transaction Capture

**Input**: Design documents from `/specs/001-sms-transaction-capture/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Test tasks ARE included. The spec's success criteria SC-001, SC-003, SC-004 and SC-009 are
only meaningful as executable gates, and research.md D15 defines the test strategy per layer. The
parser corpus in particular *is* SC-001 and SC-004.

**Organization**: Tasks are grouped by user story so each priority level is an independently
completable, independently testable slice. **Phase 3 + Phase 4 (US1 + US2, both P1) together form a
shippable MVP.**

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel — different files, no dependency on an incomplete task
- **[Story]**: `[US1]`…`[US5]`; Setup, Foundational and Polish tasks carry no story label
- Every task names the exact file it creates or modifies, relative to the repository root

## Path Conventions (this repository)

- Gradle modules are grouped `:app`, `:screen:*`, `:shared:*`, `:widget:*`, `:ci-actions:*`
- `:shared:data:model` uses `src/main/kotlin/`; every other module uses `src/main/java/`
- Unit tests live in `src/test/java/`, instrumented tests in `src/androidTest/java/`
- Every module applies the `iris.feature` convention plugin and is wired with typesafe project
  accessors (`projects.shared.smsParser`)
- **Never add code to `:temp:legacy-code` or `:temp:old-design`**

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the four new Gradle modules from plan.md §"Project Structure" and wire them into
the build, so every later task has somewhere to land. No feature logic here.

- [X] T001 [P] Create `shared/sms/parser/build.gradle.kts` applying `id("iris.feature")`, `android { namespace = "com.iris.sms.parser" }`, with `api(projects.shared.data.model)`, `implementation(projects.shared.base)`, `testImplementation(projects.shared.data.modelTesting)`. No Android framework dependency and no version literal — everything resolves from `gradle/libs.versions.toml`.
- [X] T002 [P] Create `shared/sms/capture/build.gradle.kts` (`iris.feature`, namespace `com.iris.sms.capture`) depending on `projects.shared.smsParser`, `projects.shared.domain`, `projects.shared.data.core`, `projects.shared.base`, `libs.androidx.work`, `libs.bundles.hilt` (which already carries `hilt-work`), plus `testImplementation(libs.bundles.testing)`; and create the stub library manifest `shared/sms/capture/src/main/AndroidManifest.xml` with an empty `<manifest><application/></manifest>`.
- [X] T003 [P] Create `screen/sms-review/build.gradle.kts` (`iris.feature`, namespace `com.iris.sms.review`) depending on `projects.shared.base`, `projects.shared.data.core`, `projects.shared.domain`, `projects.shared.ui.core`, `projects.shared.ui.navigation`, with `testImplementation(projects.shared.ui.testing)`. **Do not** add `projects.temp.legacyCode` / `projects.temp.oldDesign` — this module stays clean (research.md D13).
- [X] T004 [P] Create `screen/sms-settings/build.gradle.kts` (`iris.feature`, namespace `com.iris.sms.settings`) with the same dependency set as T003 plus `implementation(projects.shared.smsCapture)` (needed to enqueue `SmsImportWorker` and read `SmsCaptureGate`).
- [X] T005 Register the four modules in `settings.gradle.kts`: `include(":screen:sms-review")`, `include(":screen:sms-settings")`, `include(":shared:sms:capture")`, `include(":shared:sms:parser")`, each in the existing alphabetical position. (depends on T001–T004)
- [X] T006 Add `implementation(projects.screen.smsReview)` and `implementation(projects.screen.smsSettings)` to the `dependencies` block of `app/build.gradle.kts`, in the existing alphabetical `projects.screen.*` run. (depends on T005)
- [X] T007 Add `<uses-permission android:name="android.permission.RECEIVE_SMS" />` and `<uses-permission android:name="android.permission.READ_SMS" />` to the existing permission block in `app/src/main/AndroidManifest.xml` (contracts/ui-and-platform-contract.md §5). Declaring them does not grant them — FR-001's default-off is a DataStore concern (T048).
- [X] T008 **Checkpoint — Phase 1**: run `./gradlew :shared:sms:parser:assembleDebug :shared:sms:capture:assembleDebug :screen:sms-review:assembleDebug :screen:sms-settings:assembleDebug` and `./gradlew detekt`. All four modules must configure, build and pass detekt while still empty. Confirms the module graph in plan.md §"Module graph" is acyclic.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The domain types, the Room schema at version 131, the DataStore keys and the parsing
*engine* — everything US1…US5 share. Provider rule sets are deliberately **not** here; they belong to
the stories that need them.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### 2a. Domain model — `:shared:data:model`

- [X] T009 [P] Create `shared/data/model/src/main/kotlin/com/iris/data/model/sms/SmsPrimitives.kt` with `CapturedTransactionId`, `SenderId`, `ProviderReference`, `MessageFingerprint`, `CounterpartyKey` and `RuleSetId` as `@JvmInline value class`es with Arrow `Exact` companions exactly as specified in data-model.md §1.1. `RuleSetId` lives here (not in the parser) because `FinancialSender` stores it.
- [X] T010 [P] Create `shared/data/model/src/main/kotlin/com/iris/data/model/sms/CapturedTransaction.kt` with `MoneyDirection`, `CaptureOrigin`, the sealed `CapturedKind { Principal(direction) | Fee(parent, tax) }`, the `CapturedTransaction` data class (amount is `PositiveDouble`, `account`/`category`/`duplicateOf` nullable), the derived `ReviewStatus` ADT, the pure `CapturedTransaction.reviewStatus()` function and `CapturedEntry(principal, fee)` — data-model.md §1.2. (FR-008, FR-012, FR-019, FR-020, FR-027a)
- [X] T011 [P] Create `shared/data/model/src/main/kotlin/com/iris/data/model/sms/FinancialSender.kt` per data-model.md §1.3 — `id: SenderId`, `displayName`, `ruleSet: RuleSetId?`, `account: AccountId?`, `enabled: Boolean`. (FR-003, FR-004, FR-027, FR-027a)
- [X] T012 [P] Create `shared/data/model/src/main/kotlin/com/iris/data/model/sms/CounterpartyCategory.kt` — `counterparty: CounterpartyKey`, `category: CategoryId`, `updatedAt: Instant`. (FR-024)
- [X] T013 [P] Create `shared/data/model/src/main/kotlin/com/iris/data/model/sms/ProcessedMessage.kt` with `ProcessedOutcome { Captured, Ignored, Dismissed }` and the `ProcessedMessage` record holding **no** message content. (FR-006, FR-025, FR-028)
- [X] T014 [P] Create `shared/data/model/src/test/java/com/iris/data/model/sms/SmsPrimitivesTest.kt` — JUnit4 + Kotest `shouldBe`, Given/When/Then, backticked names. Assert `SenderId.from("  m-pesa ")` normalises to `M-PESA`, `ProviderReference` rejects non-alphanumerics and out-of-range lengths, `RuleSetId` rejects non-slugs, and `CounterpartyKey.from("Frank Inn Kikuyu")` equals the key for `"FRANK INN KIKUYU"`. (depends on T009)
- [X] T015 [P] Create `shared/data/model/src/test/java/com/iris/data/model/sms/CapturedTransactionTest.kt` asserting `reviewStatus()` returns `NeedsAccount` when `account == null`, `PossibleDuplicate` when `duplicateOf != null`, and `ReadyToConfirm` otherwise — and that `NeedsAccount` wins over `PossibleDuplicate`. (depends on T010, FR-027a, FR-029)

### 2b. Parsing engine — `:shared:sms:parser` (Android-free)

- [X] T016 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/RawSmsMessage.kt` and `.../ParsedMessage.kt` with `RawSmsMessage(sender, body, receivedAt)`, `ParsedMessage(ruleSet, rule, principal, fee, time, counterparty, reference)`, `ParsedAmountLine` and `ParsedFee` per data-model.md §2. (depends on T009, T010)
- [X] T017 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/SmsParseError.kt` with the sealed `SmsParseError` (`NoRuleForSender`, `NoMatchingPattern`, `ExcludedByRule`, `MissingField`, `InvalidAmount`, `InvalidDateTime`, `InvalidValue`) plus `ExclusionReason` and `RequiredField` enums — contracts/sms-parser-contract.md §2. (FR-013)
- [X] T018 Create `shared/sms/parser/src/main/java/com/iris/sms/parser/model/RuleModel.kt` with `SenderRuleSet`, `ExclusionRule`, `MessageRule`, `FieldBindings`, `FeeRule`, `GroupName` and `RuleId` (contracts/sms-parser-contract.md §3). All collections are `ImmutableList`; pattern fields are `Regex` but the shape stays serialisable-friendly (research.md D2). (depends on T017)
- [X] T019 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/primitive/AmountParser.kt` — strips `Ksh`/`KSh`/`KES` (prefix or suffix) and thousands separators, then delegates to the existing `PositiveDouble.from(...)`, returning `Either<String, PositiveDouble>` so `Ksh0.00` is a `Left` by construction. Expose `KenyanShilling = AssetCode.unsafe("KES")` here. (research.md D9, FR-009, FR-012)
- [X] T020 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/primitive/SmsDateTimeParser.kt` with the `SmsDateTimeFormat` enum (`d/M/yy`, `dd/MM/yyyy`, `d MMM yyyy`, `yyyy-MM-dd hh:mm:ss a`, `h:mm a`, `HH:mm`), the fixed `ZONE = ZoneId.of("Africa/Nairobi")`, two-digit-year pivot via `appendValueReduced(YEAR, 2, 2, 2000)`, `EAT` suffix stripping, and the date-without-time fallback rule from research.md D8. Returns `Either<String, Instant>`. (FR-010)
- [X] T021 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/primitive/CounterpartyNormalizer.kt` implementing the five-step normalisation contract in contracts/sms-parser-contract.md §4 plus `key(name): CounterpartyKey`. (FR-014, FR-024)
- [X] T022 [P] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/primitive/PromoTailStripper.kt` — `strip(body, markers)` truncates at the first promo marker match. (FR-011)
- [X] T023 [P] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/AmountParserTest.kt` covering `Ksh1,350.00`, `KES 540.25`, `6000.00 KES`, `KES 13,000.00`, and `Ksh0.00` → `Left`. (depends on T019)
- [X] T024 [P] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/SmsDateTimeParserTest.kt` asserting `25/7/26` + `7:50 PM` → `2026-07-25T16:50:00Z`, `25 Jul 2026` + `17:41 EAT` → `2026-07-25T14:41:00Z`, `25/07/2026` with no time, `2026-07-24 07:19:54 PM`, and that the result is independent of the JVM default time zone. (depends on T020, FR-010)
- [X] T025 [P] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/CounterpartyNormalizerTest.kt` asserting `james Kinyua Mwangi9.` → `James Kinyua Mwangi`, `GITHUB, INC. SAN FRANCISCO CA` → `Github, Inc.`-style output, `KCB` stays `KCB`, masked fragments `5XXXXX5001` / `254****956` are dropped, and an all-noise input is `Left`. (depends on T021, FR-014)
- [X] T026 [P] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/PromoTailStripperTest.kt` asserting `Download My OneApp on https://…` and `Dial *522#` tails are removed and the retained head is unchanged. (depends on T022, FR-011)
- [X] T027 Create `shared/sms/parser/src/main/java/com/iris/sms/parser/MessageFingerprints.kt` — `of(message, parsed)` returns `"<ruleSetId>:<REFERENCE>"` when a reference exists, else `"h:" + sha256(normalisedSender + '\u0000' + strippedBody).take(32)`. The preimage must exclude `receivedAt`. (depends on T009, T016, FR-006, FR-028)
- [X] T028 [P] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/MessageFingerprintsTest.kt` asserting: same message twice → same fingerprint; live-capture and inbox-import `receivedAt` values → same fingerprint; reference-bearing vs reference-less paths differ in prefix; the body never appears in the output. (depends on T027, SC-003)
- [X] T029 Create `shared/sms/parser/src/main/java/com/iris/sms/parser/SmsParser.kt` with the `SmsParser` interface and `@Singleton class RuleDrivenSmsParser @Inject constructor(catalog, amountParser, dateTimeParser, counterpartyNormalizer, promoStripper)` implementing the seven-step pipeline verbatim from contracts/sms-parser-contract.md §1 — sender resolution → promo strip → exclusions → first-matching message rule → principal extraction → independent fee extraction → assembly. Never throws; nothing partial is returned. (depends on T016–T022, FR-007, FR-013)
- [X] T030 Create `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/RuleCatalogSource.kt` (`interface RuleCatalogSource` + `@Singleton CompiledRuleCatalogSource @Inject constructor(ruleSets: Set<@JvmSuppressWildcards SenderRuleSet>)`) and `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/SmsRuleCatalogModule.kt` as an `@InstallIn(SingletonComponent::class) object` with the `@Binds`/`@Provides` for the source and **no** rule sets yet. (depends on T018, T029, research.md D2)
- [X] T031 Create the corpus harness: `shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/SmsCorpus.kt` with `CorpusCase(name, sender, body, receivedAt, expected)` and the sealed `Expectation { Parsed(...) | Rejected(reason) }` (contracts/sms-parser-contract.md §6, fixed `Instant` literals, never `Instant.now()`), plus `shared/sms/parser/src/test/java/com/iris/sms/parser/SmsParserCorpusTest.kt` which iterates every registered case and asserts the whole `Either` with Kotest `shouldBe`. Starts green over an empty corpus. (depends on T029, T030)

### 2c. Persistence — `:shared:data:core` (Room 130 → 131)

- [X] T032 [P] Create `shared/data/core/src/main/java/com/iris/data/db/entity/FinancialSenderEntity.kt` — table `financial_senders`, PK `senderId TEXT`, columns per contracts/persistence-contract.md §1. **Not** `@Serializable` (excluded from backup by design).
- [X] T033 [P] Create `shared/data/core/src/main/java/com/iris/data/db/entity/CapturedTransactionEntity.kt` — table `captured_transactions`, PK `id`, with `kind`, `direction`, `parentId`, `taxAmount`, `amount`, `assetCode`, `dateTime`, `counterparty`, `reference`, `accountId`, `categoryId`, `description`, `duplicateOfTransactionId`, `capturedAt`, `origin`, and indices on `parentId` and `dateTime`. No foreign keys (consistent with every existing table).
- [X] T034 [P] Create `shared/data/core/src/main/java/com/iris/data/db/entity/ProcessedMessageEntity.kt` — table `processed_messages`, PK `fingerprint TEXT`, `senderId`, `outcome`, `processedAt`, index on `processedAt`. Holds no body and no hash preimage.
- [X] T035 [P] Create `shared/data/core/src/main/java/com/iris/data/db/entity/CounterpartyCategoryEntity.kt` — table `counterparty_categories`, PK `counterpartyKey TEXT`, `categoryId`, `updatedAt`.
- [X] T036 [P] Create the four read DAOs in `shared/data/core/src/main/java/com/iris/data/db/dao/read/`: `CapturedTransactionDao.kt` (`findAll`, `findById`, `findByParentId`, `pendingCount(): Flow<Int>`), `FinancialSenderDao.kt` (`findAll`, `findEnabledById`), `ProcessedMessageDao.kt` (`exists`), `CounterpartyCategoryDao.kt` (`findByKey`) — signatures verbatim from contracts/persistence-contract.md §2. (depends on T032–T035)
- [X] T037 [P] Create the four write DAOs in `shared/data/core/src/main/java/com/iris/data/db/dao/write/`: `WriteCapturedTransactionDao.kt`, `WriteFinancialSenderDao.kt`, `WriteProcessedMessageDao.kt`, `WriteCounterpartyCategoryDao.kt`, mirroring `WriteTransactionDao` (`@Insert(onConflict = REPLACE) save/saveMany`, `deleteById`, `deleteByParentId`, `deleteAll`). (depends on T032–T035)
- [X] T038 Create `shared/data/core/src/main/java/com/iris/data/db/migration/Migration130to131_SmsCapture.kt` in package `com.iris.data.db.migration`, following the exact shape of the neighbouring `Migration129to130_LoanIncludeNote.kt`, executing the four `CREATE TABLE IF NOT EXISTS` statements and three `CREATE INDEX IF NOT EXISTS` statements from contracts/persistence-contract.md §1 unchanged. (depends on T032–T035)
- [X] T039 Modify `shared/data/core/src/main/java/com/iris/data/db/IrisRoomDatabase.kt`: add the four entity classes to `@Database(entities = [...])`, change `version = 130` to `version = 131`, add the eight `abstract val` DAO accessors, and append `Migration130to131_SmsCapture()` to the list returned by `migrations()`. No existing entity, DAO or query may be touched. (depends on T036, T037, T038)
- [X] T040 Modify `shared/data/core/src/main/java/com/iris/data/di/RoomDbModule.kt` to add the eight `@Provides` functions returning the new read and write DAOs from `IrisRoomDatabase`. (depends on T039)
- [X] T041 Run `./gradlew :shared:data:core:assembleDebug` and commit the generated `shared/data/core/schemas/com.iris.data.db.IrisRoomDatabase/131.json`. The exported schema is what `runMigrationsAndValidate` checks the hand-written DDL against in T050. (depends on T039)
- [X] T042 [P] Create `shared/data/core/src/main/java/com/iris/data/repository/mapper/CapturedTransactionMapper.kt` mirroring `TransactionMapper` — `toDomain(entity): Either<String, CapturedTransaction>` reconstructing `CapturedKind` from `kind`/`direction`/`parentId`/`taxAmount`, and `toEntity(domain)`. (depends on T010, T033)
- [X] T043 [P] Create `shared/data/core/src/main/java/com/iris/data/repository/mapper/FinancialSenderMapper.kt` — `toDomain` drops an unknown `ruleSetId` to `null` rather than failing the row (data-model.md §1.3), `toEntity` writes it back. (depends on T011, T032)
- [X] T044 Create `shared/data/core/src/main/java/com/iris/data/repository/CapturedTransactionRepository.kt` with `findAllPending(): List<CapturedEntry>` (principals joined to their fee, newest first), `findById`, `pendingCount(): Flow<Int>`, `save`, `saveEntry` (principal + fee in one Room transaction), `deleteEntry` (deletes the linked fee too — FR-018) and `deleteAll`. All functions main-safe via `withContext(dispatchersProvider.io)`; mapper failures dropped with `.getOrNull()` exactly as `CategoryRepository` does. (depends on T036, T037, T042)
- [X] T045 [P] Create `shared/data/core/src/main/java/com/iris/data/repository/FinancialSenderRepository.kt` — `findAll`, `findEnabled(sender)`, `save`, `deleteById`. (depends on T036, T037, T043)
- [X] T046 [P] Create `shared/data/core/src/main/java/com/iris/data/repository/ProcessedMessageRepository.kt` — `isProcessed(fingerprint)`, `record(value)`. (depends on T036, T037)
- [X] T047 [P] Create `shared/data/core/src/main/java/com/iris/data/repository/CounterpartyCategoryRepository.kt` — `findCategory(key)`, `remember(value)` with upsert semantics. (depends on T036, T037)
- [X] T048 Add three keys to `shared/data/core/src/main/java/com/iris/data/datastore/DatastoreKeys.kt`: `SMS_CAPTURE_ENABLED = booleanPreferencesKey("sms_capture_enabled")`, `SMS_TRANSACTION_COST_CATEGORY_ID = stringPreferencesKey("sms_transaction_cost_category_id")`, `SMS_HISTORICAL_IMPORT_COMPLETED_AT = longPreferencesKey("sms_historical_import_completed_at")`. (FR-001, FR-017, FR-030)
- [X] T049 [P] Create `shared/data/core/src/test/java/com/iris/data/datastore/SmsDatastoreKeysTest.kt` asserting that reading an absent `SMS_CAPTURE_ENABLED` yields `false`, never `true` — the single default that keeps SC-010 true for every existing user on upgrade. (depends on T048)
- [X] T050 Add `migrate130to131_SmsCapture()` to `shared/data/core/src/androidTest/java/com/iris/data/db/IrisRoomDatabaseMigrationTest.kt`, following the existing `migrate129to130_LoanIncludeNote` pattern: create the DB at 130 with one `transactions` row, run `helper.runMigrationsAndValidate(TestDb, 131, true, Migration130to131_SmsCapture())`, then assert the pre-existing row survived and each of the four new tables accepts an insert. (depends on T038, T041)
- [X] T051 **Checkpoint — Phase 2**: `./gradlew :shared:data:model:testDebugUnitTest :shared:sms:parser:testDebugUnitTest :shared:data:core:testDebugUnitTest` green; `./gradlew detekt` green; `./scripts/integrationTests.sh` green (proves the migration and the exported `131.json` agree). Foundation ready — user story work can begin.

---

## Phase 3: User Story 1 — Capture a payment from a confirmation SMS (Priority: P1) 🎯 MVP

**Goal**: An M-PESA or bank payment SMS that arrives while the app is closed turns into a pending
item carrying the correct amount, counterparty, instant, reference and account, which the user can
confirm into an ordinary `Expense`/`Income`.

**Independent Test**: quickstart.md **V2** (parser corpus, no device), **V3** (force-stop the app,
`adb emu sms send MPESA "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. …"`, expect a
notification within 10 s and a pre-filled review item at `25 Jul 2026 19:50`, with balances unchanged
until confirm) and **V7.1** (the same message twice yields exactly one item).

### Parser rule sets and corpus (US1)

- [x] T052 [P] [US1] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/MpesaRules.kt` — `SenderRuleSet` id `mpesa`, sender patterns matching `MPESA` and the Safaricom short codes, `promoMarkers` for `Download My OneApp on https://…` / `Dial *522#`, exclusions for OTP, balance enquiry, loan-limit advert, statement notice, `FAILED`, `CANCELLED`, `REVERSED`, and `messageRules` for `paid to`, `sent to`, `SEND TO M-PESA request of` (MoneyOut) and `received`/`deposit` (MoneyIn). Principal rules only — fee rules arrive in T085. (FR-004, FR-007, FR-008, FR-011, FR-012)
- [x] T053 [P] [US1] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/DtbRules.kt` — id `dtb`, sender patterns for `DTB-KENYA`/`DTB`, `messageRules` for the `DTB 6000.00 KES has been successfully sent to … Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT` wording with `d MMM yyyy` + `HH:mm` formats, and exclusions for its promotional and failure wordings.
- [x] T054 [P] [US1] Create `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/KcbRules.kt` — id `kcb`, sender patterns for `KCB`, `messageRules` for the `ALERT: Your account no. 5XXXXX5001 has been debited with KES 540.25 for a POS PURCHASE at GITHUB, INC. SAN FRANCISCO CA on 25/07/2026.` wording (MoneyOut, no reference, `dd/MM/yyyy`) and the matching credited/MoneyIn wording.
- [x] T055 [US1] Add `@Provides @IntoSet` entries for `MpesaRules.ruleSet`, `DtbRules.ruleSet` and `KcbRules.ruleSet` to `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/SmsRuleCatalogModule.kt`. The engine file itself is not touched. (depends on T052–T054)
- [x] T056 [P] [US1] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/MpesaCorpus.kt` with the real spec messages: case 1 (`UGP7B0ITE4 … Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM …`) expecting 1350.00 / MoneyOut / `James Kinyua Mwangi` / `UGP7B0ITE4` / `2026-07-25T16:50:00Z`, an incoming-funds case expecting `MoneyIn`, a `2026-07-24 07:19:54 PM` timestamp case, and a case whose body carries a promo tail. (depends on T031, T052)
- [x] T057 [P] [US1] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/DtbCorpus.kt` with the DTB transfer message expecting 6000.00 / MoneyOut / `Michael Kamau Njuguna` / `AD3EA389C13A7` / `2026-07-25T14:41:00Z`. (depends on T031, T053)
- [x] T058 [P] [US1] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/KcbCorpus.kt` with the POS-purchase message expecting 540.25 / MoneyOut / normalised `GITHUB, INC.` / no reference / 2026-07-25. (depends on T031, T054)
- [x] T059 [P] [US1] Create `shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/NegativeCorpus.kt` with every mandatory negative from contracts/sms-parser-contract.md §6: zero-value principal, failed, cancelled, reversal notice, balance enquiry, loan-limit advert, statement notice, OTP, a non-financial sender (`NoRuleForSender`) and a financial sender in an unseen wording (`NoMatchingPattern`, **not** a half-filled parse). (depends on T031, FR-012, FR-013, SC-004)
- [x] T060 [US1] Register all four corpus files in `SmsCorpus.kt`, run `./gradlew :shared:sms:parser:testDebugUnitTest --tests "*SmsParserCorpusTest*"` and iterate the rule sets until every case is green. Grow the corpus toward the ≥200-message target from plan.md §"Scale/Scope". (depends on T055–T059, SC-001, SC-004)

### Domain use cases (US1)

- [x] T061 [US1] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/CaptureSmsUseCase.kt` together with the sealed `SmsCaptureError` (`CaptureDisabled`, `PermissionMissing`, `SenderNotConfigured`, `SenderDisabled`, `AlreadyProcessed`, `ParseFailure(SmsParseError)`, `Persistence`). Flow: normalise the sender → `FinancialSenderRepository.findEnabled` (FR-004) → `SmsParser.parse` → `MessageFingerprints.of` → `ProcessedMessageRepository.isProcessed` short-circuit (FR-028) → build the principal `CapturedTransaction` with `account` copied from the sender mapping (null allowed — FR-027a) → `DetectDuplicateTransactionUseCase` → `CapturedTransactionRepository.saveEntry` → `ProcessedMessageRepository.record`. Returns `Either<SmsCaptureError, CapturedEntry>`; never throws. (depends on T029, T044–T047, FR-005, FR-007, FR-013, FR-021)
- [x] T062 [P] [US1] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/DetectDuplicateTransactionUseCase.kt` — looks through `TransactionRepository` for an existing row with the same account, the same amount and a `dateTime` on the same Nairobi calendar day, returning `TransactionId?`. It never auto-discards. (FR-029, research.md D7)
- [x] T063 [US1] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/ConfirmCapturedTransactionUseCase.kt` with the sealed `ConfirmCaptureError` (`MissingAccount`, `CapturedItemGone`, `CategoryCreationFailed`, `Persistence`). Inside a single `IrisRoomDatabase.withTransaction { }`: reject with `MissingAccount` when `account == null` (FR-027a); insert the principal as `Expense`/`Income` via `TransactionRepository` using the field mapping in contracts/persistence-contract.md §5 (reference appended to the description as `Ref: UGP7B0ITE4` — FR-015); set `processed_messages.outcome = CAPTURED`; delete the captured row. **No `source` column, marker tag or metadata flag is written** (FR-033). Fee handling is added in T091. (depends on T061, FR-021, FR-033)
- [x] T064 [P] [US1] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/DismissCapturedTransactionUseCase.kt` — sets `processed_messages.outcome = DISMISSED` and deletes the captured row(s), inserting nothing, so a re-delivered or re-imported message can never resurrect it. (FR-025)
- [x] T065 [P] [US1] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/CaptureSmsUseCaseTest.kt` — MockK'd repositories, Given/When/Then, backticked names. Cases: gate closed → `CaptureDisabled`; unknown sender → `SenderNotConfigured`; disabled sender → `SenderDisabled`; already-processed fingerprint → `AlreadyProcessed` and nothing written; sender with `accountId = null` → captured with `account == null`; parse `Left` → `ParseFailure` and a `processed_messages` row with outcome `IGNORED`. (depends on T061)
- [x] T066 [P] [US1] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/ConfirmCapturedTransactionUseCaseTest.kt` — confirming a `NeedsAccount` item returns `MissingAccount` and writes nothing; confirming a ready item inserts exactly one `Transaction`, deletes the captured row and leaves no marker distinguishing it from a manual entry. (depends on T063)
- [x] T067 [P] [US1] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/DismissCapturedTransactionUseCaseTest.kt` — dismissing deletes the captured row, records `DISMISSED`, and a subsequent `CaptureSmsUseCase` call with the same fingerprint returns `AlreadyProcessed`. (depends on T064, FR-025)
- [x] T068 [P] [US1] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/DetectDuplicateTransactionUseCaseTest.kt` — same amount/account/day → the existing `TransactionId`; different account or different day → `null`. (depends on T062, FR-029)

### Android capture edge (US1)

- [x] T069 [US1] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsCaptureGate.kt` — `@Singleton`, `suspend fun isCapturing(): Boolean` = `SMS_CAPTURE_ENABLED` true **and** `RECEIVE_SMS` granted; `fun canReadInbox(): Boolean` = `READ_SMS` granted. An absent preference reads `false`. (depends on T048, FR-001, FR-003, FR-032)
- [x] T070 [US1] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsCaptureCoordinator.kt` — `@Singleton`, owns an application-scoped `CoroutineScope(SupervisorJob() + dispatchersProvider.io)`; `captureAsync(messages, onFinished)` joins multipart parts per originating address, checks `SmsCaptureGate.isCapturing()` first, runs `CaptureSmsUseCase` per message, posts the notification on success, and **always** invokes `onFinished`, including on the error path. The body is never written to disk or to a log. (depends on T061, T069, FR-005, FR-006)
- [x] T071 [US1] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsCaptureReceiver.kt` (`@AndroidEntryPoint`, `goAsync()`, delegates to the coordinator, no parsing and no DB access inline) and declare it in `shared/sms/capture/src/main/AndroidManifest.xml` with `android:exported="true"`, `android:permission="android.permission.BROADCAST_SMS"` and an `<intent-filter android:priority="100">` for `android.provider.Telephony.SMS_RECEIVED` — contracts/ui-and-platform-contract.md §4.1. (depends on T070, FR-005)
- [x] T072 [US1] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsCaptureNotifier.kt` — its own channel `sms_capture` created in this module (**not** by extending `IrisNotificationChannel` in `:temp:legacy-code`), `notifyCaptured(pendingCount)` with a content intent targeting `RootActivity` with `ACTION_REVIEW = "iris.wallet.intent.action.review_captured"`. A `POST_NOTIFICATIONS` denial degrades to "no notification", never to "no capture". (FR-026)
- [x] T073 [US1] Add an `<intent-filter>` for `iris.wallet.intent.action.review_captured` to the `.RootActivity` block in `app/src/main/AndroidManifest.xml` alongside the existing `iris.wallet.intent.action.add_transaction` filter, and map that action to `Navigation.navigateTo(SmsReviewScreen)` in `app/src/main/java/com/iris/RootActivity.kt`. (depends on T072, T075, FR-026)
- [x] T074 [US1] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/DefaultFinancialSenderCatalog.kt` seeding `MPESA`, `DTB-KENYA` and `KCB` with `accountId = null` and `enabled = true` on first capture-enable, so a P1-only build has senders to match while every item correctly lands in `NeedsAccount` until US4 supplies the mapping UI (plan.md §"P1 caveat"). (depends on T045, FR-004, FR-027a)

### Review screen (US1)

- [x] T075 [US1] Add `data object SmsReviewScreen : Screen` to `shared/ui/navigation/src/main/java/com/iris/navigation/Screens.kt`, leaving `isLegacy` at its `false` default.
- [x] T076 [US1] Add `SmsReviewScreen -> SmsReviewScreenImpl()` to the `when (screen)` in `app/src/main/java/com/iris/IrisNavGraph.kt` and the matching import, following the existing `FeaturesScreen -> FeaturesScreenImpl()` convention. (depends on T006, T075, T080)
- [x] T077 [P] [US1] Create `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewState.kt` with the `@Immutable sealed interface SmsReviewState { Loading | Empty | Content }`, `CapturedItemUi` (pre-formatted `amountFormatted`/`timeFormatted` strings only) and `ReviewStatusUi`, per contracts/ui-and-platform-contract.md §2. No domain type crosses into view-state. (FR-021, FR-021a, FR-027a)
- [x] T078 [P] [US1] Create `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewEvent.kt` with the US1 subset — `OnItemExpanded`, `OnConfirm`, `OnDismiss`, `OnClose`. The enrichment events are added in T102.
- [x] T079 [US1] Create `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewViewModel.kt` extending `com.iris.ui.ComposeViewModel<SmsReviewState, SmsReviewEvent>`, using the Compose runtime (`mutableStateOf` + `@Composable fun uiState()`), **not** Flow/StateFlow. It loads via `CapturedTransactionRepository.findAllPending()`, maps to `CapturedItemUi` with `FormatMoneyUseCase` and `TimeFormatter`, and routes `OnConfirm`/`OnDismiss` to the use cases. (depends on T044, T063, T064, T077, T078)
- [x] T080 [US1] Create `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewScreen.kt` exposing `@Composable fun SmsReviewScreenImpl()` plus a stateless `SmsReviewUi(state, onEvent)` so Paparazzi can render it without Hilt. Renders the pending list with amount, counterparty, time, reference and account, and confirm/dismiss affordances; the confirm affordance is disabled for `ReviewStatusUi.NeedsAccount`. (depends on T077, T079)
- [x] T081 [P] [US1] Create `screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewFixtures.kt` with **static** view-state fixtures — hard-coded `amountFormatted = "KES 1,350.00"`, `timeFormatted = "25 Jul 2026, 19:50"` — and no `Instant.now()` or live formatter anywhere. (depends on T077)
- [x] T082 [P] [US1] Create `screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewViewModelTest.kt` using the Molecule-backed `viewModel.runTest(events = listOf(...)) { }` from `:shared:ui:testing`. US1 rows of the table in contracts/ui-and-platform-contract.md §2: no pending items → `Empty`; an item with no account → `NeedsAccount` and `OnConfirm` is a no-op; `OnConfirm` on a ready item invokes the use case once and removes it from the list; `OnDismiss` removes it and does not re-add on reload. (depends on T079)
- [x] T083 [P] [US1] Create `screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewScreenshotTest.kt` extending `PaparazziScreenshotTest` with `@RunWith(TestParameterInjector::class)` and `@TestParameter theme: PaparazziTheme`, snapshotting `empty`, `single item ready to confirm` and `item needing an account`; record with `./gradlew :screen:sms-review:recordPaparazziDebug` and commit the goldens. (depends on T080, T081)
- [x] T084 [US1] **Checkpoint — User Story 1**: `./gradlew :shared:sms:parser:testDebugUnitTest :shared:domain:testDebugUnitTest --tests "com.iris.domain.usecase.sms.*" :screen:sms-review:testDebugUnitTest`, `./gradlew :screen:sms-review:verifyPaparazziDebug`, `./gradlew detekt :app:lintDebug` — all green. Then run quickstart **V2**, **V3** and **V7.1** on an emulator with the toggle flipped, confirming the notification lands within 10 s (SC-005), the review item carries the *message* timestamp, and balances/budgets/reports/search are unchanged before confirm (FR-021a).

---

## Phase 4: User Story 2 — Record transaction costs as their own spending (Priority: P1) 🎯 MVP

**Goal**: A reported charge greater than zero becomes a second, separate expense filed under one
dedicated "Transaction costs" category, linked to the payment it came from.

**Independent Test**: quickstart.md **V4** — inject the DTB transfer, expect one pending item of
KES 6,000.00 showing a linked fee of KES 59.76; confirm it and expect *two* transactions, the fee
under a category named "Transaction costs" created exactly once; then inject the zero-fee M-PESA
message and expect exactly one transaction and no fee.

- [X] T085 [P] [US2] Add `feeRules` to `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/MpesaRules.kt` for `Transaction cost, Ksh0.00` and `Transaction cost KES 66.00 Incl. Tax Amount KES 8.25` (binding both the `amount` and the `tax` group). Zero costs need no special case — `PositiveDouble` rejects them, so no fee is produced (FR-016, FR-019, Acceptance 2.3).
- [X] T086 [P] [US2] Add `feeRules` to `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/DtbRules.kt` for the `Charges 59.76 KES` wording.
- [X] T087 [P] [US2] Add `feeRules` to `shared/sms/parser/src/main/java/com/iris/sms/parser/rules/KcbRules.kt` for its charge/levy wordings, including a standalone charge notification that arrives as its own message with no principal (FR-020, Edge "Ambiguous fee attribution").
- [X] T088 [US2] Extend the corpus: fee expectations on the DTB case in `DtbCorpus.kt` (fee 59.76), the `SEND TO M-PESA` case in `MpesaCorpus.kt` (fee 66.00, tax 8.25), an explicit `fee = null` expectation for the `Transaction cost, Ksh0.00` case, and a standalone-fee case with `principal = null`. Re-run `./gradlew :shared:sms:parser:testDebugUnitTest --tests "*SmsParserCorpusTest*"`. (depends on T085–T087)
- [X] T089 [US2] Extend `shared/domain/src/main/java/com/iris/domain/usecase/sms/CaptureSmsUseCase.kt` to persist `ParsedMessage.fee` as a second `CapturedTransaction` with `kind = CapturedKind.Fee(parent = principalId, tax = …)` in the same `saveEntry` call, and with `parent = null` when the message carried no principal (FR-020). (depends on T061, T088, FR-016, FR-018, FR-019)
- [X] T090 [US2] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/EnsureTransactionCostCategoryUseCase.kt` returning `Either<String, CategoryId>`: read `DatastoreKeys.SMS_TRANSACTION_COST_CATEGORY_ID`, verify it still resolves via `CategoryRepository.findById`, otherwise create a `Category` named "Transaction costs" via `CategoryRepository.save` and store the new id. Called lazily on the first fee confirmation, never at feature-enable time. (depends on T048, FR-017, research.md D11)
- [X] T091 [US2] Extend `ConfirmCapturedTransactionUseCase` so that, inside the same `withTransaction { }` block, a linked fee is committed as a second `Expense` whose `categoryId` is the result of `EnsureTransactionCostCategoryUseCase`, and both captured rows are deleted together. Any failure rolls the whole block back to `Left(ConfirmCaptureError.Persistence)`. (depends on T063, T090, FR-016, FR-017, SC-003)
- [X] T092 [US2] Extend `DismissCapturedTransactionUseCase` with an `alsoRemoveFee: Boolean` parameter so discarding a principal removes its linked fee, keeping the two from drifting apart. (depends on T064, FR-018, Acceptance 2.5)
- [X] T093 [P] [US2] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/EnsureTransactionCostCategoryUseCaseTest.kt` — first call creates the category and stores the id; second call reuses it; a renamed category is still reused (lookup is by id, not name); a deleted category is recreated once. (depends on T090, FR-017)
- [X] T094 [P] [US2] Extend `ConfirmCapturedTransactionUseCaseTest` and `CaptureSmsUseCaseTest` with fee cases: a principal + fee confirms into exactly two `transactions` rows with the fee under the transaction-cost category; a zero-fee message produces one row; a standalone fee (`parent == null`) confirms on its own; a rolled-back failure leaves the captured rows intact. (depends on T089, T091)
- [X] T095 [US2] Surface the fee in the review UI: add `feeFormatted: String?` handling to `SmsReviewViewModel`'s mapping, render the linked cost on its principal in `SmsReviewScreen.kt`, and add the `alsoRemoveFee` choice to the dismiss affordance. (depends on T077, T080, T092, FR-018)
- [X] T096 [P] [US2] Add a `item with a fee expanded` snapshot to `SmsReviewScreenshotTest.kt` (static fixture, `feeFormatted = "KES 59.76"`) and a `runTest` case in `SmsReviewViewModelTest.kt` asserting `OnDismiss(alsoRemoveFee = true)` removes both rows and `OnConfirm` on a fee-bearing principal commits two transactions. (depends on T095)
- [X] T097 [US2] **Checkpoint — User Story 2 / P1 MVP gate**: `./gradlew testDebugUnitTest`, `./gradlew detekt`, `./gradlew verifyPaparazziDebug`, `./scripts/integrationTests.sh` all green; run quickstart **V4** end to end plus a re-run of **V2**, **V3**, **V7.1**. Per quickstart.md's definition of done, P1 (US1 + US2) is now shippable on its own.

---

## Phase 5: User Story 3 — Add category and description in a few taps (Priority: P2)

**Goal**: A captured payment can be given a category and a description, and every extracted value can
be corrected, without re-entering anything — with the category the user last chose for that
counterparty pre-selected.

**Independent Test**: quickstart.md **V5** (file a payment to `FRANK INN KIKUYU` under "Food &
Drinks", capture a second payment from the same payee and expect that category pre-selected before
any input; override it and expect the newest choice suggested next) and **V6** (an unmapped sender is
captured, flagged, and cannot be confirmed until an account is picked).

- [x] T098 [US3] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/SuggestCategoryUseCase.kt` — `CounterpartyNormalizer.key(counterparty)` → `CounterpartyCategoryRepository.findCategory(key)` → `CategoryId?`, returning null rather than guessing. (depends on T047, T021, FR-024)
- [x] T099 [US3] Extend `ConfirmCapturedTransactionUseCase` to upsert `counterparty_categories` inside the same `withTransaction { }` block whenever a category was chosen, so the most recent choice is always the one suggested. (depends on T063, T091, FR-024, Acceptance 3.3)
- [x] T100 [P] [US3] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/SuggestCategoryUseCaseTest.kt` — a remembered counterparty returns its category; an unseen counterparty returns null; `"Frank Inn Kikuyu"` and `"FRANK INN KIKUYU"` share one memory row; confirming with an overriding category updates the memory. (depends on T098, T099)
- [x] T101 [US3] Extend `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewState.kt` with `expandedItemId`, `categories: ImmutableList<CategoryPickUi>`, `accounts: ImmutableList<AccountPickUi>` and the `categoryName`/`description` fields on `CapturedItemUi`, plus `ReviewStatusUi.PossibleDuplicate(existingSummary)`. All still primitives / `@Immutable` / `ImmutableList`. (depends on T077)
- [x] T102 [US3] Extend `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewEvent.kt` with `OnCategorySelected`, `OnDescriptionChanged`, `OnAccountSelected`, `OnAmountEdited`, `OnCounterpartyEdited`, `OnTimeEdited` and `OnKeepDespiteDuplicate` per contracts/ui-and-platform-contract.md §2. (depends on T078, FR-022, FR-023, FR-027a, FR-029)
- [x] T103 [US3] Extend `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewViewModel.kt`: load categories via `CategoryRepository` and accounts via `AccountRepository`, pre-fill `categoryName` from `SuggestCategoryUseCase` before any user input, handle every new event exhaustively, and pass the *edited* values to confirm. (depends on T098, T101, T102, FR-022, FR-023, FR-024)
- [x] T104 [US3] Extend `screen/sms-review/src/main/java/com/iris/sms/review/SmsReviewScreen.kt` with the expanded per-item editor: category picker, description field, account picker (which clears `NeedsAccount`), inline amount / counterparty / date editing, and the "keep anyway" affordance for a possible duplicate. Target SC-002 — three taps or fewer from open to confirm. (depends on T101, T103)
- [x] T105 [P] [US3] Extend `screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewViewModelTest.kt` with the remaining rows of the contract table: a remembered counterparty pre-fills `categoryName`; `OnCategorySelected` overriding the suggestion is what reaches confirm; `OnAccountSelected` then `OnConfirm` on a `NeedsAccount` item invokes confirm once and removes the item; `OnKeepDespiteDuplicate` clears the flag without committing; edited amount/date/counterparty are the values saved. (depends on T103)
- [x] T106 [P] [US3] Extend `screen/sms-review/src/test/java/com/iris/sms/review/SmsReviewScreenshotTest.kt` with the `item flagged as a possible duplicate` and expanded-editor snapshots, using static fixtures only; re-record with `./gradlew :screen:sms-review:recordPaparazziDebug`. (depends on T104)
- [ ] T107 [US3] **Checkpoint — User Story 3**: `./gradlew :screen:sms-review:testDebugUnitTest :shared:domain:testDebugUnitTest`, `./gradlew :screen:sms-review:verifyPaparazziDebug`, `./gradlew detekt`, and the Compose stability gate (`./gradlew assembleDemo -PcomposeCompilerReports=true` then `./gradlew :ci-actions:compose-stability:run`). Run quickstart **V5** and **V6**.

---

## Phase 6: User Story 4 — Set up and control SMS capture (Priority: P2)

**Goal**: The user is told what will be read *before* any system dialog, grants `RECEIVE_SMS`, maps
each sender to one of their accounts, and can turn the feature — or any single sender — off at any
time.

**Independent Test**: quickstart.md **V1** (enable from Settings, see the rationale first, grant,
map `MPESA`, all under 90 seconds; and on a second run deny the permission and get an explanation
plus a retry), **V9** (disable the toggle and verify nothing further is captured while confirmed
transactions and pending items are untouched) and **V0** (a fresh install surfaces nothing but one
new Settings row).

- [x] T108 [US4] Add `data object SmsCaptureSettingsScreen : Screen` to `shared/ui/navigation/src/main/java/com/iris/navigation/Screens.kt`, leaving `isLegacy` at `false`. (depends on T075)
- [x] T109 [P] [US4] Create `screen/sms-settings/src/main/java/com/iris/sms/settings/SmsCaptureSettingsState.kt` with `SmsCaptureSettingsState`, `SmsPermissionUi { Granted | NotRequested | Denied | PermanentlyDenied }`, `SenderMappingUi` and `HistoricalImportUi { Available | Running | Finished | AlreadyRun }` per contracts/ui-and-platform-contract.md §3. (FR-002, FR-003, FR-027)
- [x] T110 [P] [US4] Create `screen/sms-settings/src/main/java/com/iris/sms/settings/SmsCaptureSettingsEvent.kt` with the full event set from the same contract section.
- [x] T111 [US4] Create `screen/sms-settings/src/main/java/com/iris/sms/settings/SmsCaptureSettingsViewModel.kt` extending `ComposeViewModel<SmsCaptureSettingsState, SmsCaptureSettingsEvent>`: `captureEnabled` defaults to `false` on fresh install and on upgrade; `OnEnableRequested` only sets `rationaleVisible = true` and never triggers the system dialog directly; `OnPermissionResult(granted = false)` leaves the toggle off with a retry; `OnDisable` flips the DataStore flag only and deletes nothing; sender add/map/enable/remove go through `FinancialSenderRepository`; `pendingCount` comes from `CapturedTransactionRepository.pendingCount()`. (depends on T045, T069, T074, T109, T110, FR-001, FR-003, FR-021a, FR-027)
- [x] T112 [US4] Create `screen/sms-settings/src/main/java/com/iris/sms/settings/SmsCaptureSettingsScreen.kt` exposing `SmsCaptureSettingsScreenImpl()` plus a stateless `SmsCaptureSettingsUi(state, onEvent)`. Renders the rationale panel stating *what is read*, *what is extracted* and *that nothing leaves the device* before requesting anything, then requests `RECEIVE_SMS` via `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`, using `shouldShowRequestPermissionRationale` to distinguish `Denied` from `PermanentlyDenied` (the latter deep-links to the system app-settings page, copying the existing `SettingsViewModel` pattern). (depends on T111, FR-002, FR-032)
- [x] T113 [US4] Add the per-sender management UI to `SmsCaptureSettingsScreen.kt` — add a sender, map it to an account, toggle it, remove it, each sender independent of the others — plus a "Review N captured transactions" action navigating to `SmsReviewScreen`. (depends on T112, FR-003, FR-027, FR-027a, Acceptance 4.5)
- [x] T114 [US4] Add `SmsCaptureSettingsScreen -> SmsCaptureSettingsScreenImpl()` to the `when (screen)` in `app/src/main/java/com/iris/IrisNavGraph.kt` with its import. (depends on T006, T108, T112)
- [x] T115 [US4] Add one row — "SMS transaction capture" — to `screen/settings/src/main/java/com/iris/settings/SettingsScreen.kt` navigating to `SmsCaptureSettingsScreen`, with the pending count as a trailing badge when greater than zero. **This is the only edit made to an existing screen module**, which is what protects SC-010. (depends on T108, T111, FR-021a)
- [x] T116 [P] [US4] Create `screen/sms-settings/src/test/java/com/iris/sms/settings/SmsCaptureSettingsViewModelTest.kt` using `viewModel.runTest(events) { }`: default state is `captureEnabled = false`; `OnEnableRequested` shows the rationale and does not enable; `OnRationaleAccepted` + granted → enabled; denied → still disabled with a retry offered; permanently denied → `PermanentlyDenied`; `OnDisable` leaves captured items and committed transactions untouched; `OnSenderEnabledChanged` and `OnSenderAccountSelected` affect only the targeted sender. (depends on T111)
- [ ] T117 [US4] **Checkpoint — User Story 4**: `./gradlew :screen:sms-settings:testDebugUnitTest :screen:settings:testDebugUnitTest`, `./gradlew detekt :app:lintDebug`, `./gradlew verifyPaparazziDebug`. Run quickstart **V0**, **V1** and **V9**, timing V1 against SC-007 (under 90 seconds) and confirming with `adb shell dumpsys package com.iris.wallet.debug` that `RECEIVE_SMS`/`READ_SMS` are requested but not granted before the user opts in.

---

## Phase 7: User Story 5 — Import recent past messages (Priority: P3)

**Goal**: An opt-in, one-time import of payment messages from the previous 30 days, running in the
background without blocking the app and without duplicating anything already captured.

**Independent Test**: quickstart.md **V8** — seed the emulator inbox including messages older than 30
days, run the import, leave the screen, and expect the app to stay responsive, a completion
notification reporting the captured count, nothing produced from the older messages, and no
duplicates against items already captured live.

- [X] T118 [P] [US5] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsInboxDataSource.kt` — `readWindow(from: Instant, to: Instant, page: Int)` queries `Telephony.Sms.Inbox` through `ContentResolver` with `selection = "date >= ? AND date <= ?"` and `LIMIT/OFFSET` paging of 200 rows. The 30-day bound is enforced **in the SQL selection**, so older messages are never read into the process. Define `HistoricalImportWindow.DAYS = 30` here. (depends on T069, FR-030)
- [X] T119 [US5] Create `shared/domain/src/main/java/com/iris/domain/usecase/sms/ImportRecentSmsUseCase.kt` — walks the paged window, runs every row through the *same* `CaptureSmsUseCase` as live capture (therefore the same `processed_messages` dedupe), `yield()`s between pages, and reports `(processed, captured)` counts as it goes. (depends on T061, T118, FR-030, FR-031, SC-003, SC-009)
- [X] T120 [US5] Create `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsImportWorker.kt` — `@HiltWorker` `CoroutineWorker`, `UNIQUE_NAME = "sms-historical-import"`, enqueued with `ExistingWorkPolicy.KEEP` so a second tap cannot double-import, publishing `setProgress()` with `PROGRESS_PROCESSED`/`PROGRESS_CAPTURED` after each page. Its input is two timestamps — **no message content ever enters the WorkManager database**. (depends on T119, FR-031)
- [X] T121 [US5] Add `notifyImportFinished(capturedCount)` to `shared/sms/capture/src/main/java/com/iris/sms/capture/SmsCaptureNotifier.kt` and have `SmsImportWorker` post it on completion and write `DatastoreKeys.SMS_HISTORICAL_IMPORT_COMPLETED_AT`, which is what makes the import one-time. (depends on T048, T072, T120, FR-030, FR-031)
- [X] T122 [US5] Wire the import into `screen/sms-settings/src/main/java/com/iris/sms/settings/SmsCaptureSettingsScreen.kt` and `SmsCaptureSettingsViewModel.kt`: request `READ_SMS` **only** when the user opts into the import (so a P1-only user grants `RECEIVE_SMS` alone), offer the action only when `READ_SMS` is granted and `SMS_HISTORICAL_IMPORT_COMPLETED_AT` is unset, enqueue `SmsImportWorker`, and drive `HistoricalImportUi.Available → Running(processed) → Finished(captured) → AlreadyRun` from the worker's progress. (depends on T109–T112, T120, T121, FR-030, FR-031)
- [X] T123 [P] [US5] Create `shared/domain/src/test/java/com/iris/domain/usecase/sms/ImportRecentSmsUseCaseTest.kt` — the query window starts at `now - 30 days` and older rows are never requested; a message already captured live is skipped via `processed_messages`; a dismissed message is not re-captured; paging covers a set larger than one page; cancellation between pages loses no already-captured item. (depends on T119, FR-030, SC-003, SC-009)
- [X] T124 [P] [US5] Extend `screen/sms-settings/src/test/java/com/iris/sms/settings/SmsCaptureSettingsViewModelTest.kt` with import cases: the action is hidden without `READ_SMS`; hidden once `SMS_HISTORICAL_IMPORT_COMPLETED_AT` is set; `OnStartHistoricalImport` moves the state to `Running` and then to `Finished(captured)`. (depends on T122)
- [ ] T125 [US5] **Checkpoint — User Story 5**: `./gradlew :shared:domain:testDebugUnitTest :screen:sms-settings:testDebugUnitTest`, `./gradlew detekt :app:lintDebug`. Run quickstart **V8** with a seeded inbox of ~5,000 messages, verifying with `adb logcat -s SmsImportWorker` that the query window starts at `now - 30d` (FR-030) and that the app never becomes unresponsive (SC-009).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T126 [P] Create `shared/data/core/src/test/java/com/iris/data/backup/SmsCaptureBackupExclusionTest.kt` asserting that `BackupDataUseCase` in `shared/data/core/src/main/java/com/iris/data/backup/` and the CSV export in `shared/domain/src/main/java/com/iris/domain/usecase/csv/` emit none of the four new tables — un-reviewed captured data is transient and privacy-sensitive (research.md D4, contracts/persistence-contract.md §6).
- [X] T127 [P] Audit every `Timber` call added by this feature across `:shared:sms:parser`, `:shared:sms:capture` and `:shared:domain` against the log contract in quickstart.md §Diagnostics: rule ids, fingerprints, `SmsParseError` types and counts only — never a message body, a sender's full number or an extracted counterparty name. `ExcludedByRule`/`NoMatchingPattern` log at `Timber.d`; `InvalidAmount`/`InvalidDateTime`/`InvalidValue` at `Timber.w` with the offending token only. (FR-006)
- [X] T128 [P] Add a round-trip test to `shared/data/core/src/androidTest/java/com/iris/data/db/` — save a captured principal with a linked fee, read it back as a `CapturedEntry`, confirm it, then assert two rows in `transactions`, zero rows in `captured_transactions`, and that neither transaction carries any marker distinguishing it from a manual entry. (FR-033, SC-003)
- [X] T129 [P] Regenerate the module graph with `./scripts/generateModulesGraph.sh` (updating `all_modules.gv`) and add the four new modules to the module list in `.github/copilot-instructions.md`.
- [X] T130 Run `./scripts/detektFormat.sh` and resolve every finding; refresh the lint baseline only if a genuinely unavoidable warning appears (`app/lint-baseline.xml`), preferring a fix over a baseline entry.
- [ ] T131 Run the Compose stability gate — `./gradlew assembleDemo -PcomposeCompilerReports=true` then `./gradlew :ci-actions:compose-stability:run` — and fix any unstable parameter reported for `SmsReviewState`, `CapturedItemUi`, `SmsCaptureSettingsState` or `SenderMappingUi`.
- [X] T132 Run `./gradlew recordPaparazziDebug` followed by `./gradlew verifyPaparazziDebug` and commit the final goldens for `:screen:sms-review`.
- [ ] T133 **Full CI gate**: `./gradlew detekt`, `./gradlew :app:lintRelease`, `./gradlew testDebugUnitTest`, `./gradlew verifyPaparazziDebug`, `./gradlew assembleDemo -PcomposeCompilerReports=true && ./gradlew :ci-actions:compose-stability:run`, `./scripts/integrationTests.sh` — every one green.
- [ ] T134 **Full quickstart validation**: run scenarios **V0 → V9** from [quickstart.md](./quickstart.md) end to end on an emulator, then confirm the parser corpus has reached the ≥200-message target that holds SC-001 (≥95% accuracy) and SC-004 (zero false positives).

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 Setup (T001–T008)** — no dependencies; start immediately
- **Phase 2 Foundational (T009–T051)** — depends on Phase 1; **blocks every user story**
- **Phase 3 US1 (T052–T084)** — depends on Phase 2 only
- **Phase 4 US2 (T085–T097)** — depends on Phase 2; extends US1's rule sets, capture and confirm paths, so in practice it follows Phase 3
- **Phase 5 US3 (T098–T107)** — depends on Phase 2; extends the `:screen:sms-review` files created in Phase 3
- **Phase 6 US4 (T108–T117)** — depends on Phase 2; independent of Phases 3–5 except for `SmsReviewScreen` navigation (T108 follows T075)
- **Phase 7 US5 (T118–T125)** — depends on Phase 2 and on `CaptureSmsUseCase` (T061); its UI lands in the `:screen:sms-settings` files from Phase 6
- **Phase 8 Polish (T126–T134)** — depends on all shipped stories

### Cross-cutting hard edges

```text
T001–T004 ──► T005 ──► T006 ──► T076, T114        (module registration before nav wiring)
T009, T010 ──► T016, T027, T033, T042             (domain types before parser + persistence)
T017 ──► T018 ──► T029 ──► T030 ──► T031          (error ADT → rule model → engine → catalog → corpus)
T019–T022 ──► T029                                (primitive parsers before the pipeline)
T032–T035 ──► T036, T037, T038 ──► T039 ──► T040, T041 ──► T050
T042, T036, T037 ──► T044 ──► T061 ──► T063, T089, T119
T048 ──► T049, T069, T090, T121
T052–T054 ──► T055 ──► T060                       (rules registered before the corpus can go green)
T061 ──► T070 ──► T071                            (use case before coordinator before receiver)
T072 ──► T073                                     (notifier before its intent-filter)
T075 ──► T076, T073, T108
T077, T078 ──► T079 ──► T080 ──► T083
T063 ──► T091 ──► T099                            (confirm is extended, never forked)
T090 ──► T091
T111 ──► T112 ──► T113, T114, T115, T122
T118 ──► T119 ──► T120 ──► T121 ──► T122
```

### Within each user story

- Parser rules before the corpus rows that exercise them
- Domain types before repositories before use cases before ViewModels before screens
- View-state and event files before the ViewModel; the ViewModel before the composable
- Static Paparazzi fixtures before the screenshot test
- Story complete and its checkpoint green before moving to the next priority

---

## Parallel Opportunities

**71 of the 134 tasks are marked `[P]`** and can run concurrently within their phase.

The largest parallel batches:

```bash
# Phase 1 — the four module scaffolds are entirely independent
T001 shared/sms/parser/build.gradle.kts
T002 shared/sms/capture/build.gradle.kts + library manifest
T003 screen/sms-review/build.gradle.kts
T004 screen/sms-settings/build.gradle.kts

# Phase 2a — five domain model files, no shared file
T009 SmsPrimitives.kt   T010 CapturedTransaction.kt   T011 FinancialSender.kt
T012 CounterpartyCategory.kt   T013 ProcessedMessage.kt

# Phase 2b — the four primitive parsers, then their four tests
T019 AmountParser.kt   T020 SmsDateTimeParser.kt
T021 CounterpartyNormalizer.kt   T022 PromoTailStripper.kt
T023 AmountParserTest   T024 SmsDateTimeParserTest
T025 CounterpartyNormalizerTest   T026 PromoTailStripperTest

# Phase 2c — the four Room entities, then the two mappers, then three repositories
T032 FinancialSenderEntity   T033 CapturedTransactionEntity
T034 ProcessedMessageEntity  T035 CounterpartyCategoryEntity
T042 CapturedTransactionMapper   T043 FinancialSenderMapper
T045 FinancialSenderRepository   T046 ProcessedMessageRepository   T047 CounterpartyCategoryRepository

# Phase 3 — one file per provider, one corpus file per provider
T052 MpesaRules.kt   T053 DtbRules.kt   T054 KcbRules.kt
T056 MpesaCorpus.kt  T057 DtbCorpus.kt  T058 KcbCorpus.kt  T059 NegativeCorpus.kt
T065 T066 T067 T068  (four use-case test files)

# Phase 4 — fee rules, one file per provider
T085 MpesaRules fee rules   T086 DtbRules fee rules   T087 KcbRules fee rules
```

Across stories: once Phase 2 is green, US1 (`:shared:sms:parser` rules + `:shared:sms:capture` +
`:screen:sms-review`) and US4 (`:screen:sms-settings`) touch disjoint modules and can be staffed in
parallel; US5 is the only story that must wait on another story's use case (`CaptureSmsUseCase`).

---

## Implementation Strategy

### MVP first (US1 + US2 — both P1)

1. Phase 1 Setup → Phase 2 Foundational (T001–T051)
2. Phase 3 US1 (T052–T084) — **stop and validate** with quickstart V2, V3, V7.1
3. Phase 4 US2 (T085–T097) — **stop and validate** with quickstart V4
4. At T097 the feature is shippable: with the DataStore toggle flipped, a real M-PESA/DTB/KCB message
   becomes a correct transaction plus a correct fee. FR-001's default-off means every user who does
   not opt in sees no change whatsoever (SC-010).
5. Known and *correct* P1 limitation: with no settings UI yet, every captured item sits in
   `NeedsAccount` until US4 — this is FR-027a working as specified, not a degradation.

### Incremental delivery

| Increment | Tasks | Delivers |
|---|---|---|
| Foundation | T001–T051 | Nothing user-visible; DB at 131, engine ready |
| **MVP (P1)** | T052–T097 | SC-001, SC-003, SC-004, SC-005, SC-006 |
| P2 | T098–T117 | SC-002, SC-007, SC-008, SC-010 |
| P3 | T118–T125 | SC-009 |
| Polish | T126–T134 | Full CI gate + V0–V9 |

### Parallel team strategy

After T051: Developer A takes US1 then US2 (`:shared:sms:parser`, `:shared:sms:capture`,
`:screen:sms-review`); Developer B takes US4 (`:screen:sms-settings`, the Settings row); Developer C
picks up US3 once T080 exists and US5 once T061 exists. Only `Screens.kt`, `IrisNavGraph.kt` and
`app/src/main/AndroidManifest.xml` are shared files — each is touched by a small, clearly owned task
(T073, T075, T076, T108, T114).

---

## Requirements Coverage

Every one of the 35 functional requirements maps to at least one task.

| Requirement | Tasks |
|---|---|
| FR-001 capture off by default | T048, T049, T069, T111, T117 |
| FR-002 explain before requesting | T112, T117 |
| FR-003 disable globally / per sender | T011, T069, T111, T113 |
| FR-004 only configured senders | T011, T036, T052, T061, T074 |
| FR-005 capture while app not running | T070, T071, T084 |
| FR-006 nothing leaves the device | T013, T027, T070, T127 |
| FR-007 extract amount/currency/instant/counterparty/reference/cost | T029, T052–T054, T056–T058, T061 |
| FR-008 direction money-in / money-out | T010, T018, T052–T054, T056 |
| FR-009 separators, `Ksh` and `KES` | T019, T023 |
| FR-010 date/time formats, 2-digit years, EAT | T020, T024 |
| FR-011 discard promotional text | T022, T026, T052 |
| FR-012 no transaction from failed/zero | T010, T017, T019, T052, T059 |
| FR-013 skip rather than guess | T017, T029, T059 |
| FR-014 normalise counterparty names | T021, T025 |
| FR-015 retain the reference | T010, T063 |
| FR-016 fee as a separate expense | T085–T089, T091 |
| FR-017 one transaction-cost category | T048, T090, T091, T093 |
| FR-018 link fee, remove together | T044, T089, T092, T095 |
| FR-019 tax is detail, not a transaction | T010, T085, T088 |
| FR-020 standalone fee | T010, T087, T088, T089 |
| FR-021 pending list, nothing committed until confirm | T033, T044, T061, T063, T077, T080 |
| FR-021a no effect on balances; show the count | T036, T039, T111, T115, T084 |
| FR-022 assign category, edit description | T101–T104 |
| FR-023 correct amount/date/counterparty/account | T102, T103, T104, T105 |
| FR-024 suggest the last category for a counterparty | T012, T021, T047, T098, T099, T100 |
| FR-025 dismissed items never reappear | T013, T064, T067 |
| FR-026 notify, leading to review | T072, T073 |
| FR-027 map sender → account | T011, T045, T111, T113 |
| FR-027a no mapping ⇒ capture, flag, block confirm | T010, T015, T063, T074, T077, T104 |
| FR-028 never process a message twice | T027, T028, T034, T061 |
| FR-029 flag a probable duplicate | T062, T068, T101, T105 |
| FR-030 optional one-time 30-day import | T118, T119, T121, T122, T123 |
| FR-031 import is non-blocking and reports | T119, T120, T121, T122 |
| FR-032 everything works when disabled | T007, T069, T112, T117 |
| FR-033 indistinguishable from manual entries | T039, T063, T128 |

### Success criteria coverage

| Criterion | Proven by |
|---|---|
| SC-001 ≥95% extraction accuracy | T060, T088, T134 (parser corpus) |
| SC-002 record in <10 s, ≤3 taps | T104, T107 (quickstart V5) |
| SC-003 zero duplicates | T028, T061, T091, T123, T128 |
| SC-004 zero false positives over ≥200 messages | T059, T060, T134 |
| SC-005 captured within 10 s | T070, T071, T084 (quickstart V3) |
| SC-006 fee spend as a single figure | T090, T091, T097 (quickstart V4) |
| SC-007 setup under 90 s | T112, T117 (quickstart V1) |
| SC-008 3× more transactions recorded | T104, T107 (US3 flow) |
| SC-009 5,000-message import without ANR | T118, T119, T120, T123, T125 |
| SC-010 nothing changes when disabled | T049, T115, T117 (quickstart V0, V9) |

---

## Notes

- `[P]` means a different file with no dependency on an incomplete task — never two tasks editing the
  same file
- `ConfirmCapturedTransactionUseCase` (T063) is deliberately *extended* by T091 and T099 rather than
  forked, so the atomicity contract in contracts/persistence-contract.md §4 has exactly one
  implementation
- Every fallible path returns Arrow `Either` with a sealed error type — nothing in this feature throws
- Screen modules hold exactly four files (`…Screen.kt`, `…ViewModel.kt`, `…State.kt`, `…Event.kt`);
  test fixtures and tests live under `src/test/java/`
- ViewModels use the Compose runtime (`mutableStateOf`, `@Composable fun uiState()`), never
  Flow/StateFlow, and view-state is primitives / `@Immutable` / `ImmutableList` only
- Paparazzi fixtures are static — hard-coded `Instant`s and pre-formatted strings, never `now()`
- Commit after each task or logical group; stop at any checkpoint to validate the story on its own
