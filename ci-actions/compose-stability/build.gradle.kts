plugins {
    id("iris.script")
    application
}

application {
    mainClass = "iris.automate.compose.stability.MainKt"
}

dependencies {
    implementation(projects.ciActions.base)
}
