# Phase 1 Data Model: Automatic SMS Transaction Capture

**Feature**: `001-sms-transaction-capture` | **Date**: 2026-07-26

Derived from the six Key Entities in [spec.md](./spec.md) and the decisions in
[research.md](./research.md). Everything below follows `docs/guidelines/Data-Modeling.md`: ADTs so
impossible states are unrepresentable, `@JvmInline value class` + Arrow `Exact` for domain values,
primitives allowed in Room entities only.

Layer placement follows the existing stack `model → data → domain → ui/screen`.

---

## 1. Domain model — `:shared:data:model`

New package `shared/data/model/src/main/kotlin/com/iris/data/model/sms/`.
These types are placed beside the existing `Transaction.kt` / `Account.kt` / `Category.kt` because
they are core domain values consumed by `:shared:data:core`, `:shared:domain`, `:shared:sms:parser`
and both screen modules.

### 1.1 Identifiers and exact values — `SmsPrimitives.kt`

```kotlin
package com.iris.data.model.sms

@JvmInline
value class CapturedTransactionId(override val value: UUID) : UniqueId

/** Normalised SMS originating address: trimmed, uppercased, whitespace-collapsed. */
@JvmInline
value class SenderId private constructor(val value: String) {
    companion object : Exact<String, SenderId> {
        override val exactName = "SenderId"
        override fun Raise<String>.spec(raw: String): SenderId {
            val trimmed = NotBlankTrimmedString.from(raw).bind()
            return SenderId(trimmed.value.uppercase().replace(WHITESPACE, " "))
        }
    }
}

/** Provider reference code, e.g. UGP7B0ITE4, AD3EA389C13A7. Uppercased, alphanumeric only. */
@JvmInline
value class ProviderReference private constructor(val value: String) {
    companion object : Exact<String, ProviderReference> {
        override val exactName = "ProviderReference"
        override fun Raise<String>.spec(raw: String): ProviderReference {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value.uppercase()
            ensure(trimmed.all(Char::isLetterOrDigit)) { "'$trimmed' is not alphanumeric" }
            ensure(trimmed.length in REF_LENGTH) { "'$trimmed' length not in $REF_LENGTH" }
            return ProviderReference(trimmed)
        }
        private val REF_LENGTH = 4..32
    }
}

/** Dedupe key. Either "ruleSetId:REFERENCE" or "h:<32 hex chars of sha256>". Never the body. */
@JvmInline
value class MessageFingerprint private constructor(val value: String) {
    companion object : Exact<String, MessageFingerprint> { /* not blank, <= 64 chars */ }
}

/** Lookup key for counterparty→category memory: uppercased, punctuation-stripped counterparty. */
@JvmInline
value class CounterpartyKey private constructor(val value: String) {
    companion object : Exact<String, CounterpartyKey> { /* derived from NotBlankTrimmedString */ }
}

/**
 * Identifies which parser rule set handles a sender, e.g. "mpesa", "dtb", "kcb".
 * Declared HERE rather than in `:shared:sms:parser` because `FinancialSender` (§1.3) stores it
 * and `:shared:data:model` must not depend on the parser. The parser consumes this type.
 */
@JvmInline
value class RuleSetId private constructor(val value: String) {
    companion object : Exact<String, RuleSetId> {
        override val exactName = "RuleSetId"
        override fun Raise<String>.spec(raw: String): RuleSetId {
            val trimmed = NotBlankTrimmedString.from(raw).bind().value.lowercase()
            ensure(trimmed.matches(SLUG)) { "'$trimmed' is not a lowercase slug" }
            return RuleSetId(trimmed)
        }
        private val SLUG = Regex("[a-z0-9-]+")
    }
}
```

**Why `Exact`**: `SenderId` and `CounterpartyKey` are used as primary/lookup keys. If normalisation
lived at call sites, `"MPESA"` and `"M-PESA "` would become different senders. Making the type
impossible to construct un-normalised removes that entire class of bug.

### 1.2 The captured transaction ADT — `CapturedTransaction.kt`

