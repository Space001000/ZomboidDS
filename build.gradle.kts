plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.shadow) apply false
}

// Everything under :bridge runs inside the game's JVM, so it must target the game's Java version.
subprojects {
    if (path.startsWith(":bridge:")) {
        plugins.withType<JavaPlugin> {
            tasks.withType<JavaCompile>().configureEach {
                options.release.set(17)
                options.encoding = "UTF-8"
            }
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
        }
    }
}
