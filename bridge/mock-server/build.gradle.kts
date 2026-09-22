plugins {
    application
}

dependencies {
    implementation(project(":bridge:core"))
    implementation(libs.gson)
}

// The shared protocol fixtures are the mock's initial game state.
sourceSets.main {
    resources.srcDir(rootProject.file("protocol/fixtures"))
}

application {
    mainClass.set("dev.zomboidds.bridge.mock.MockServerMain")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    // Options use the same syntax as the agent, e.g. -Pmock.args=port=7786
    args(providers.gradleProperty("mock.args").getOrElse(""))
}
