# Feature Specification: Automatic SMS Transaction Capture

**Feature Branch**: `001-sms-transaction-capture`

**Created**: 2026-07-26

**Status**: Draft

**Input**: User description: "Add a feature where the app automatically reads SMS confirmation messages, such as M-PESA or bank SMS, that contain transaction details such as amount spent as well as transaction cost. This is very common in Kenya, where most transactions are done via mobile payments with M-PESA or bank apps. The application should be able to automatically read SMSes and retrieve the amount paid as well as the transaction cost (as its own spend category), and easily allow the user to add additional details like category and description. The goal is to make it very convenient to capture spend details."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture a payment from a confirmation SMS (Priority: P1)

A user in Kenya pays a merchant with M-PESA. Seconds later the confirmation SMS arrives. Instead of switching to IrisWallet and typing the amount, payee, and date by hand, the user opens the app (or taps a notification) and finds the payment already extracted and waiting: amount, payee, date and time, and reference code. The user confirms it and the expense is recorded.

**Why this priority**: This is the core of the feature. On its own it removes almost all of the manual data entry that causes users to abandon expense tracking. Every other story builds on the extracted payment.

**Independent Test**: Deliver a known M-PESA payment SMS to the device with the feature enabled, then verify a matching expense candidate appears with the correct amount, payee, and timestamp, and that confirming it produces an expense visible in the transaction list and reflected in the account balance.

**Acceptance Scenarios**:

1. **Given** the user has granted SMS access and mapped their M-PESA sender to an account, **When** the SMS `UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM. New M-PESA balance is Ksh1,242.02. Transaction cost, Ksh0.00.` arrives, **Then** a captured expense of KES 1,350.00 to "james Kinyua Mwangi" dated 25 Jul 2026 19:50 is available for the user, carrying reference `UGP7B0ITE4`.
2. **Given** the SMS `ALERT: Your account no. 5XXXXX5001 has been debited with KES 540.25 for a POS PURCHASE at GITHUB, INC. SAN FRANCISCO CA on 25/07/2026.` arrives from the user's bank, **When** the app processes it, **Then** an expense of KES 540.25 to "GITHUB, INC." dated 25 Jul 2026 is captured against the account mapped to that bank sender.
3. **Given** the same confirmation SMS is delivered twice, or a duplicate exists with the same reference code, **When** the app processes it, **Then** only one expense is captured and the duplicate is discarded silently.
4. **Given** an SMS arrives while the app is closed, **When** the user next opens the app, **Then** the payment captured from that SMS is present with the timestamp taken from the message, not from when the app was opened.
5. **Given** the user has not granted SMS access, **When** payment messages arrive, **Then** the app captures nothing and continues to work exactly as it does today.

---

### User Story 2 - Record transaction costs and charges as their own spending (Priority: P1)

Kenyan mobile money and bank transfers carry per-transaction fees that users rarely track, so their real spending is understated. When a confirmation SMS reports a transaction cost or charge, the app records that fee as a separate expense filed under a dedicated transaction-cost category, so the user can see at a glance how much they spend on fees each month.

**Why this priority**: The user called this out explicitly, and it is the part no manual workflow captures today. It is a small, independently valuable slice: even if nothing else shipped, seeing monthly fee spend would be new insight.

**Independent Test**: Deliver a transfer SMS that reports both a principal amount and a non-zero charge, then verify two separate expenses are produced — the principal and the fee — with the fee assigned to the transaction-cost category, and verify a zero-value fee produces no fee expense at all.

**Acceptance Scenarios**:

