# Contract: Persistence (Room schema, DAOs, repositories, migration)

**Module**: `:shared:data:core` | **Database**: `com.iris.data.db.IrisRoomDatabase`
**Version**: `130 → 131` | **Schemas exported to**: `shared/data/core/schemas/com.iris.data.db.IrisRoomDatabase/131.json`

All four tables are additive. No existing table, entity, DAO or query is modified — which is the
mechanism by which FR-021a (pending items invisible to balances/budgets/reports/search) and SC-010
(nothing existing changes) are guaranteed.

---

## 1. DDL — `Migration130to131_SmsCapture`

`shared/data/core/src/main/java/com/iris/data/db/migration/Migration130to131_SmsCapture.kt`,
package `com.iris.data.db.migration`, following the exact shape of
`Migration129to130_LoanIncludeNote`.

```sql
CREATE TABLE IF NOT EXISTS `financial_senders` (
    `senderId`    TEXT    NOT NULL,
    `displayName` TEXT    NOT NULL,
    `ruleSetId`   TEXT,
    `accountId`   TEXT,              -- NULL => needs an account at review time (FR-027a)
    `enabled`     INTEGER NOT NULL,
    PRIMARY KEY(`senderId`)
);

CREATE TABLE IF NOT EXISTS `captured_transactions` (
    `id`                       TEXT    NOT NULL,
    `senderId`                 TEXT    NOT NULL,
    `kind`                     TEXT    NOT NULL,  -- 'PRINCIPAL' | 'FEE'
    `direction`                TEXT,              -- 'MONEY_OUT' | 'MONEY_IN'; NULL when kind='FEE'
    `parentId`                 TEXT,              -- fee -> principal; NULL for standalone fee (FR-020)
    `taxAmount`                REAL,              -- fee only (FR-019)
    `amount`                   REAL    NOT NULL,  -- always > 0 by construction
    `assetCode`                TEXT    NOT NULL,
    `dateTime`                 INTEGER NOT NULL,  -- Instant epoch millis, RoomTypeConverters
    `counterparty`             TEXT,
    `reference`                TEXT,
    `accountId`                TEXT,              -- NULL => ReviewStatus.NeedsAccount
    `categoryId`               TEXT,
    `description`              TEXT,
    `duplicateOfTransactionId` TEXT,              -- FR-029
    `capturedAt`               INTEGER NOT NULL,
    `origin`                   TEXT    NOT NULL,  -- 'LIVE_BROADCAST' | 'HISTORICAL_IMPORT'
    PRIMARY KEY(`id`)
);
CREATE INDEX IF NOT EXISTS `index_captured_transactions_parentId` ON `captured_transactions` (`parentId`);
CREATE INDEX IF NOT EXISTS `index_captured_transactions_dateTime` ON `captured_transactions` (`dateTime`);

CREATE TABLE IF NOT EXISTS `processed_messages` (
    `fingerprint`  TEXT    NOT NULL,  -- "<ruleSetId>:<REF>" or "h:<sha256[0..31]>"; never the body
    `senderId`     TEXT    NOT NULL,
    `outcome`      TEXT    NOT NULL,  -- 'CAPTURED' | 'IGNORED' | 'DISMISSED'
    `processedAt`  INTEGER NOT NULL,
    PRIMARY KEY(`fingerprint`)
);
CREATE INDEX IF NOT EXISTS `index_processed_messages_processedAt` ON `processed_messages` (`processedAt`);

CREATE TABLE IF NOT EXISTS `counterparty_categories` (
    `counterpartyKey` TEXT    NOT NULL,
    `categoryId`      TEXT    NOT NULL,
    `updatedAt`       INTEGER NOT NULL,
    PRIMARY KEY(`counterpartyKey`)
);
```

**No foreign keys.** Consistent with every existing table in this database (`transactions` stores a
bare `accountId TEXT`), and deliberate: a captured item must survive the user deleting the account
or category it referenced — it degrades to `NeedsAccount` rather than cascading away.

**Registration** in `IrisRoomDatabase.kt`:

