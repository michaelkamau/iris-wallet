# Implementation Plan: Automatic SMS Transaction Capture

**Branch**: `001-sms-transaction-capture` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-sms-transaction-capture/spec.md`

## Summary

Read M-PESA and Kenyan bank confirmation SMS on-device, extract the amount, counterparty, instant,
reference and transaction cost, and hold each result in a **pending review list** until the user
confirms it. Confirmation commits ordinary `Income`/`Expense` rows through the existing
`TransactionRepository` — a transaction cost becomes a second `Expense` under one dedicated
"Transaction costs" category — after which nothing distinguishes a captured transaction from a
manually entered one.

Technical approach, in one paragraph: a **rule-driven, Android-free parser** in a new
`:shared:sms:parser` module turns a raw message into `Either<SmsParseError, ParsedMessage>`, where
each provider contributes an immutable `SenderRuleSet` via a Hilt `@IntoSet` multibinding so new
wordings never touch the engine. A manifest-registered `SmsCaptureReceiver` in a new
`:shared:sms:capture` module uses `goAsync()` to run `CaptureSmsUseCase` in-process — the message
body is never written to disk — while a bounded `SmsImportWorker` covers the optional one-time
30-day inbox import. Pending items live in **four new tables of the existing `IrisRoomDatabase`**
(DB 130 → 131), which is what structurally guarantees they cannot reach balances, budgets, reports
or search. Two new `:screen:*` modules provide the review/enrichment surface and the
consent/mapping settings surface, following the existing four-file `ComposeViewModel` pattern.

All design decisions, with rejected alternatives, are in [research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin (JDK 17 target, `libs.versions.toml` `jvm-target = "17"`)

**Primary Dependencies**: Jetpack Compose (runtime used in ViewModels), Hilt, Arrow (`either`/
`Exact`), Room 2.8.4, DataStore, WorkManager 2.11.2 + `androidx.hilt:hilt-work`, kotlinx-collections
-immutable, Timber. **No new third-party dependency is introduced** — everything needed is already
in `gradle/libs.versions.toml`. `android.provider.Telephony` is platform API, not a dependency.

**Storage**: existing `IrisRoomDatabase` (`iriswallet.db`), migrated 130 → 131 with four additive
tables (`financial_senders`, `captured_transactions`, `processed_messages`,
`counterparty_categories`); schema exported to
`shared/data/core/schemas/com.iris.data.db.IrisRoomDatabase/131.json`. Three new DataStore keys for
the master toggle, the fee-category id and the import timestamp.

**Testing**: JUnit4 + Kotest matchers + MockK; table-driven parser corpus in
`:shared:sms:parser`; Molecule-backed `viewModel.runTest(events) { }` from `:shared:ui:testing`;
Paparazzi (`PaparazziScreenshotTest` + `TestParameterInjector` + `PaparazziTheme`) for the review
screen; Room `MigrationTestHelper` androidTest via `./scripts/integrationTests.sh`.

**Target Platform**: Android, `min-sdk = 28`, `compile-sdk = 37`. Kenya-only senders; KES only.

**Project Type**: Mobile app — multi-module Gradle KTS Android project (~40 modules), no backend.

**Performance Goals**: captured payment visible ≤ 10 s after delivery (SC-005) — receiver-path
budget is parse + ≤ 3 Room writes, single-digit ms; 30-day import of 5,000 messages completes
without ANR (SC-009) via 200-row paging with `yield()` between pages; the review list and its
view-state stay Compose-skippable (the `:ci-actions:compose-stability` gate).

**Constraints**: **fully offline / on-device** — no message content or derivative may leave the
device (FR-006), and no message body may be persisted anywhere (this is why WorkManager is *not*
used for live capture). **Never throw** — every fallible path returns Arrow `Either` with a sealed
error type. Pending items must have zero effect on balances/budgets/reports/search (FR-021a). No
default-account fallback (FR-027a). Historical window hard-bounded to 30 days, enforced in the SQL
selection (FR-030). Must not add code to `:temp:legacy-code` or `:temp:old-design`.

**Scale/Scope**: 4 new Gradle modules, 2 new screens, 4 new tables, 1 DB migration, ~7 new use
cases, 3 provider rule sets at launch (M-PESA, DTB, KCB), a parser corpus of ≥ 200 real messages,
35 functional requirements across 5 prioritised user stories.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is **an unfilled template** — it still contains
`[PRINCIPLE_1_NAME]` placeholders and has never been ratified. There is therefore **no project
constitution to gate against**, and no violation can be derived from it.

In its place, this plan is gated against the project's de-facto ratified engineering principles:
`docs/Guidelines.md` and `docs/guidelines/{Architecture,Screen-Architecture,Data-Modeling,
Error-Handling,Unit-Testing}.md`, plus the mechanical gates CI enforces.

| Gate | Source | Pre-design | Post-design (after Phase 1) |
|---|---|---|---|
| **Minimal complexity / 80-20** — simplest thing that works, PR scoped to one issue | `docs/Guidelines.md` | PASS | **PASS** — reuses the existing DB, the existing `Transaction`/`Account`/`Category` model, the existing WorkManager and Hilt wiring; adds no library and no parallel ledger. The 4 new modules are justified in Complexity Tracking below. |
| **Layering** Data → Domain → UI, `model → data → domain → ui/screen` | `docs/guidelines/Architecture.md` | PASS | **PASS** — dependency graph in §"Module graph" is acyclic and strictly downward. |
| **No throwing; `Either` + sealed errors** | `docs/guidelines/Error-Handling.md` | PASS | **PASS** — `SmsParseError`, `SmsCaptureError`, `ConfirmCaptureError`; a non-transactional message is an ordinary `Left`, not an exception. |
| **ADTs + `Exact` value classes; impossible states unrepresentable** | `docs/guidelines/Data-Modeling.md` | PASS | **PASS** — `CapturedKind`, `ReviewStatus` (derived, not stored), `MoneyDirection`; `SenderId`, `ProviderReference`, `MessageFingerprint`, `CounterpartyKey`, and `PositiveDouble` making zero amounts unconstructible. |
| **Screen pattern**: 4 files, `ComposeViewModel<State, Event>`, Compose runtime not Flow, `@Immutable`/`ImmutableList` view-state, `isLegacy = false` | `docs/guidelines/Screen-Architecture.md`, `.github/copilot-instructions.md` | PASS | **PASS** — both new screens conform; no domain type crosses into view-state. |
| **Testing**: JUnit4 + Kotest + MockK, Given/When/Then, backticked names, prefer provided fakes | `docs/guidelines/Unit-Testing.md` | PASS | **PASS** — strategy in research.md D15; parser corpus is the SC-001/SC-004 gate. |
| **Build conventions**: convention plugin, typesafe project accessors, versions only from `libs.versions.toml`, register in `settings.gradle.kts` (+ `app/build.gradle.kts` for screens) | `.github/copilot-instructions.md` | PASS | **PASS** — all four new modules apply `iris.feature`; every dependency uses `projects.*`; no version literal is added. |
| **Don't extend `:temp:*`** | `.github/copilot-instructions.md` | PASS | **PASS** — the notification channel is defined fresh in `:shared:sms:capture` rather than added to `IrisNotificationChannel` in `:temp:legacy-code`. |
| **CI gates stay green**: `detekt` (allRules), `lintRelease`, `testDebugUnitTest`, `verifyPaparazziDebug`, Compose stability | `.github/workflows`, `docs/CI-Troubleshooting.md` | PASS | **PASS** — see [quickstart.md](./quickstart.md); Paparazzi fixtures are static, view-state is stable. |

**Result: no violations, no justification required beyond the module count tracked below.**

## Project Structure

### Documentation (this feature)

```text
specs/001-sms-transaction-capture/
├── plan.md                                  # This file
├── spec.md                                  # Input
├── research.md                              # Phase 0 — 15 decisions with rejected alternatives
├── data-model.md                            # Phase 1 — domain ADTs, entities, view-state
├── quickstart.md                            # Phase 1 — build/run/validate guide
├── contracts/                               # Phase 1
│   ├── sms-parser-contract.md               #   engine API, rule model, corpus contract
│   ├── persistence-contract.md              #   DDL, DAOs, repositories, migration, atomicity
│   └── ui-and-platform-contract.md          #   screens, navigation, receiver, worker, permissions
├── checklists/requirements.md               # Pre-existing, green
└── tasks.md                                 # Phase 2 — NOT created by /speckit.plan
```

### Source Code (repository root)

```text
settings.gradle.kts                                  # +4 include(...) lines
app/
├── build.gradle.kts                                 # +2 implementation(projects.screen.*)
├── src/main/AndroidManifest.xml                     # +RECEIVE_SMS, +READ_SMS, +review intent-filter
└── src/main/java/com/iris/IrisNavGraph.kt           # +2 branches in the when(screen)