```kotlin
package com.iris.data.model.sms

/** Direction of a principal amount, per FR-008. */
enum class MoneyDirection { MoneyOut, MoneyIn }

/** Where a captured item came from. Used only for diagnostics and import reporting. */
enum class CaptureOrigin { LiveBroadcast, HistoricalImport }

/**
 * Discriminates a principal amount from a transaction cost.
 * A tax component (FR-019) is only representable on a Fee — never on a Principal.
 */
sealed interface CapturedKind {
    data class Principal(val direction: MoneyDirection) : CapturedKind

    /**
     * @param parent the principal this fee belongs to; null for a standalone fee (FR-020).
     * @param tax the tax portion reported inside the cost; detail only, never its own transaction.
     */
    data class Fee(
        val parent: CapturedTransactionId?,
        val tax: PositiveDouble?,
    ) : CapturedKind
}

/**
 * A transaction extracted from one message, awaiting user review.
 * Lives ONLY in `captured_transactions`; it has no effect on balances, budgets,
 * reports or search until confirmed (FR-021, FR-021a).
 */
data class CapturedTransaction(
    override val id: CapturedTransactionId,
    val sender: SenderId,
    val kind: CapturedKind,
    val amount: PositiveDouble,
    val asset: AssetCode,
    val time: Instant,
    val counterparty: NotBlankTrimmedString?,
    val reference: ProviderReference?,
    /** null => the sender has no account mapping yet (FR-027a). No default fallback exists. */
    val account: AccountId?,
    val category: CategoryId?,
    val description: NotBlankTrimmedString?,
    /** Set when an existing ledger transaction looks like the same event (FR-029). */
    val duplicateOf: TransactionId?,
    val capturedAt: Instant,
    val origin: CaptureOrigin,
) : Identifiable<CapturedTransactionId>

/** Derived, never stored — storing it would let status and `account` disagree. */
sealed interface ReviewStatus {
    data object NeedsAccount : ReviewStatus
    data class PossibleDuplicate(val existing: TransactionId) : ReviewStatus
    data object ReadyToConfirm : ReviewStatus
}

fun CapturedTransaction.reviewStatus(): ReviewStatus = when {
    account == null -> ReviewStatus.NeedsAccount
    duplicateOf != null -> ReviewStatus.PossibleDuplicate(duplicateOf)
    else -> ReviewStatus.ReadyToConfirm
}

/** A principal together with the fee captured from the same message, for review and confirm. */
data class CapturedEntry(
    val principal: CapturedTransaction,
    val fee: CapturedTransaction?,
)
```

**Validation rules encoded by construction**

| Requirement | How the model enforces it |
|---|---|
| FR-012 / Edge "Zero-value amounts" | `amount: PositiveDouble` — `0.00` cannot be constructed |
| FR-019 tax is detail, not a transaction | `tax` is a field of `CapturedKind.Fee`, and nothing maps a `tax` to a `Transaction` |
| FR-020 standalone fee | `CapturedKind.Fee.parent` is nullable; no other code path is needed |
| FR-027a no default account | `account: AccountId?` + `ConfirmCaptureError.MissingAccount`; no `defaultAccount` symbol exists anywhere |
| FR-014 clean counterparty | `counterparty: NotBlankTrimmedString?` produced only by `CounterpartyNormalizer` |
| FR-015 reference retained | `reference: ProviderReference?`, copied into `Transaction.description` on confirm |

**State transitions** (a captured item is short-lived by design):

```
                 ┌─────────────── dismiss ───────────────┐
                 │                                        ▼
 SMS ──parse──► captured_transactions row ──confirm──► transactions row(s)
                 │        (NeedsAccount → pick account → ReadyToConfirm)
                 │        (PossibleDuplicate → keep or dismiss)
                 └── row deleted in both terminal cases; a processed_messages
                     row survives with outcome = Captured | Dismissed | Ignored
```

There is no `CONFIRMED` state stored on the captured row: confirming **deletes** the captured
row(s) and inserts `Transaction`s in the same Room transaction. That is what makes FR-033 true —
after confirm, nothing distinguishes the result from a manual entry.

### 1.3 Financial sender — `FinancialSender.kt`

