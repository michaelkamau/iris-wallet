plugins {
    id("com.android.application")
    org.jetbrains.kotlin.plugin.compose
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvm.target.get()))
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    // Detekt's `jvmTarget` defaults to `JavaVersion.current()`, which fails when the running
    // JDK (e.g. 25) is newer than what detekt's bundled compiler frontend understands. Pin it
    // explicitly to the project's target JVM version, same as the `iris.detekt` convention
    // plugin used by other modules.
    jvmTarget = libs.versions.jvm.target.get()
}

android {
    namespace = "com.iris.wallet"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.iris.wallet"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        versionName = libs.versions.version.name.get()
        versionCode = libs.versions.version.code.get().toInt()
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "IRIS7834!DEbug"
            keyAlias = "debug"
            keyPassword = "IRIS7834!DEbug"
        }

        create("release") {
            storeFile = file("../sign.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("release")

            resValue("string", "app_name", "Iris Wallet")
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            isDebuggable = true
            isDefault = true

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Iris Wallet Debug")
        }

        create("demo") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks.add("release")

            isDebuggable = false

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Iris Wallet")
        }
    }

    val javaVersion = libs.versions.jvm.target.get()

    compileOptions {
        sourceCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
        targetCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        disable += "ComposeViewModelInjection"
        checkDependencies = true
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        htmlOutput = file("${project.rootDir}/build/reports/lint/lint.html")
        xmlReport = true
        xmlOutput = file("${project.rootDir}/build/reports/lint/lint.xml")
        baseline = file("lint-baseline.xml")
    }
}

dependencies {

    implementation(projects.screen.attributions)
    implementation(projects.screen.balance)
    implementation(projects.screen.budgets)
    implementation(projects.screen.categories)
    implementation(projects.screen.contributors)
    implementation(projects.screen.disclaimer)
    implementation(projects.screen.editTransaction)
    implementation(projects.screen.exchangeRates)
    implementation(projects.screen.features)
    implementation(projects.screen.home)
    implementation(projects.screen.importData)
    implementation(projects.screen.loans)
    implementation(projects.screen.main)
    implementation(projects.screen.onboarding)
    implementation(projects.screen.piechart)
    implementation(projects.screen.plannedPayments)
    implementation(projects.screen.releases)
    implementation(projects.screen.reports)
    implementation(projects.screen.search)
    implementation(projects.screen.settings)
    implementation(projects.screen.smsReview)
    implementation(projects.screen.smsSettings)
    implementation(projects.screen.transactions)
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.sms.capture)
    implementation(projects.shared.sms.parser)
    implementation(projects.shared.ui.navigation)
    implementation(projects.shared.ui.core)
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)
    implementation(projects.widget.addTransaction)
    implementation(projects.widget.balance)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotlin.android)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.activity)
    implementation(libs.bundles.google)
    implementation(libs.datastore)
    implementation(libs.androidx.security)
    implementation(libs.androidx.biometrics)

    implementation(libs.bundles.hilt)
    implementation(libs.material)
    ksp(libs.hilt.compiler)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.timber)
    implementation(libs.keval)
    implementation(libs.bundles.opencsv)
    implementation(libs.androidx.work)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.androidx.work.testing)

    lintChecks(libs.slack.lint.compose)
}