shared/sms/parser/                                   # NEW — iris.feature, com.iris.sms.parser
├── build.gradle.kts
└── src/main/java/com/iris/sms/parser/
    ├── SmsParser.kt                                 # interface + RuleDrivenSmsParser
    ├── SmsParseError.kt                             # sealed error ADT
    ├── RawSmsMessage.kt  ParsedMessage.kt
    ├── MessageFingerprints.kt
    ├── model/  RuleModel.kt                         # SenderRuleSet, MessageRule, ExclusionRule, FeeRule
    ├── primitive/  AmountParser.kt  SmsDateTimeParser.kt
    │               CounterpartyNormalizer.kt  PromoTailStripper.kt
    └── rules/  SmsRuleCatalogModule.kt  RuleCatalogSource.kt
                 MpesaRules.kt  DtbRules.kt  KcbRules.kt      # ← one file per provider
    src/test/java/com/iris/sms/parser/
    ├── corpus/  SmsCorpus.kt  MpesaCorpus.kt  DtbCorpus.kt  KcbCorpus.kt  NegativeCorpus.kt
    ├── SmsParserCorpusTest.kt                        # table-driven; SC-001 + SC-004 gate
    └── AmountParserTest.kt  SmsDateTimeParserTest.kt
        CounterpartyNormalizerTest.kt  MessageFingerprintsTest.kt