- add the four entity classes to `@Database(entities = [...])`
- bump `version = 130` → `version = 131`
- add the eight `abstract val` DAO accessors
- append `Migration130to131_SmsCapture()` to `migrations()`
- provide the eight DAOs in `shared/data/core/src/main/java/com/iris/data/di/RoomDbModule.kt`

---

## 2. DAO contracts

Read DAOs — `shared/data/core/src/main/java/com/iris/data/db/dao/read/`:

```kotlin
@Dao
interface CapturedTransactionDao {
    @Query("SELECT * FROM captured_transactions ORDER BY dateTime DESC")
    suspend fun findAll(): List<CapturedTransactionEntity>

    @Query("SELECT * FROM captured_transactions WHERE id = :id")
    suspend fun findById(id: UUID): CapturedTransactionEntity?

    @Query("SELECT * FROM captured_transactions WHERE parentId = :parentId")
    suspend fun findByParentId(parentId: UUID): List<CapturedTransactionEntity>

    @Query("SELECT COUNT(*) FROM captured_transactions WHERE kind = 'PRINCIPAL'")
    fun pendingCount(): Flow<Int>   // drives the review badge / settings row (FR-021a)
}

@Dao
interface FinancialSenderDao {
    @Query("SELECT * FROM financial_senders ORDER BY displayName")
    suspend fun findAll(): List<FinancialSenderEntity>

    @Query("SELECT * FROM financial_senders WHERE senderId = :senderId AND enabled = 1")
    suspend fun findEnabledById(senderId: String): FinancialSenderEntity?
}

@Dao
interface ProcessedMessageDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_messages WHERE fingerprint = :fingerprint)")
    suspend fun exists(fingerprint: String): Boolean
}

@Dao
interface CounterpartyCategoryDao {
    @Query("SELECT * FROM counterparty_categories WHERE counterpartyKey = :key")
    suspend fun findByKey(key: String): CounterpartyCategoryEntity?
}
```

Write DAOs — `.../dao/write/` — mirror `WriteTransactionDao`:
`@Insert(onConflict = OnConflictStrategy.REPLACE) save/saveMany`, `deleteById`,
`deleteByParentId`, `deleteAll`.

---

## 3. Repository contracts

`shared/data/core/src/main/java/com/iris/data/repository/`. All functions main-safe via
`withContext(dispatchersProvider.io)`; all entity→domain mapping returns
`Either<String, Domain>` and is dropped with `.getOrNull()` at the repository boundary exactly as
`CategoryRepository` does today.

```kotlin
@Singleton
class CapturedTransactionRepository @Inject constructor(
    private val mapper: CapturedTransactionMapper,
    private val dao: CapturedTransactionDao,
    private val writeDao: WriteCapturedTransactionDao,
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun findAllPending(): List<CapturedEntry>          // principals + their fees, newest first
    suspend fun findById(id: CapturedTransactionId): CapturedEntry?
    fun pendingCount(): Flow<Int>
    suspend fun save(value: CapturedTransaction)
    suspend fun saveEntry(entry: CapturedEntry)                // principal + fee in one Room txn
    suspend fun deleteEntry(id: CapturedTransactionId)         // deletes the fee too (FR-018)
    suspend fun deleteAll()
}

@Singleton
class FinancialSenderRepository @Inject constructor(...) {
    suspend fun findAll(): List<FinancialSender>
    suspend fun findEnabled(sender: SenderId): FinancialSender?
    suspend fun save(value: FinancialSender)
    suspend fun deleteById(id: SenderId)
}

@Singleton
class ProcessedMessageRepository @Inject constructor(...) {
    suspend fun isProcessed(fingerprint: MessageFingerprint): Boolean
    suspend fun record(value: ProcessedMessage)
}

@Singleton
class CounterpartyCategoryRepository @Inject constructor(...) {
    suspend fun findCategory(key: CounterpartyKey): CategoryId?
    suspend fun remember(value: CounterpartyCategory)   // upsert
}
```

---

## 4. Atomicity contract for confirm

`ConfirmCapturedTransactionUseCase` must perform, **inside a single `IrisRoomDatabase`
`withTransaction { }` block**:

