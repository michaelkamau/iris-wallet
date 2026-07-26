# IrisWallet — Copilot instructions

Android money-management app. Kotlin + Jetpack Compose, multi-module Gradle (KTS), Hilt, Arrow, Room, Ktor.

## Environment

- JDK 17 (`jvm-target` in `gradle/libs.versions.toml`), Android SDK Platform 37 (`compile-sdk`), `min-sdk = 28`.
- No Firebase/Google Services/Crashlytics config is needed for local builds.
- Gradle configuration cache is on (`org.gradle.configuration-cache=true`).

## Commands

Run everything from the repository root.

```sh
./gradlew :app:assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:installDebug
./gradlew testDebugUnitTest           # all JVM unit tests
./gradlew detekt                      # static analysis (also: detektFormat for autocorrect)
./gradlew :app:lintDebug              # CI runs lintRelease
./gradlew verifyPaparazziDebug        # screenshot tests
./scripts/integrationTests.sh         # connected-device tests (:shared:data:core, :shared:domain)
```

Targeted runs (prefer these over full-suite while iterating):

```sh
./gradlew :screen:balance:testDebugUnitTest
./gradlew :shared:domain:testDebugUnitTest --tests "com.iris.domain.usecase.wallet.CalcWalletBalanceActTest"
./gradlew :shared:base:testDebugUnitTest --tests "*TimeConverter*"
./gradlew :screen:transactions:verifyPaparazziDebug
./gradlew :screen:transactions:recordPaparazziDebug   # re-record baselines after intentional UI change
./scripts/detektFormat.sh                             # detekt --auto-correct on changed .kt/.kts only
```

Baseline scripts exist (`scripts/detektBaseline.sh`, `scripts/lintBaseline.sh`, `scripts/composeStabilityBaseline.sh`) but are a last resort for legacy code — prefer fixing, or a narrow `@Suppress("RULE_ID")`.

CI gates on: Detekt, `lintRelease`, `testDebugUnitTest`, `verifyPaparazziDebug`, Compose stability (`./gradlew assembleDemo -PcomposeCompilerReports=true` then `:ci-actions:compose-stability:run`), and PR-description conformance to `.github/PULL_REQUEST_TEMPLATE.md`. See `docs/CI-Troubleshooting.md`.

## Module layout

`settings.gradle.kts` lists ~40 modules in five groups:

- `:app` — the only `com.android.application`; wires all screens together and owns `IrisNavGraph.kt`.
- `:screen:*` — one Gradle module per screen (`:screen:balance`, `:screen:transactions`, …).
- `:shared:*` — `base` (time, resources, dispatchers, legacy utils), `data:model`, `data:core` (Room, DataStore, Ktor, repositories), `domain` (use cases), `ui:core` (design system + `ComposeViewModel`), `ui:navigation`, and the `*-testing` fixture modules.
- `:widget:*` — Glance home-screen widgets.
- `:temp:legacy-code` and `:temp:old-design` — pre-modularization code being migrated out. Don't add to them; treat them as a shrinking dependency.
- `:ci-actions:*` — plain Kotlin JVM CLI tools used by GitHub Actions.

Dependencies are declared with typesafe project accessors: `implementation(projects.shared.domain)`, never `project(":shared:domain")`. All versions/bundles come from `gradle/libs.versions.toml` — never hardcode a version in a module build file.

A new module must: apply a convention plugin, be added to `settings.gradle.kts`, and (for screens) be added to `app/build.gradle.kts` dependencies.

## Convention plugins (`buildSrc/src/main/kotlin`)

Module build files are near-empty because behaviour lives in convention plugins:

- `iris.feature` — the default for screens and shared modules; composes `iris.module` + `iris.compose` + `iris.paparazzi`.
- `iris.module` → `iris.kotlin-android` + `iris.hilt` + `iris.kotlinx-serialization` (android library, jvm target, arrow/kotlin/timber deps, test bundle).
- `iris.compose` — enables Compose, adds Slack `compose-lints`, emits compiler reports.
- `iris.paparazzi`, `iris.room` (schemas in `<module>/schemas`), `iris.integration.testing` (androidTest), `iris.widget` (Glance), `iris.script` (JVM CLI), `iris.detekt`.