1. **Given** the SMS `DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna ... Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT. Charges 59.76 KES` arrives, **When** the app processes it, **Then** it captures an expense of KES 6,000.00 for the transfer and a separate expense of KES 59.76 filed under the transaction-cost category, both dated 25 Jul 2026 17:41 and linked to the same reference.
2. **Given** the SMS `... Your SEND TO M-PESA request of KES 13,000.00 ... Transaction cost KES 66.00 Incl. Tax Amount KES 8.25. M-PESA REF: UGO680QNMS.` arrives, **When** the app processes it, **Then** it captures KES 13,000.00 as the transfer and KES 66.00 as the transaction cost; the tax component is retained as detail on the fee rather than becoming a third expense.
3. **Given** an SMS reports `Transaction cost, Ksh0.00`, **When** the app processes it, **Then** no transaction-cost expense is created.
4. **Given** the transaction-cost category does not yet exist in the user's category list, **When** the first fee is captured, **Then** the app creates it once and reuses it for every subsequent fee.
5. **Given** a captured payment and its fee, **When** the user discards or deletes the payment, **Then** the app offers to remove the linked fee together with it so the two never drift apart.

---

### User Story 3 - Add category and description in a few taps (Priority: P2)

The SMS gives the app the money facts but not the meaning. The user needs to say "this was groceries" or "this was rent for July". The app presents the captured payment with a category picker and a description field pre-filled with what it could infer from the payee, so completing the entry takes seconds rather than a full manual form.

**Why this priority**: Without it, captured transactions land uncategorised and the user's reports stay unusable — but the capture itself (P1) already delivers value, so this can follow.

**Independent Test**: Capture a payment, open it, assign a category and description, save, and verify the transaction list and category reports reflect the choice; then capture a second payment from the same payee and verify the previously chosen category is suggested.

**Acceptance Scenarios**:

1. **Given** a captured payment awaiting detail, **When** the user opens it, **Then** the amount, payee, date, and account are already filled in and the user only needs to choose a category and optionally edit the description.
2. **Given** the user previously filed a payment to "FRANK INN KIKUYU" under "Food & Drinks", **When** a new payment to the same payee is captured, **Then** that category is pre-selected, and the user can override it.
3. **Given** the user overrides a suggested category, **When** the next payment from that payee is captured, **Then** the most recent choice is the one suggested.
4. **Given** a captured payment, **When** the user edits the amount, date, payee, or account before saving, **Then** the edited values are what get saved.
5. **Given** a captured payment the user does not want to track, **When** the user dismisses it, **Then** it is removed from the pending list and is not re-captured if the same SMS is re-processed.

---

### User Story 4 - Set up and control SMS capture (Priority: P2)

Before anything is read, the user is told plainly what will be accessed and why, grants permission, and tells the app which of their IrisWallet accounts corresponds to each message sender (their M-PESA line, each bank). They can turn the whole feature off, or disable an individual sender, at any time.

**Why this priority**: Capture cannot be trusted or even correctly attributed without it, but it can be built and tested after the parsing core exists.

**Independent Test**: Walk through enabling the feature from settings, granting permission, mapping one sender to an account; then disable the feature and verify no further messages are captured.

**Acceptance Scenarios**:

1. **Given** the feature is off (the default for existing and new users), **When** the user opens the SMS capture setting, **Then** they see an explanation of what is read, that it stays on the device, and a control to enable it.
2. **Given** the user enables the feature, **When** the system permission prompt is declined, **Then** the feature remains off, the app explains that capture cannot work without it, and offers a way to retry.
3. **Given** a message arrives from a sender the user has not mapped to an account, **When** the app captures it, **Then** the captured payment is still presented but flagged as needing an account before it can be saved.
4. **Given** the user disables SMS capture, **When** new payment messages arrive, **Then** nothing is captured, and payments already saved as transactions are untouched.
5. **Given** the user has more than one SIM or several bank senders, **When** they configure the feature, **Then** each sender can be mapped to a different account and enabled or disabled independently.

---

### User Story 5 - Import recent past messages (Priority: P3)

When first enabling the feature, the user can pull in payment messages from the previous 30 days that are already sitting in their inbox, so they do not start from an empty history.

**Why this priority**: A one-time convenience. Everything else works without it, and it carries the most privacy weight, so it ships last, stays opt-in, and is bounded to a recent window.

**Independent Test**: With a device inbox containing known past payment messages, run the import and verify the expected payments are captured, none are duplicated against already-saved transactions, and messages older than 30 days are ignored.

**Acceptance Scenarios**:

