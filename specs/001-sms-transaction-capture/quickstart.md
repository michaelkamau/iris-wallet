# Quickstart: Validating Automatic SMS Transaction Capture

**Feature**: `001-sms-transaction-capture` | **Branch**: `001-sms-transaction-capture`

How to build, run and prove this feature works. Every command is run from the repository root.
This is a validation guide — implementation detail lives in
[plan.md](./plan.md), [data-model.md](./data-model.md) and [contracts/](./contracts/).

---

## Prerequisites

- JDK 17, Android SDK Platform 37 (`compile-sdk`), `min-sdk = 28`
- An emulator or device on API 28+ (an **emulator is required** for scenario V4 — it is the only
  way to inject an SMS without a real Kenyan line)
- No Firebase/Google Services config needed

---

## Fast loop — the checks you run while iterating

```sh
# 1. The parser corpus. This IS success criteria SC-001 and SC-004. Run it constantly.
./gradlew :shared:sms:parser:testDebugUnitTest

# 2. A single parser case while chasing a wording
./gradlew :shared:sms:parser:testDebugUnitTest --tests "*SmsParserCorpusTest*"
./gradlew :shared:sms:parser:testDebugUnitTest --tests "*SmsDateTimeParserTest*"

# 3. Use cases
./gradlew :shared:domain:testDebugUnitTest --tests "com.iris.domain.usecase.sms.*"

# 4. ViewModels
./gradlew :screen:sms-review:testDebugUnitTest
./gradlew :screen:sms-settings:testDebugUnitTest

# 5. Screenshots (record after an intentional UI change, then verify)
./gradlew :screen:sms-review:recordPaparazziDebug
./gradlew :screen:sms-review:verifyPaparazziDebug

# 6. Formatting, before you push
./scripts/detektFormat.sh
```

## Full gate — what CI will run

```sh
./gradlew detekt
./gradlew :app:lintRelease
./gradlew testDebugUnitTest
./gradlew verifyPaparazziDebug
./gradlew assembleDemo -PcomposeCompilerReports=true && ./gradlew :ci-actions:compose-stability:run
./scripts/integrationTests.sh          # includes the Room 130→131 migration test
```

The Room migration test additionally requires the schema to have been exported — after changing any
entity, run `./gradlew :shared:data:core:assembleDebug` and confirm
`shared/data/core/schemas/com.iris.data.db.IrisRoomDatabase/131.json` is created and **committed**.

---

## Build and install

```sh
./gradlew :app:installDebug
```

---

## Validation scenarios

Each scenario maps to a prioritised user story in [spec.md](./spec.md) and can be run
independently once its phase is complete.

### V0 — The feature is invisible until enabled (SC-010, FR-001, FR-032)

1. Install over an existing build with data.
2. Open the app. **Expect**: everything behaves exactly as before; no permission prompt; no new
   screens surfaced anywhere except one new row in Settings.
3. `adb shell dumpsys package com.iris.wallet.debug | grep -A20 "requested permissions"` —
   `RECEIVE_SMS` and `READ_SMS` are *requested* but **not granted**.
4. Inject a payment SMS (see V4). **Expect**: nothing is captured.

### V1 — Enable capture and map a sender (US4, FR-001–FR-003, SC-007)

1. Settings → **SMS transaction capture**.
2. **Expect**: an explanation of what is read, what is extracted, and that content stays on the
   device, *before* any system dialog (FR-002).
3. Accept → grant `RECEIVE_SMS`.
4. Map the sender `MPESA` to an account you own.
5. **Expect**: setup completes in under 90 seconds end to end (SC-007).
6. Deny the permission instead, on a second run. **Expect**: the toggle stays off, an explanation is
   shown, and a retry is offered (Acceptance 4.2).

### V2 — Parser correctness without a device (US1, US2, SC-001, SC-004)

```sh
./gradlew :shared:sms:parser:testDebugUnitTest
```

**Expect**: green, covering every real sample from spec.md —

- `UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM…` →
  KES 1,350.00, expense, `James Kinyua Mwangi`, `2026-07-25T16:50:00Z`, ref `UGP7B0ITE4`,
  **no fee row** (`Transaction cost, Ksh0.00`)
- the DTB transfer → principal 6,000.00 **plus** a 59.76 fee
- the `SEND TO M-PESA` message → principal 13,000.00, fee 66.00 with tax 8.25 as fee detail
- every negative case (OTP, balance enquiry, loan advert, failed, reversed, promo tail, unknown
  wording) → `Either.Left`, no transaction

This is the gate that holds SC-001 (≥95% accuracy) and SC-004 (zero false positives) as rules evolve.

### V3 — Capture a live payment while the app is closed (US1, FR-005, SC-005)

On an emulator with the feature enabled and `MPESA` mapped:

1. Force-stop the app: `adb shell am force-stop com.iris.wallet.debug`
2. Inject the message:

   ```sh
   adb emu sms send MPESA "UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM. New M-PESA balance is Ksh1,242.02. Transaction cost, Ksh0.00."
   ```

