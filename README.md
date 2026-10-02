[![Latest Release](https://img.shields.io/github/v/release/michaelkamau/iris-wallet)](https://github.com/michaelkamau/iris-wallet/releases)
[![APK](https://github.com/michaelkamau/iris-wallet/actions/workflows/apk.yml/badge.svg)](https://github.com/michaelkamau/iris-wallet/actions/workflows/apk.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# IrisWallet

IrisWallet is a free and open-source Android money-management app written in Kotlin and Jetpack
Compose. It helps you keep track of personal finances.

## Quick start

### Prerequisites

- Git
- JDK 17
- The latest stable Android Studio, with Android SDK Platform 37 installed
- An Android emulator or a physical device running Android 9 (API 28) or later

### Set up the project

1. Clone the repository and enter it:

   ```sh
   git clone https://github.com/michaelkamau/iris-wallet.git
   cd iris-wallet
   ```

2. In Android Studio, use **SDK Manager** to install Android SDK Platform 37. Android Studio
   normally creates `local.properties` with the SDK location. If Gradle cannot locate the SDK,
   create or update `local.properties` in the repository root:

   ```properties
   sdk.dir=/absolute/path/to/Android/Sdk
   ```

3. Open the repository root in Android Studio, set the Gradle JDK to JDK 17, and select **Sync
   Project with Gradle Files**.

   Firebase, Google Services, and Crashlytics configuration files or credentials are not required
   for local builds.

### Build and run

Build the debug APK from the repository root:

```sh
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To run IrisWallet, start an emulator or connect an Android 9 (API 28) or newer device with USB
debugging enabled. Select the `app` run configuration in Android Studio and click **Run**, or
install the debug build with:

```sh
./gradlew :app:installDebug
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for development checks and the contribution workflow.

### Development checks

Run these commands from the repository root:

```sh
./gradlew testDebugUnitTest
./gradlew detekt
./gradlew :app:lintDebug
./gradlew verifyPaparazziDebug
```

For connected-device integration tests, start an emulator or connect a device, then run:

```sh
./scripts/integrationTests.sh
```

## Learning materials

Development resources are available in [docs/resources](docs/resources/). Read
the [Developer Guidelines](docs/Guidelines.md) for technical guidance.

## Tech stack

### Core

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3](https://m3.material.io/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Kotlin Flow](https://kotlinlang.org/docs/flow.html)
- [Hilt](https://dagger.dev/hilt/)
- [ArrowKt](https://arrow-kt.io/)

### Testing

- [JUnit4](https://github.com/junit-team/junit4)
- [Kotest](https://kotest.io/)
- [Paparazzi](https://github.com/cashapp/paparazzi)

### Local persistence

- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Room DB](https://developer.android.com/training/data-storage/room)

### Networking

- [Ktor client](https://ktor.io/docs/getting-started-ktor-client)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)

### Build and CI

- [Gradle KTS](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Gradle convention plugins](https://docs.gradle.org/current/samples/sample_convention_plugins.html)
- [Gradle version catalogs](https://developer.android.com/build/migrate-to-catalogs)
- [GitHub Actions](https://github.com/michaelkamau/iris-wallet/actions)

### Other

- [Timber](https://github.com/JakeWharton/timber)
- [Detekt](https://github.com/detekt/detekt)
- [Ktlint](https://github.com/pinterest/ktlint)
- [Slack's compose-lints](https://slackhq.github.io/compose-lints/)

## Contribute

See [CONTRIBUTING.md](./CONTRIBUTING.md).

## Contributors

<a href="https://github.com/michaelkamau/iris-wallet/graphs/contributors">
  <img alt="contributors graph" src="https://contrib.rocks/image?repo=michaelkamau/iris-wallet" />
</a>

_The [contrib.rocks](https://contrib.rocks/preview?repo=michaelkamau%2Firis-wallet) graph may take
up to 24 hours to update._

### Creative contributors

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/62771583?v=4" width="100px;" alt="Stefan Ilijev - Designer"/><br>
    <strong>Stefan Ilijev</strong><br>
    <small>Co-founder and designer of the original project. Created its <a href="https://www.figma.com/file/kSwIa07jcHEHZXo6rzx7dn/Design-System?node-id=0%3A1&mode=dev">design system</a>.</small>
    <br/>
    <br/>
</div>

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/86833171?v=4" width="100px;" alt="Aditya [ADX]"/><br>
    <strong><a href="https://github.com/adx69">Aditya</a></strong><br>
    <br/>
</div>

<div style="text-align: center">
    <img src="https://avatars.githubusercontent.com/u/130169485?v=4" width="100px;" alt="Shymom [SSI]"/><br>
    <strong><a href="https://github.com/SHYMOM">Shymom</a></strong><br>
    <br/>
</div>

IrisWallet is distributed under the [GPL-3.0 License](LICENSE).
