// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    // AGP 9's built-in Kotlin has a runtime dependency on Kotlin Gradle Plugin (KGP) 2.2.10.
    // Without this override, AGP resolves KGP/KSP at 2.2.10 for its internal built-in-Kotlin
    // compiler classpath while our version catalog (and thus the compose-compiler-plugin
    // artifact) targets a newer Kotlin, causing a ClassCastException when loading the
    // Compose compiler plugin (ABI mismatch between compiler and plugin artifact versions).
    // See: https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin
    dependencies {
        // Keep these in sync with `kotlin` / `ksp-plugin` in gradle/libs.versions.toml.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
    }
}

plugins {
    // Run with:
    // ./gradlew detekt // Simple report in the console
    // ./gradlew detektFormat // To check with enabled auto-correction
    id("iris.detekt")
    id("com.jraska.module.graph.assertion")

    alias(libs.plugins.gradleWrapperUpgrade)

    alias(libs.plugins.koverPlugin)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    kover {
        reports {
            filters {
                excludes {
                    classes(
                        "*Activity",
                        "*Activity\$*",
                        "*.BuildConfig",
                        "dagger.hilt.*",
                        "hilt_aggregated_deps.*",
                        "*.Hilt_*"
                    )
                    annotatedBy("@Composable")
                }
            }
        }
    }
}

wrapperUpgrade {
    gradle {
        create("irisWallet") {
            repo.set("michaelkamau/iris-wallet")
            baseBranch.set("main")
        }
    }
}