Put shared build logic in a convention plugin rather than repeating it across modules.

## Architecture

Data flow: **Raw model → Domain model → ViewState model**; layers: **Data → Domain (optional) → UI**. Full write-ups in `docs/Guidelines.md` and `docs/guidelines/`.

- Data layer (`:shared:data:core`): data sources wrap IO and never throw; mappers validate raw → domain; repositories are main-safe (`withContext(Dispatchers.IO)`).
- Domain layer (`:shared:domain`): use cases / "Act" classes combining repositories with business rules.
- UI layer: ViewModel adapts domain → view-state and events → domain calls; composables are dumb and only render state and emit events.

### Screen pattern (MVI over the Compose runtime)

Each `:screen:*` module holds four files in `src/main/java/com/iris/<name>/`: `XxxScreen.kt`, `XxxViewModel.kt`, `XxxState.kt`, `XxxEvent.kt`.

ViewModels extend `com.iris.ui.ComposeViewModel<State, Event>` and use the **Compose runtime (not Flow/StateFlow)** for state:

```kotlin
@Stable
@HiltViewModel
class BalanceViewModel @Inject constructor(...) : ComposeViewModel<BalanceState, BalanceEvent>() {
    private var currentBalance by mutableDoubleStateOf(0.0)

    @Composable
    override fun uiState(): BalanceState { /* remember/LaunchedEffect allowed here */ }

    override fun onEvent(event: BalanceEvent) = when (event) { /* exhaustive */ }
}
```

The screen composable calls `viewModel.uiState()` and passes `viewModel::onEvent` down to a private `UI(state, onEvent)` composable. View-state must be primitives / `@Immutable` / `ImmutableList` so composables stay skippable — the Compose-stability CI job fails otherwise.

Navigation is a hand-rolled sealed `Screen` hierarchy: add a `data object`/`data class` to `shared/ui/navigation/.../Screens.kt`, then map it in `app/src/main/java/com/iris/IrisNavGraph.kt`. `Screen.isLegacy` marks old screens (auto-wrapped in a `Surface`); new Material3 screens must leave it `false`.

### Error handling and data modeling

- **Do not throw.** Fallible functions return Arrow `Either<Error, Data>`; compose them inside `either { }` with `.bind()`, `raise()`, `ensure()`. Throwing is reserved for cases where crashing is the correct outcome (e.g. out of disk space).
- Model domain state with ADTs — `sealed interface` + `data class` — so impossible states are unrepresentable (`sealed interface UiState { Loading; Content; Error }`, not nullable-flag bags).
- Use explicit `@JvmInline value class` types with Arrow `Exact` companions (`PositiveInt.from(x): Either<String, PositiveInt>`) for domain values; DTOs and Room entities may stay primitive.

## Testing

- JUnit4 + Kotest matchers (`shouldBe`) + MockK, Given/When/Then bodies, backtick test names that read as sentences. Keep a test under half a screen; extract fixtures into a `companion object` or a shared `SomethingFixtures` object.
- ViewModel tests extend `ComposeViewModelTest` and use the Molecule-backed helper: `viewModel.runTest(events = listOf(...)) { counter shouldBe 43 }`.
- Screenshot tests extend `PaparazziScreenshotTest`, run with `@RunWith(TestParameterInjector::class)` and a `@TestParameter theme: PaparazziTheme` so each snapshot is captured in Light and Dark; images live in `src/test/snapshots/images/`. **Use static data only** — anything time/date-dependent makes snapshots flaky.
- Prefer the provided fakes (`FakeDao`, `TestTimeConverter`, `TestResourceProvider`, `:shared:*-testing` modules) over mocking everything.

## Style

Detekt runs with `allRules = true` and `buildUponDefaultConfig = true` (config in `config/detekt/config.yml`, ktlint + compose rulesets included) — match existing formatting rather than reformatting files. The overarching project value, per `docs/Guidelines.md`, is minimal complexity: prefer the simplest change that works and keep PRs scoped to one issue.