shared/sms/capture/                                  # NEW — iris.feature, com.iris.sms.capture
├── build.gradle.kts
├── src/main/AndroidManifest.xml                     # <receiver> for SMS_RECEIVED
└── src/main/java/com/iris/sms/capture/
    ├── SmsCaptureReceiver.kt                        # goAsync() only; no parsing, no DB
    ├── SmsCaptureCoordinator.kt                     # app-scoped coroutine scope
    ├── SmsCaptureGate.kt                            # toggle AND permission
    ├── SmsInboxDataSource.kt                        # Telephony.Sms.Inbox, 30-day SQL bound
    ├── SmsImportWorker.kt                           # @HiltWorker, paged, setProgress
    └── SmsCaptureNotifier.kt                        # own channel; not :temp:legacy-code

shared/data/model/src/main/kotlin/com/iris/data/model/sms/    # NEW package
├── SmsPrimitives.kt          # SenderId, ProviderReference, MessageFingerprint, CounterpartyKey, RuleSetId
├── CapturedTransaction.kt    # + CapturedKind, ReviewStatus, CapturedEntry, MoneyDirection
├── FinancialSender.kt  CounterpartyCategory.kt  ProcessedMessage.kt

shared/data/core/src/main/java/com/iris/data/
├── db/IrisRoomDatabase.kt                           # version 130 -> 131, +4 entities, +8 DAOs
├── db/entity/{CapturedTransaction,FinancialSender,ProcessedMessage,CounterpartyCategory}Entity.kt
├── db/dao/read/{CapturedTransaction,FinancialSender,ProcessedMessage,CounterpartyCategory}Dao.kt
├── db/dao/write/Write*Dao.kt                        # 4 files
├── db/migration/Migration130to131_SmsCapture.kt
├── di/RoomDbModule.kt                               # +8 @Provides
├── datastore/DatastoreKeys.kt                       # +3 keys
├── repository/{CapturedTransaction,FinancialSender,ProcessedMessage,CounterpartyCategory}Repository.kt
└── repository/mapper/{CapturedTransaction,FinancialSender}Mapper.kt
    src/androidTest/java/com/iris/data/db/IrisRoomDatabaseMigrationTest.kt   # +migrate130to131

