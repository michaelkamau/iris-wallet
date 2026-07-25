plugins {
    id("iris.script")
    application
}

application {
    mainClass = "iris.automate.issue.create.MainKt"
}

dependencies {
    implementation(projects.ciActions.base)
}
