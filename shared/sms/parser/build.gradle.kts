plugins {
    id("iris.feature")
}

android {
    namespace = "com.iris.sms.parser"
}

dependencies {
    api(projects.shared.data.model)
    implementation(projects.shared.base)

    testImplementation(projects.shared.data.modelTesting)
}
