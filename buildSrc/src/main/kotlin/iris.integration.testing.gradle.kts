plugins {
    id("iris.feature")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.pickFirsts.apply {
            add("win32-x86-64/attach_hotspot_windows.dll")
            add("win32-x86/attach_hotspot_windows.dll")
            add("META-INF/**")
            add("xsd/catalog.xml")
        }
    }
}

dependencies {
    androidTestImplementation(libs.bundles.integration.testing)
}