```kotlin
package com.iris.data.model.sms

/**
 * A message sender the user has identified as a source of transaction confirmations.
 * Only messages whose sender matches a FinancialSender are ever inspected (FR-004).
 */
data class FinancialSender(
    override val id: SenderId,
    val displayName: NotBlankTrimmedString,
    /** Which parser rule set handles this sender; null => sender known but unparseable yet. */
    val ruleSet: RuleSetId?,
    /** null => captured items from this sender need an account chosen at review time (FR-027a). */
    val account: AccountId?,
    /** Per-sender kill switch (FR-003). */
    val enabled: Boolean,
) : Identifiable<SenderId>
```

`RuleSetId` is declared in `:shared:data:model` (§1.1) precisely so this type can reference it —
`:shared:data:model` must never depend on `:shared:sms:parser`. The parser *consumes* `RuleSetId`;
it does not own it. `FinancialSenderMapper` in `:shared:data:core` performs the
`RuleSetId.from(entity.ruleSetId)` validation, dropping an unknown value to `null` rather than
failing the whole row — a sender whose rule set was removed stays configured but stops matching.

### 1.4 Counterparty→category memory — `CounterpartyCategory.kt`

```kotlin
package com.iris.data.model.sms

/** The user's most recent category choice for a counterparty (FR-024). */
data class CounterpartyCategory(
    val counterparty: CounterpartyKey,
    val category: CategoryId,
    val updatedAt: Instant,
)
```

Written on every confirm where a category was chosen; upsert semantics mean Acceptance Scenario 3.3
("the most recent choice is the one suggested") is the natural behaviour.

### 1.5 Processed-message record — `ProcessedMessage.kt`

```kotlin
package com.iris.data.model.sms

enum class ProcessedOutcome {
    /** Produced one or two captured rows. */
    Captured,
    /** Matched an exclusion rule, or no rule matched — deliberately not a transaction. */
    Ignored,
    /** The user threw it away; must never reappear (FR-025). */
    Dismissed,
}

/** Minimal proof a message was already handled. Holds NO message content (FR-006). */
data class ProcessedMessage(
    val fingerprint: MessageFingerprint,
    val sender: SenderId,
    val outcome: ProcessedOutcome,
    val processedAt: Instant,
)
```

---

## 2. Parser model — `:shared:sms:parser`

Package `shared/sms/parser/src/main/java/com/iris/sms/parser/`. Pure JVM; no Android imports.

```kotlin
/** What the platform layer hands to the parser. */
data class RawSmsMessage(
    val sender: SenderId,
    val body: String,
    val receivedAt: Instant,
)

@JvmInline value class RuleSetId // declared in :shared:data:model (com.iris.data.model.sms), consumed here
@JvmInline value class RuleId    private constructor(val value: String) { companion object : Exact<String, RuleId> { /* "<ruleSetId>/<slug>" */ } }

/** The parser's success value — still pure data, no account, no category. */
data class ParsedMessage(
    val ruleSet: RuleSetId,
    val rule: RuleId,
    val principal: ParsedAmountLine?,   // null only for a standalone-fee message (FR-020)
    val fee: ParsedFee?,                // null when absent or reported as zero
    val time: Instant,
    val counterparty: NotBlankTrimmedString?,
    val reference: ProviderReference?,
)

data class ParsedAmountLine(
    val amount: PositiveDouble,
    val asset: AssetCode,               // always KES in this release
    val direction: MoneyDirection,
)

data class ParsedFee(
    val amount: PositiveDouble,
    val asset: AssetCode,
    val tax: PositiveDouble?,
)
```

`ParsedMessage` deliberately has **no** account, category or description: those are user/domain
concerns, and keeping them out means the parser corpus tests never need a repository.

Rule types (`SenderRuleSet`, `MessageRule`, `ExclusionRule`, `FeeRule`, `FieldBindings`) and the
`SmsParseError` ADT are specified in
[contracts/sms-parser-contract.md](./contracts/sms-parser-contract.md).

---

## 3. Persistence model — `:shared:data:core`

