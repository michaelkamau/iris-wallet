# Contract: SMS Parsing Engine

**Module**: `:shared:sms:parser` — `shared/sms/parser/src/main/java/com/iris/sms/parser/`
**Namespace**: `com.iris.sms.parser` | **Plugin**: `iris.feature`
**Depends on**: `projects.shared.data.model`, `projects.shared.base` — **no Android framework APIs**

This is the contract every provider rule set and every caller programs against. The engine
(`SmsParser` + the primitive parsers) is closed for modification; providers are added as data.

---

## 1. Public entry point

```kotlin
package com.iris.sms.parser

interface SmsParser {
    /**
     * Total function. Never throws. Never guesses (FR-013).
     * A message that is not a transaction is an ordinary `Either.Left`, not an error condition.
     */
    fun parse(message: RawSmsMessage): Either<SmsParseError, ParsedMessage>
}

@Singleton
class RuleDrivenSmsParser @Inject constructor(
    private val catalog: RuleCatalogSource,
    private val amountParser: AmountParser,
    private val dateTimeParser: SmsDateTimeParser,
    private val counterpartyNormalizer: CounterpartyNormalizer,
    private val promoStripper: PromoTailStripper,
) : SmsParser
```

### Guaranteed evaluation order

`parse` is specified as this exact pipeline. Rule sets cannot change it.

1. **Sender resolution** — find the single `SenderRuleSet` whose `senderPatterns` match
   `message.sender`. None → `Left(NoRuleForSender)`. (FR-004: unconfigured senders never reach here
   at all; this guards a configured sender with no rule set.)
2. **Promo stripping** — `PromoTailStripper.strip(body, ruleSet.promoMarkers)` truncates at the
   first promo marker. Applies to matching *and* to the description (FR-011).
3. **Exclusion** — first matching `ExclusionRule` short-circuits to
   `Left(ExcludedByRule(ruleId, reason))`. Runs before any field extraction (FR-012).
4. **Message rule match** — `messageRules` are tried in declaration order; **first match wins**.
   None → `Left(NoMatchingPattern(ruleSetId))`.
5. **Principal extraction** — amount, date/time, counterparty, reference, per the matched rule's
   `FieldBindings`. Any required binding that fails → `Left(...)`; nothing partial is returned.
6. **Fee extraction** — `feeRules` run over the *same* stripped body independently of which message
   rule matched. A fee that parses as `0.00` yields no fee (`PositiveDouble` rejects it, D9), not an
   error.
7. **Assembly** — returns `ParsedMessage`. If step 5 produced nothing but step 6 produced a fee, the
   result carries `principal = null` and a `CapturedKind.Fee(parent = null, …)` downstream (FR-020).

---

## 2. Error ADT

```kotlin
sealed interface SmsParseError {
    data class NoRuleForSender(val sender: SenderId) : SmsParseError
    data class NoMatchingPattern(val ruleSet: RuleSetId) : SmsParseError
    data class ExcludedByRule(val rule: RuleId, val reason: ExclusionReason) : SmsParseError
    data class MissingField(val rule: RuleId, val field: RequiredField) : SmsParseError
    data class InvalidAmount(val rule: RuleId, val raw: String, val cause: String) : SmsParseError
    data class InvalidDateTime(val rule: RuleId, val raw: String, val cause: String) : SmsParseError
    data class InvalidValue(val rule: RuleId, val field: RequiredField, val cause: String) : SmsParseError
}

enum class ExclusionReason { Promotional, BalanceEnquiry, OneTimePassword, Failed, Cancelled, Reversed, ZeroValue, Statement }
enum class RequiredField { Amount, DateTime, Direction, Counterparty, Reference }
```

**Contract for callers**: `ExcludedByRule` and `NoMatchingPattern` are *expected* outcomes and must
be logged at `Timber.d` with the rule id only. `InvalidAmount` / `InvalidDateTime` /
`InvalidValue` indicate a rule that matched but then failed — log at `Timber.w` with the rule id and
the *offending token only*, never the body (FR-006).

---

## 3. Rule model — how a provider is added