shared/domain/src/main/java/com/iris/domain/usecase/sms/       # NEW package
├── CaptureSmsUseCase.kt                 ImportRecentSmsUseCase.kt
├── ConfirmCapturedTransactionUseCase.kt DismissCapturedTransactionUseCase.kt
├── SuggestCategoryUseCase.kt            EnsureTransactionCostCategoryUseCase.kt
└── DetectDuplicateTransactionUseCase.kt
    src/test/java/com/iris/domain/usecase/sms/*Test.kt

shared/ui/navigation/src/main/java/com/iris/navigation/Screens.kt   # +2 data objects

screen/sms-review/                                   # NEW — iris.feature, com.iris.sms.review
└── src/main/java/com/iris/sms/review/
    SmsReviewScreen.kt  SmsReviewViewModel.kt  SmsReviewState.kt  SmsReviewEvent.kt
    src/test/java/com/iris/sms/review/
    SmsReviewViewModelTest.kt  SmsReviewScreenshotTest.kt  SmsReviewFixtures.kt

screen/sms-settings/                                 # NEW — iris.feature, com.iris.sms.settings
└── src/main/java/com/iris/sms/settings/
    SmsCaptureSettingsScreen.kt  SmsCaptureSettingsViewModel.kt
    SmsCaptureSettingsState.kt   SmsCaptureSettingsEvent.kt

screen/settings/src/main/java/com/iris/settings/SettingsScreen.kt   # +1 row -> SmsCaptureSettingsScreen
```

**Structure Decision**: the feature is delivered as **four new Gradle modules** plus **additive**
changes to seven existing files, following the repository's five-group layout
(`:app`, `:screen:*`, `:shared:*`, `:widget:*`, `:ci-actions:*`) exactly:

- **`:shared:sms:parser`** — the pure, Android-free parsing engine and provider rule catalog. Sits
  at the `data:model` layer so its ~200-case corpus runs as fast JVM unit tests without Room, Ktor
  or Android. (research.md D1, D2)
- **`:shared:sms:capture`** — the Android edge: broadcast receiver, capture coordinator, permission
  gate, SMS inbox data source, import worker, notifier. Isolated so `:app` stays thin wiring.
  (research.md D5, D6)
- **`:screen:sms-review`** and **`:screen:sms-settings`** — one module per screen, matching the
  existing 21 `:screen:*` modules. (research.md D13)
- Domain types go into the existing `:shared:data:model`, persistence into the existing
  `:shared:data:core` (where `IrisRoomDatabase` and its exported schemas live), use cases into the
  existing `:shared:domain`. **No parallel ledger and no second database.** (research.md D4)

### Module graph (all edges use typesafe project accessors, all modules apply `iris.feature`)

```text
:shared:data:model  ──────────────► (:shared:sms:parser, :shared:data:core, everything)
        ▲
:shared:base ───────────────────────► used by all
        │
:shared:sms:parser ─────► :shared:data:model, :shared:base
        ▲
:shared:data:core ──────► :shared:data:model (api), :shared:base
        ▲
:shared:domain ─────────► :shared:data:core, :shared:sms:parser, :shared:base
        ▲
:shared:sms:capture ────► :shared:domain, :shared:data:core, :shared:sms:parser, :shared:base
        ▲                  + libs.androidx.work, libs.hilt.work
:screen:sms-review ─────► :shared:domain, :shared:data:core, :shared:ui:core, :shared:ui:navigation
:screen:sms-settings ───► :shared:domain, :shared:data:core, :shared:ui:core, :shared:ui:navigation,
                          :shared:sms:capture   (to enqueue SmsImportWorker + read SmsCaptureGate)
        ▲
:app ───────────────────► :screen:sms-review, :screen:sms-settings, :shared:sms:capture
```

Acyclic and strictly downward. Neither new screen module depends on `:temp:legacy-code` or
`:temp:old-design` — unlike every existing screen module, these two are clean.

Build files, e.g. `shared/sms/parser/build.gradle.kts`:

```kotlin
plugins { id("iris.feature") }
android { namespace = "com.iris.sms.parser" }
dependencies {
    api(projects.shared.data.model)
    implementation(projects.shared.base)
    testImplementation(projects.shared.data.modelTesting)
}
```

`:shared:data:core` additionally keeps its existing `iris.room` and `iris.integration.testing`
plugins — no plugin change is needed there, so schema export and the migration test keep working.

## Phasing — P1 alone is a shippable MVP

Phases map 1:1 onto the spec's prioritised user stories. Each is independently testable via the
matching scenario in [quickstart.md](./quickstart.md).

### Phase A — P1 MVP: capture a payment and its fee (US1 + US2)

Everything needed for a user to receive a payment SMS and end up with correct transactions.

1. `:shared:sms:parser` end to end: rule model, engine, primitive parsers, M-PESA + DTB + KCB rule
   sets, fingerprinting, and the corpus tests (V2).
2. `:shared:data:model` sms package; `:shared:data:core` entities, DAOs, repositories, mappers,
   `Migration130to131_SmsCapture`, exported `131.json`, migration androidTest.
3. `:shared:domain`: `CaptureSmsUseCase`, `EnsureTransactionCostCategoryUseCase`,
   `ConfirmCapturedTransactionUseCase`, `DismissCapturedTransactionUseCase`,
   `DetectDuplicateTransactionUseCase`.
4. `:shared:sms:capture`: receiver, coordinator, gate, notifier. Permissions in the app manifest.
5. `:screen:sms-review` with confirm/dismiss and the fee shown on its principal, wired into
   `Screens.kt` + `IrisNavGraph.kt` + `app/build.gradle.kts`.
6. A temporary entry point: the Settings row (built fully in Phase B) plus the notification deep
   link.

**Shippable because**: FR-001's default-off is honoured by the DataStore default, so a P1 build with
the toggle flipped by a developer captures and commits correct transactions with fees. Delivers
SC-001, SC-003, SC-004, SC-005, SC-006.

*P1 caveat*: with no settings UI yet, sender mapping is seeded by a one-time default catalog
(`MPESA`, `DTB-KENYA`, `KCB` with `accountId = null`), so every captured item lands in
`NeedsAccount` until Phase B — which is the correct, spec-mandated behaviour (FR-027a), not a
degradation.

### Phase B — P2: enrichment and control (US3 + US4)

7. `SuggestCategoryUseCase` + `counterparty_categories` write-back; category picker, description
   field, and inline amount/date/counterparty/account editing in `:screen:sms-review`.
8. `:screen:sms-settings`: rationale panel, `RECEIVE_SMS` request, master toggle, per-sender add /
   map / enable / remove.
9. One new row in `screen/settings/.../SettingsScreen.kt` with the pending-count badge.

Delivers SC-002, SC-007, SC-008, SC-010.

### Phase C — P3: bounded historical import (US5)

10. `SmsInboxDataSource` + `SmsImportWorker` + `ImportRecentSmsUseCase`, the `READ_SMS` request, and
    the import UI states in `:screen:sms-settings`.

Delivers SC-009.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| `RECEIVE_SMS`/`READ_SMS` are Google Play **restricted permissions** | Would block a future Play listing | APK-via-GitHub-Releases distribution today (`.github/workflows/internal_release.yml`) needs no exemption. If Play is pursued: gate the feature out of that variant or file a Permissions Declaration. Recorded, not a blocker. |
| Provider wording changes silently break a rule | Captures stop; user sees nothing | Rules are data with a mandatory corpus row each; `NoMatchingPattern` is logged distinctly from `ExcludedByRule` so a regression is visible; the `RuleCatalogSource` seam allows a future updatable catalog. |
| Process killed inside `goAsync()` | One message missed | Accepted: nothing is silently *wrong*, and the 30-day import (Phase C) is the backstop. Documented in research.md D5. |
| `Migration130to131` drifts from the entities | Crash on upgrade | `runMigrationsAndValidate` against the exported `131.json` fails the build; migration test is part of `./scripts/integrationTests.sh`. |
| A false-positive parse commits a wrong transaction | Corrupted balances | Structurally impossible: nothing reaches `transactions` without an explicit user confirm (FR-021), and pending rows live in separate tables. |
| Paparazzi flakiness from time-dependent data | Red CI | View-state carries **pre-formatted** strings; fixtures use hard-coded literals; `PaparazziScreenshotTest` already pins `Locale.US`. |

## Complexity Tracking

> No constitution exists to violate. This table records the two places where the design adds
> structure beyond the smallest possible change, so a reviewer can challenge them directly.

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| **4 new Gradle modules** rather than folding the feature into `:shared:data:core` + `:screen:settings` | The repo's own convention is one module per screen (21 exist) and layered `:shared:*` modules; splitting `parser` from `capture` is what keeps the ~200-case corpus a pure-JVM test with no Android or Room on the classpath | Folding the parser into `:shared:data:core` drags Room/Ktor/DataStore into every corpus test run and grows an already-large module; folding both screens into `:screen:settings` couples the P1 MVP to the P2 permission plumbing |
| **4 new Room tables** rather than flags on `TransactionEntity` | FR-021a and FR-033 demand pending items be invisible to balances/budgets/reports/search **and** confirmed items be indistinguishable from manual ones — both are guaranteed structurally by separate tables | Adding `isPending`/`source` columns means auditing and editing every existing query in `TransactionDao`, `CalcWalletBalanceAct`, budgets, reports, search and CSV export; one missed `WHERE` silently corrupts a balance, and the `source` column would violate FR-033 permanently |

Two deliberate scope reductions are recorded rather than built:

1. **Updatable rule catalog** — the `RuleCatalogSource` seam and serialisable rule shape ship now;
   the asset/remote loader does not (research.md D2).
2. **Home-screen pending badge** — deferred until `:screen:home` leaves `:temp:old-design`; FR-021a
   is met for now via the notification and the Settings-row count (research.md D13).