Room entities keep primitives (per `docs/guidelines/Data-Modeling.md`: "we make an exception for
DTOs and entities"). Full DDL, indices, DAO signatures and the migration body are in
[contracts/persistence-contract.md](./contracts/persistence-contract.md); the entity shapes are:

New files under `shared/data/core/src/main/java/com/iris/data/db/entity/`:

| Entity | Table | Primary key | Notes |
|---|---|---|---|
| `FinancialSenderEntity` | `financial_senders` | `senderId TEXT` | `accountId` nullable — FR-027a |
| `CapturedTransactionEntity` | `captured_transactions` | `id TEXT` (UUID) | `kind`, `direction`, `parentId`, `taxAmount`, `duplicateOfTransactionId` |
| `ProcessedMessageEntity` | `processed_messages` | `fingerprint TEXT` | `outcome TEXT`, no body, no hash preimage |
| `CounterpartyCategoryEntity` | `counterparty_categories` | `counterpartyKey TEXT` | upsert on confirm |

Unlike the existing entities, the four new ones are **not** `@Serializable`: they are excluded from
`BackupDataUseCase` by design (see research.md D4), so no serializer is needed and none is added.

DAOs follow the existing read/write split exactly:

- `shared/data/core/src/main/java/com/iris/data/db/dao/read/` — `CapturedTransactionDao`,
  `FinancialSenderDao`, `ProcessedMessageDao`, `CounterpartyCategoryDao`
- `.../dao/write/` — `WriteCapturedTransactionDao`, `WriteFinancialSenderDao`,
  `WriteProcessedMessageDao`, `WriteCounterpartyCategoryDao`

Repositories + mappers, mirroring `CategoryRepository` / `CategoryMapper`:

- `shared/data/core/src/main/java/com/iris/data/repository/CapturedTransactionRepository.kt`
- `.../repository/FinancialSenderRepository.kt`
- `.../repository/ProcessedMessageRepository.kt`
- `.../repository/CounterpartyCategoryRepository.kt`
- `.../repository/mapper/CapturedTransactionMapper.kt`, `FinancialSenderMapper.kt`

All repository functions are main-safe (`withContext(dispatchersProvider.io)`), and all mappers
return `Either<String, Domain>` exactly like `TransactionMapper.toDomain`.

---

## 4. Settings model — DataStore

New keys added to `shared/data/core/src/main/java/com/iris/data/datastore/DatastoreKeys.kt`:

| Key | Type | Purpose |
|---|---|---|
| `SMS_CAPTURE_ENABLED` | `booleanPreferencesKey("sms_capture_enabled")` | Master switch, default `false` (FR-001) |
| `SMS_TRANSACTION_COST_CATEGORY_ID` | `stringPreferencesKey("sms_transaction_cost_category_id")` | The one fee category (FR-017, D11) |
| `SMS_HISTORICAL_IMPORT_COMPLETED_AT` | `longPreferencesKey("sms_historical_import_completed_at")` | Makes the 30-day import one-time (FR-030) |

Per-sender enablement is **not** a DataStore key — it is `financial_senders.enabled`, because it is
per-row data with a natural home in the table.

---

## 5. View-state models

Both screens follow the four-file `:screen:*` pattern with `@Immutable` view-state built from
primitives and `ImmutableList`, so the Compose-stability CI job stays green. Full state/event
definitions are in [contracts/ui-and-platform-contract.md](./contracts/ui-and-platform-contract.md).
Key point: **no domain type crosses into view-state**. `CapturedTransaction` is mapped to
`CapturedItemUi` with pre-formatted `amountFormatted: String` (via the existing
`com.iris.ui.FormatMoneyUseCase`) and `timeFormatted: String` (via `com.iris.ui.time.TimeFormatter`),
so Paparazzi snapshots stay deterministic.

---

## 6. Entity → requirement traceability

| Spec Key Entity | Domain type | Table | Requirements |
|---|---|---|---|
| Financial sender | `FinancialSender` | `financial_senders` | FR-003, FR-004, FR-027, FR-027a |
| Captured transaction | `CapturedTransaction` + `CapturedKind.Principal` | `captured_transactions` | FR-007, FR-008, FR-013, FR-021, FR-021a, FR-022–FR-025, FR-029 |
| Captured fee | `CapturedTransaction` + `CapturedKind.Fee` | `captured_transactions` | FR-016, FR-018, FR-019, FR-020 |
| Transaction-cost category | `CategoryId` in DataStore | — (existing `categories`) | FR-017 |
| Counterparty→category memory | `CounterpartyCategory` | `counterparty_categories` | FR-024 |
| Processed-message record | `ProcessedMessage` | `processed_messages` | FR-025, FR-028, SC-003 |