3. **Expect** within 10 seconds (SC-005): a notification "1 transaction captured".
4. Tap it. **Expect**: the review list opens with the payment pre-filled — amount, payee, date
   `25 Jul 2026 19:50` (message time, **not** open time — Acceptance 1.4), reference, account.
5. **Expect**: the wallet balance, budgets, reports and search are **unchanged** at this point
   (FR-021a) — verify by checking the balance before and after.

### V4 — Transaction costs become their own expense (US2, FR-016–FR-019)

1. Inject:

   ```sh
   adb emu sms send DTB-KENYA "DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna. Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT. Charges 59.76 KES"
   ```

2. **Expect**: one pending item of KES 6,000.00 showing a linked fee of KES 59.76.
3. Confirm it.
4. **Expect**: **two** transactions in the transaction list — the transfer and a KES 59.76 expense
   filed under a category named "Transaction costs", created once.
5. Repeat with a second fee-bearing message. **Expect**: the same category is reused, not
   duplicated (FR-017).
6. Inject the zero-fee M-PESA message from V3. **Expect**: exactly one transaction, no fee
   (Acceptance 2.3).

### V5 — Category and description in a few taps (US3, FR-022–FR-024, SC-002)

1. Open a pending payment to `FRANK INN KIKUYU`, pick "Food & Drinks", confirm.
2. **Expect**: it appears in the transaction list and in category reports under that category.
3. Inject a second payment to the same payee.
4. **Expect**: "Food & Drinks" is **pre-selected** before you touch anything (FR-024).
5. Override to another category and confirm; capture a third. **Expect**: the newest choice is now
   suggested (Acceptance 3.3).
6. Time the flow from opening the item to confirming. **Expect**: under 10 seconds and no more than
   three taps (SC-002).

### V6 — A sender with no account mapping (FR-027a)

1. Add a financial sender but leave its account unmapped.
2. Inject a payment from it.
3. **Expect**: the item is captured and listed, flagged as needing an account, and the confirm
   affordance is **disabled**.
4. **Expect**: no transaction appears in any account (there is no default-account fallback).
5. Pick an account. **Expect**: confirm becomes available and posts to that account.

### V7 — Duplicates (FR-028, FR-029, SC-003)

1. Inject the exact same message from V3 a second time. **Expect**: still exactly one pending item;
   the duplicate is discarded silently (Acceptance 1.3).
2. Restart the app and re-run the historical import. **Expect**: no new copies.
3. Manually create a transaction, then inject a message with the same amount, account and date.
   **Expect**: the captured item is flagged as a **probable duplicate** and you are asked to decide
   — it is not silently dropped (FR-029).
4. Dismiss a captured item, then re-inject the same message. **Expect**: it does not reappear
   (FR-025).

### V8 — Historical import, bounded to 30 days (US5, FR-030, FR-031, SC-009)

1. Seed the emulator inbox with messages, including some dated more than 30 days ago.
2. Settings → SMS capture → **Import last 30 days**; grant `READ_SMS`.
3. Leave the screen while it runs.
4. **Expect**: the app stays responsive, progress is not lost, and a completion notification
   reports how many transactions were captured (FR-031).
5. **Expect**: messages older than 30 days produced nothing (FR-030) — verify with
   `adb logcat -s SmsImportWorker` that the query window starts at `now - 30d`.
6. **Expect**: no duplicates against items already captured live (SC-003).

### V9 — Turn it off (FR-003, FR-032, Acceptance 4.4)

1. Disable the master toggle.
2. Inject a payment message. **Expect**: nothing captured.
3. **Expect**: previously confirmed transactions are untouched, and any still-pending items remain
   accessible.
4. Revoke `RECEIVE_SMS` from system settings while items are pending. **Expect**: the app does not
   crash; pending items are still reviewable; no new messages are read.

---

## Diagnostics

```sh
adb logcat -s SmsCaptureReceiver SmsCaptureCoordinator SmsImportWorker RuleDrivenSmsParser
```

Log contract (enforced by review, motivated by FR-006): logs may contain a **rule id**, a
**fingerprint**, an **`SmsParseError` type** and counts. They must **never** contain a message body,
a sender's full number, or an extracted counterparty name.

Inspect the pending store on a debug build:

```sh
adb shell "run-as com.iris.wallet.debug sqlite3 databases/iriswallet.db \
  'SELECT id, kind, amount, counterparty, accountId FROM captured_transactions;'"
```

---

## Definition of done for each phase

| Phase | Done when |
|---|---|
| P1 (US1 + US2) | V2, V3, V4, V7.1 pass; `./gradlew testDebugUnitTest detekt verifyPaparazziDebug` green; migration test green |
| P2 (US3 + US4) | V1, V5, V6, V9 pass |
| P3 (US5) | V8 passes |
