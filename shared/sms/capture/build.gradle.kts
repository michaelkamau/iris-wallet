plugins {
    id("iris.feature")
    id("iris.integration.testing")
    id("iris.room")
}

android {
    namespace = "com.iris.sms.capture"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.sms.parser)

    implementation(libs.datastore)

    androidTestImplementation(libs.bundles.integration.testing)
}