1. insert the principal `Expense`/`Income` into `transactions`;
2. insert the fee `Expense` into `transactions` when a fee exists, with `categoryId` = the
   transaction-cost category (FR-016, FR-017);
3. upsert `counterparty_categories` when a category was chosen (FR-024);
4. update `processed_messages.outcome` to `CAPTURED`;
5. delete the captured principal row **and** its fee row.

If any step fails, the whole block rolls back and the use case returns
`Left(ConfirmCaptureError.Persistence)` — the user still sees the pending item and nothing partial
reached the ledger. This is the guarantee behind SC-003.

Dismiss is the mirror image: set `outcome = DISMISSED`, delete the captured rows, insert nothing.
The surviving `processed_messages` row is what makes FR-025 ("dismissed items MUST NOT reappear")
hold across re-delivery and re-import.

---

## 5. Mapping to the existing ledger types (FR-033)

`CapturedTransaction` → `com.iris.data.model.Transaction` on confirm:

| Captured field | Ledger field |
|---|---|
| `amount`, `asset` | `PositiveValue(amount, asset)` |
| `kind` = `Principal(MoneyOut)` | `Expense` |
| `kind` = `Principal(MoneyIn)` | `Income` |
| `kind` = `Fee` | `Expense` with `category` = transaction-cost category |
| `time` | `time: Instant` (unchanged) |
| `counterparty` | `title: NotBlankTrimmedString?` |
| `description` + `reference` | `description` — the user's text with the reference appended as `Ref: UGP7B0ITE4` (FR-015) |
| `account` (non-null, enforced) | `account: AccountId` |
| `category` | `category: CategoryId?` |
| — | `id = TransactionId(UUID.randomUUID())`, `settled = true`, `tags = emptyList()`, `metadata = TransactionMetadata(null, null, null, null)` |

**No `source` column, no marker tag, no metadata flag is written.** After confirm the row is
indistinguishable from a manual entry in reports, budgets, balances, search and CSV export —
which is FR-033 stated as a schema property rather than a behaviour to test everywhere.

---

## 6. Backup / export contract

The four new tables are **excluded** from `shared/data/core/src/main/java/com/iris/data/backup/`
(`BackupDataUseCase`) and from the CSV export in `:shared:domain`
(`com.iris.domain.usecase.csv`). Un-reviewed captured data is transient and privacy-sensitive; only
confirmed transactions — which are ordinary rows — appear in a backup. Consequently the new
entities are not `@Serializable`, unlike every existing entity.

---

## 7. DataStore contract

Added to `shared/data/core/src/main/java/com/iris/data/datastore/DatastoreKeys.kt`:

```kotlin
val SMS_CAPTURE_ENABLED = booleanPreferencesKey("sms_capture_enabled")                     // default false (FR-001)
val SMS_TRANSACTION_COST_CATEGORY_ID = stringPreferencesKey("sms_transaction_cost_category_id")
val SMS_HISTORICAL_IMPORT_COMPLETED_AT = longPreferencesKey("sms_historical_import_completed_at")
```

Reading an absent `SMS_CAPTURE_ENABLED` must yield `false`, never `true` — asserted by a test, since
this single default is what keeps SC-010 true for every existing user on upgrade.

---

## 8. Migration test contract

Added to `shared/data/core/src/androidTest/java/com/iris/data/db/IrisRoomDatabaseMigrationTest.kt`,
following the existing `migrate129to130_LoanIncludeNote` pattern:

```kotlin
@Test
fun migrate130to131_SmsCapture() {
    helper.createDatabase(TestDb, 130).apply {
        // insert one `transactions` row so we can assert existing data survives
        close()
    }
    val db = helper.runMigrationsAndValidate(TestDb, 131, true, Migration130to131_SmsCapture())
    // then: the pre-existing transaction row is intact, and each new table accepts an insert
}
```

Run with `./scripts/integrationTests.sh` (connected device / emulator). Room's
`runMigrationsAndValidate` also verifies the migration DDL matches the exported `131.json`, so a
drift between the hand-written SQL and the entity definitions fails the build.