```kotlin
data class SenderRuleSet(
    val id: RuleSetId,
    val displayName: NotBlankTrimmedString,
    /** Any match makes this rule set responsible for the sender. */
    val senderPatterns: ImmutableList<Regex>,
    /** Truncation points for promotional/instructional tails (FR-011). */
    val promoMarkers: ImmutableList<Regex>,
    val exclusions: ImmutableList<ExclusionRule>,
    val messageRules: ImmutableList<MessageRule>,
    val feeRules: ImmutableList<FeeRule>,
)

data class ExclusionRule(val id: RuleId, val pattern: Regex, val reason: ExclusionReason)

data class MessageRule(
    val id: RuleId,
    val direction: MoneyDirection,
    /** Must use named groups matching the names declared in [fields]. */
    val pattern: Regex,
    val fields: FieldBindings,
)

data class FieldBindings(
    val amount: GroupName,
    val dateTime: GroupName,
    val timeOfDay: GroupName? = null,
    val counterparty: GroupName? = null,
    val reference: GroupName? = null,
    /** Formats tried in order against the concatenated date + time text. */
    val dateTimeFormats: ImmutableList<SmsDateTimeFormat>,
)

data class FeeRule(
    val id: RuleId,
    val pattern: Regex,
    val amount: GroupName,
    val tax: GroupName? = null,
)

@JvmInline value class GroupName private constructor(val value: String) {
    companion object : Exact<String, GroupName> { /* matches [a-zA-Z][a-zA-Z0-9]* */ }
}

/** `RuleId` is parser-internal: "<ruleSetId>/<slug>", e.g. "mpesa/paid-to". */
@JvmInline value class RuleId private constructor(val value: String) {
    companion object : Exact<String, RuleId> { /* "<ruleSetId>/<slug>" */ }
}
```

> `RuleSetId` is **not** declared here — it lives in `:shared:data:model`
> (`com.iris.data.model.sms.SmsPrimitives`) because `FinancialSender` stores it, and
> `:shared:data:model` must not depend on this module. The parser imports it.

### Registration — the only wiring a new provider needs

```kotlin
// shared/sms/parser/src/main/java/com/iris/sms/parser/rules/SmsRuleCatalogModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SmsRuleCatalogModule {
    @Provides @IntoSet fun mpesa(): SenderRuleSet = MpesaRules.ruleSet
    @Provides @IntoSet fun dtb(): SenderRuleSet = DtbRules.ruleSet
    @Provides @IntoSet fun kcb(): SenderRuleSet = KcbRules.ruleSet
    // ← one line per new provider; the engine is untouched
}

interface RuleCatalogSource { fun ruleSets(): ImmutableList<SenderRuleSet> }

@Singleton
class CompiledRuleCatalogSource @Inject constructor(
    private val ruleSets: Set<@JvmSuppressWildcards SenderRuleSet>,
) : RuleCatalogSource
```

`RuleCatalogSource` is the seam for a future asset-backed, updatable catalog (research.md D2). It is
introduced now so the swap is additive; only `CompiledRuleCatalogSource` ships in this feature.

### Files a new provider touches — and nothing else

```
shared/sms/parser/src/main/java/com/iris/sms/parser/rules/<Provider>Rules.kt   (new)
shared/sms/parser/src/main/java/com/iris/sms/parser/rules/SmsRuleCatalogModule.kt  (+1 line)
shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/<Provider>Corpus.kt (new)
```

---

## 4. Primitive parsers (engine-level, shared by all rule sets)

```kotlin
/** "Ksh1,350.00", "KES 540.25", "6000.00 KES" -> PositiveDouble. "Ksh0.00" -> Left. */
class AmountParser @Inject constructor() {
    fun parse(raw: String): Either<String, PositiveDouble>
}

/** Fixed Africa/Nairobi. Two-digit years pivot on 2000. See research.md D8. */
class SmsDateTimeParser @Inject constructor(private val timeProvider: TimeProvider) {
    fun parse(
        rawDate: String,
        rawTime: String?,
        formats: ImmutableList<SmsDateTimeFormat>,
        receivedAt: Instant,
    ): Either<String, Instant>

    companion object { val ZONE: ZoneId = ZoneId.of("Africa/Nairobi") }
}

enum class SmsDateTimeFormat(internal val pattern: String) {
    SlashDayMonthShortYear("d/M/yy"),        // 25/7/26
    SlashDayMonthFullYear("dd/MM/yyyy"),     // 25/07/2026
    SpacedDayMonShortName("d MMM yyyy"),     // 25 Jul 2026
    IsoDateTimeMeridiem("yyyy-MM-dd hh:mm:ss a"), // 2026-07-24 07:19:54 PM
    ClockMeridiem("h:mm a"),                 // 7:50 PM
    Clock24("HH:mm"),                        // 17:41  (a trailing "EAT" is stripped first)
}

/** "james Kinyua Mwangi9." -> "James Kinyua Mwangi"  (FR-014) */
class CounterpartyNormalizer @Inject constructor() {
    fun normalize(raw: String): Either<String, NotBlankTrimmedString>
    fun key(name: NotBlankTrimmedString): CounterpartyKey
}

/** Truncates at "Download My OneApp on https://…", "Dial *522#", etc. (FR-011) */
class PromoTailStripper @Inject constructor() {
    fun strip(body: String, markers: ImmutableList<Regex>): String
}
```

