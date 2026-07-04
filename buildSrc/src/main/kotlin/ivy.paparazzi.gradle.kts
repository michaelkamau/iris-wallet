plugins {
    id("ivy.compose")
    id("app.cash.paparazzi")
}

tasks.withType<Test>().configureEach {
    // https://github.com/cashapp/paparazzi/issues/2111
    // Paparazzi <= 2.0.0-alpha04 uses internal Gradle APIs for HTML reports that
    // were removed in Gradle 9; disabling HTML reports avoids the breakage.
    reports.html.required = false
}

// TODO Remove when https://github.com/google/guava/issues/6567 is fixed.
// See also: https://github.com/google/guava/issues/6801.
dependencies.constraints {
    testImplementation("com.google.guava:guava") {
        attributes {
            attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM)
            )
        }
        because(
            "Paparazzi's layoutlib and sdk-common depend on Guava's -jre published variant." +
                    "See https://github.com/cashapp/paparazzi/issues/906."
        )
    }
}