1. **Given** the user opts into importing past messages, **When** the import runs, **Then** payment messages from the previous 30 days are captured and presented for review in one batch, and older messages are not read.
2. **Given** an imported message corresponds to a transaction the user already entered manually with the same amount, account, and date, **When** the import runs, **Then** the app flags it as a probable duplicate rather than silently creating a second transaction.
3. **Given** the import is running over a large inbox, **When** the user leaves the screen, **Then** the app does not lose progress or freeze, and the user is told when it finishes.

---

### Edge Cases

- **Zero-value amounts**: A message reporting a `0.00` principal or a `Ksh0.00` transaction cost must not produce a transaction; the app only records positive amounts.
- **Reversals and failures**: Messages announcing a failed, cancelled, or reversed transaction must not create an expense; where a reversal references a previously captured transaction, the user is told rather than left with a phantom expense.
- **Non-transactional messages from the same sender**: Balance enquiries, loan-limit adverts, statement notices, OTP codes, and the promotional tails on real confirmations (`Download My OneApp on https://...`, `Dial *522#`) must never produce a transaction.
- **Money received**: Deposits and incoming payments from the same senders are recognised as income, not expenses, so balances stay correct.
- **Unrecognised formats**: A message that looks financial but cannot be parsed with confidence is not guessed at; the user is not shown a half-filled entry with wrong numbers.
- **Format variation within one sender**: The same provider uses several wordings, date formats (`25/7/26`, `25/07/2026`, `25 Jul 2026`, `2026-07-24 07:19:54 PM`), currency prefixes (`Ksh`, `KES`), and thousands separators; all must resolve to the same amount and instant.
- **Time zones**: Times are stated in East Africa Time, sometimes explicitly (`17:41 EAT`), sometimes not; captured transactions must land on the correct calendar day for the user regardless of device time-zone settings.
- **Two-digit years**: `25/7/26` must resolve to 2026, not 1926 or 2126.
- **Masked identifiers**: Account and phone numbers arrive partially masked (`5XXXXX5001`, `254****956`); the app must match senders and accounts without needing the full number.
- **Payee names with noise**: Trailing digits and punctuation in payee names (`james Kinyua Mwangi9.`) must be cleaned up before being shown or used for category suggestions.
- **Duplicate delivery**: The same message delivered twice, re-processed after a restart, or seen by both live capture and historical import must yield exactly one transaction.
- **Foreign merchants**: A card purchase at an overseas merchant billed in shillings is recorded in shillings; the merchant's location is description detail only.
- **Permission revoked or feature disabled mid-stream**: Already-captured but unconfirmed items remain accessible; no new messages are read.
- **Long backlog**: A device that has been offline or an inbox with thousands of messages must not cause the app to hang or drop messages.
- **Ambiguous fee attribution**: A charge notification that arrives as its own message, separate from the payment it belongs to, is recorded as a standalone transaction-cost expense rather than being discarded.

## Requirements *(mandatory)*

### Functional Requirements

**Capture and permission**

- **FR-001**: SMS capture MUST be off by default and MUST only begin after the user explicitly enables it and grants the device permission to read messages.
- **FR-002**: The app MUST explain, before requesting permission, what message content is read, what is extracted, and that message content is processed and retained only on the device.
- **FR-003**: The app MUST allow the user to disable SMS capture entirely at any time, and to enable or disable capture per sender.
- **FR-004**: The app MUST only inspect messages from senders the user has configured as financial senders, and MUST ignore all other messages.
- **FR-005**: The app MUST capture qualifying messages that arrive while the app is not running, so that no payment is missed.
- **FR-006**: The app MUST NOT transmit message content, or anything derived from it, off the device.

**Extraction**

