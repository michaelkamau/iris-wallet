# Contributing to IrisWallet

IrisWallet is licensed under the [GPL-3.0 License](LICENSE). By contributing, you agree that your contribution may be distributed under that license.

## 1. Fork the repository

Fork [michaelkamau/iris-wallet](https://github.com/michaelkamau/iris-wallet/fork).

## 2. Pick an issue

1. Browse [IrisWallet issues](https://github.com/michaelkamau/iris-wallet/issues).
2. Choose an issue that you understand and want to work on.
3. If no existing issue fits, [create one](https://github.com/michaelkamau/iris-wallet/issues/new/choose).

Please avoid duplicating work already assigned to another contributor.

## 3. Create a feature branch

Open your forked `iris-wallet` directory and create a branch:

```sh
git checkout -b fix-issue-{YOUR_ISSUE_NUMBER}
```

Replace `{YOUR_ISSUE_NUMBER}` with the issue number.

## 4. Set up, develop, and test

Follow the [README quick start](README.md#quick-start) to install JDK 17, Android Studio, Android SDK Platform 37, and to sync the project. Firebase, Google Services, and Crashlytics configuration is not required.

Read the [Developer Guidelines](docs/Guidelines.md) before you begin. Keep changes focused, test them locally, and make sure existing functionality continues to work.

Run build and quality checks from the repository root:

```sh
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew detekt
./gradlew :app:lintDebug
./gradlew verifyPaparazziDebug
```

The repository also provides helper scripts for these checks:

```sh
./scripts/unitTests.sh
./scripts/detekt.sh
./scripts/paparazziScreenshotTests.sh
```

For connected-device integration tests, start an emulator or connect a device, then run:

```sh
./scripts/integrationTests.sh
```

## 5. Submit a pull request

Open a [pull request](https://github.com/michaelkamau/iris-wallet/pulls) against the `main` branch of `michaelkamau/iris-wallet`.

For general GitHub guidance, see [Creating a pull request from a fork](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request-from-a-fork).