### Normalisation contract for `CounterpartyNormalizer`

1. Strip a trailing run of digits and punctuation (`Mwangi9.` → `Mwangi`).
2. Strip masked-identifier fragments (`254****956`, `5XXXXX5001`).
3. Collapse internal whitespace.
4. Title-case each word, preserving all-caps acronyms of ≤ 4 characters and words containing a
   digit (`GITHUB, INC.` → `Github, Inc.`; `KCB` stays `KCB`).
5. `NotBlankTrimmedString.from(...)` — an empty result is `Left`, never a blank counterparty.

`key()` additionally uppercases and removes all non-alphanumerics, so
`"Frank Inn Kikuyu"` and `"FRANK INN KIKUYU"` share one memory row (FR-024).

---

## 5. Dedupe fingerprint

```kotlin
object MessageFingerprints {
    /** "<ruleSetId>:<REFERENCE>" when a reference exists, else "h:<32 hex of sha256>". */
    fun of(message: RawSmsMessage, parsed: ParsedMessage?): MessageFingerprint
}
```

**Invariants asserted by tests**:

- Same message parsed twice → same fingerprint (SC-003).
- Live capture and historical import of the same message → same fingerprint (Edge "Duplicate
  delivery"). The hash preimage uses the **normalised sender + stripped body only** — never
  `receivedAt` — because the inbox timestamp and the broadcast timestamp differ.
- The message body is never stored: only the digest is persisted.

---

## 6. Test corpus contract (this is SC-001 and SC-004 as an executable gate)

`shared/sms/parser/src/test/java/com/iris/sms/parser/corpus/SmsCorpus.kt` defines:

```kotlin
data class CorpusCase(
    val name: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,          // fixed literal; never Instant.now()
    val expected: Expectation,
)

sealed interface Expectation {
    data class Parsed(
        val amount: Double?, val direction: MoneyDirection?,
        val counterparty: String?, val reference: String?,
        val timeIso: String,          // e.g. "2026-07-25T16:50:00Z"  (19:50 EAT)
        val fee: Double?, val tax: Double?,
    ) : Expectation
    data class Rejected(val reason: ExclusionReason?) : Expectation
}
```

`SmsParserCorpusTest` iterates every case and asserts the whole `Either` with Kotest `shouldBe`.

**Mandatory positive cases** — the real messages from spec.md:

| # | Body (abbreviated) | Expected |
|---|---|---|
| 1 | `UGP7B0ITE4 Confirmed Ksh1,350.00 paid to james Kinyua Mwangi9. on 25/7/26 at 7:50 PM. New M-PESA balance is Ksh1,242.02. Transaction cost, Ksh0.00.` | amount 1350.00, MoneyOut, "James Kinyua Mwangi", ref `UGP7B0ITE4`, `2026-07-25T16:50:00Z`, **fee null** |
| 2 | `ALERT: Your account no. 5XXXXX5001 has been debited with KES 540.25 for a POS PURCHASE at GITHUB, INC. SAN FRANCISCO CA on 25/07/2026.` | amount 540.25, MoneyOut, "Github, Inc.", ref null, date 2026-07-25, fee null |
| 3 | `DTB 6000.00 KES has been successfully sent to Michael Kamau Njuguna … Ref. AD3EA389C13A7 on 25 Jul 2026 at 17:41 EAT. Charges 59.76 KES` | amount 6000.00, MoneyOut, "Michael Kamau Njuguna", ref `AD3EA389C13A7`, `2026-07-25T14:41:00Z`, **fee 59.76** |
| 4 | `… Your SEND TO M-PESA request of KES 13,000.00 … Transaction cost KES 66.00 Incl. Tax Amount KES 8.25. M-PESA REF: UGO680QNMS.` | amount 13000.00, MoneyOut, ref `UGO680QNMS`, **fee 66.00, tax 8.25** |
| 5 | An incoming-funds M-PESA confirmation | `MoneyIn` (Edge "Money received") |
| 6 | A confirmation with a `2026-07-24 07:19:54 PM` timestamp | correct `Instant` |
| 7 | A message whose real body carries `Download My OneApp on https://…` / `Dial *522#` tails | promo text absent from counterparty and description |

**Mandatory negative cases** (each must be `Rejected`, per FR-012, SC-004, Edge cases):
zero-value principal; `Transaction cost, Ksh0.00` → no fee row; failed transaction; cancelled;
reversal notice; balance enquiry; loan-limit advert; statement notice; OTP code; a non-financial
sender; a financial-sender message in an unseen wording (must be `NoMatchingPattern`, **not** a
half-filled parse).

**Rule for contributors**: every new or edited rule must arrive with at least one positive corpus
row and, where the wording is close to a promo/advert, one negative row.