- **FR-007**: For each qualifying message, the app MUST extract the transacted amount, the currency, the date and time of the transaction, the counterparty (payee, merchant, or sender of funds), the provider reference code, and the transaction cost or charge where present.
- **FR-008**: The app MUST determine the direction of each transaction — money out (expense) or money in (income) — and record it accordingly.
- **FR-009**: The app MUST correctly interpret amounts that use thousands separators and decimals, and both the `Ksh` and `KES` currency markers, as the same currency.
- **FR-010**: The app MUST correctly interpret the date and time formats used by Kenyan mobile money and bank senders, resolving two-digit years to the current century and interpreting stated or implied East Africa Time so the transaction falls on the correct local day.
- **FR-011**: The app MUST discard promotional and instructional text appended to confirmation messages and MUST NOT let it affect the extracted values or the description.
- **FR-012**: The app MUST NOT create a transaction from a message that reports a failed, cancelled, reversed, or zero-value transaction.
- **FR-013**: The app MUST NOT create a transaction when it cannot extract an amount and a date with confidence; such messages MUST be skipped rather than guessed at.
- **FR-014**: The app MUST normalise counterparty names by removing trailing noise characters and standardising capitalisation before displaying them.
- **FR-015**: The app MUST retain the provider reference code with the saved transaction so a user can trace it back to the original message.

**Transaction cost**

- **FR-016**: When a message reports a transaction cost or charge greater than zero, the app MUST record it as a separate expense, distinct from the principal amount.
- **FR-017**: The app MUST file every captured fee under a single dedicated transaction-cost category, creating that category once if it does not already exist and reusing it thereafter.
- **FR-018**: The app MUST link a captured fee to the payment it came from, and MUST offer to remove the fee when the user discards or deletes that payment.
- **FR-019**: Where a message reports a tax component within the transaction cost, the app MUST record the total cost as the fee amount and retain the tax figure as detail on that fee rather than as an additional transaction.
- **FR-020**: A fee arriving in a message with no identifiable principal amount MUST still be recorded as a standalone transaction-cost expense.

**Review and enrichment**

- **FR-021**: The app MUST hold every captured transaction in a pending review list and MUST NOT commit it to the ledger until the user confirms it. Each pending item MUST be presented with the extracted amount, counterparty, date, account, and reference already filled in.
- **FR-021a**: Pending items MUST NOT affect account balances, budgets, reports, or search results until confirmed, and the app MUST show how many items are awaiting review.
- **FR-022**: Users MUST be able to assign a category and edit the description of a captured transaction without re-entering any of the extracted values.
- **FR-023**: Users MUST be able to correct the amount, date, counterparty, and account of a captured transaction before it is committed.
- **FR-024**: The app MUST suggest a category for a captured transaction based on the category the user last chose for the same counterparty, and MUST let the user override the suggestion.
- **FR-025**: Users MUST be able to dismiss a captured transaction they do not want to track, and dismissed items MUST NOT reappear.
- **FR-026**: The app MUST notify the user when new transactions have been captured, and the notification MUST lead directly to reviewing them.

**Accounts and duplicates**

- **FR-027**: The app MUST let the user map each financial sender to one of their existing accounts, so captured transactions post to the right balance.
- **FR-027a**: When a message arrives from a sender that has no account mapping, the app MUST still capture it, MUST flag it as needing an account, and MUST NOT allow it to be confirmed until the user selects one. The app MUST NOT fall back to a default account.
- **FR-028**: The app MUST recognise a message it has already processed — by reference code, or by message identity where no reference exists — and MUST NOT create a second transaction from it.
- **FR-029**: The app MUST flag a captured transaction as a probable duplicate when a transaction with the same amount, account, and date already exists, and MUST let the user decide whether to keep it.

**History**

- **FR-030**: The app MUST offer, when the feature is first enabled, an optional one-time import of payment messages received in the previous 30 days. Messages older than 30 days MUST NOT be read.
- **FR-031**: An import of past messages MUST run without blocking the user from using the rest of the app, and MUST report when it has finished and how many transactions it captured.

**Behaviour when unavailable**

- **FR-032**: All existing app functionality MUST continue to work unchanged when SMS capture is disabled, unsupported, or denied permission.
- **FR-033**: Transactions created through SMS capture MUST be indistinguishable from manually entered transactions everywhere else in the app — reports, budgets, balances, search, and export.

### Key Entities

- **Financial sender**: A message sender the user has identified as a source of transaction confirmations (their mobile money line, a specific bank). Holds the sender identity, the account it maps to, and whether capture is enabled for it.
- **Captured transaction**: A transaction extracted from a single message, holding the amount, direction, currency, transaction instant, counterparty, provider reference, the account it will post to, its review state, and any user-supplied category and description. Becomes a normal transaction once committed.
- **Captured fee**: The transaction-cost portion extracted from a message, holding its own amount, any tax component, and a link to the captured transaction it belongs to. Becomes a normal expense in the transaction-cost category.
- **Transaction-cost category**: The single dedicated spending category under which all captured fees are filed, so fee spending can be reported on separately.
- **Counterparty-to-category memory**: The user's last category choice for a given counterparty, used to pre-select a category the next time that counterparty appears.
- **Processed-message record**: The minimal record of which messages have already been turned into transactions, so re-delivery or re-import cannot duplicate them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the payment and transfer confirmations issued by Kenya's major mobile money and bank senders, at least 95% of messages yield the correct amount, transaction cost, date, and direction without the user correcting any value.
- **SC-002**: A user records a captured payment, complete with category and description, in under 10 seconds and no more than three taps, compared with the roughly 45 seconds and a dozen interactions a manual entry takes today.
- **SC-003**: Zero duplicate transactions are produced across message re-delivery, app restart, and historical import in a full end-to-end run over a representative message set.
- **SC-004**: No message reporting a failed, reversed, zero-value, promotional, or non-transactional event produces a transaction, across a representative sample of at least 200 real messages.
- **SC-005**: A newly arrived payment is available to the user within 10 seconds of the message being delivered to the device.
- **SC-006**: After a month of use, users can see their total spend on transaction fees as a single figure, which they could not obtain at all before this feature.
- **SC-007**: Enabling the feature — from opening the setting to having a sender mapped and capture running — takes under 90 seconds.
- **SC-008**: Users tracking their spending through captured messages record at least three times as many transactions per month as they did entering them by hand.
- **SC-009**: Importing a 30-day window containing 5,000 messages completes without the app becoming unresponsive and without loss of already-captured items.
- **SC-010**: With the feature disabled or permission denied, every existing app capability behaves exactly as it did before the feature existed.

## Assumptions

- **Scope is Kenyan mobile money and bank SMS.** M-PESA and the major Kenyan banks (as illustrated by the Safaricom, DTB, and KCB examples supplied) are the initial target. Other countries, providers, email receipts, and in-app notification reading are out of scope for this feature.
- **Shillings only for the first release.** All example messages are denominated in KES. Multi-currency capture, and conversion for card purchases billed abroad, are not addressed here.
- **Recognition is rule-driven and updatable.** The app recognises messages using maintained patterns per sender rather than a general-purpose or machine-learned parser; providers change wording periodically and the patterns are expected to be updated over time.
- **Everything stays on the device.** Message content is never uploaded, and only the extracted transaction facts (amount, fee, counterparty, instant, reference) are retained after processing.
- **Existing app concepts are reused.** Captured items become the app's existing transaction, account, and category types; no parallel ledger is introduced. The transaction-cost category is an ordinary category the user can rename, recolour, and report on.
- **Amounts are positive by construction.** The app's transaction model admits only positive values, which is why zero-value amounts and zero fees produce no transaction at all.
- **Distribution.** The app is distributed as an APK through its GitHub releases, so the SMS-reading permission does not depend on an app-store exemption. Should store distribution be pursued, this feature may need to be gated or the store's restricted-permission process followed — treated as a release concern, not a functional one.
- **Nothing is recorded without the user's say-so.** Every captured item waits in a pending list and only becomes a real transaction when the user confirms it, so a mis-parsed message can never silently corrupt balances or reports.
- **Users hold their own accounts.** The user has already created accounts in the app representing their M-PESA line and bank accounts, or will create them during setup. Captured items from a sender with no account mapping wait for the user to choose one rather than defaulting anywhere.
- **Income capture is included.** Although the request focuses on spend, incoming-money messages from the same senders are recognised as income, because ignoring them would leave the mapped account balances wrong.
