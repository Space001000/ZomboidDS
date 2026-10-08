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

// DEVELOPMENT ONLY: the dev mod's version, "<modversion>-dev.<fingerprint>". The fingerprint changes
// with any file of the mod, its test kits (mod/dev) or the bridge, so the app's setup checklist
// offers the update after every change during development, without bumping mod.info.
val devModVersion: String by extra {
    val base = file("mod/ZomboidDS/42/mod.info").readLines().first { it.startsWith("modversion=") }.substringAfter('=').trim()
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    listOf("mod/ZomboidDS", "mod/dev", "bridge/core/src/main", "bridge/adapter-b42/src/main")
        .flatMap { root -> fileTree(root).files.map { it.relativeTo(rootDir).invariantSeparatorsPath to it } }
        .sortedBy { it.first }
        .forEach { (path, f) -> digest.update(path.toByteArray()); digest.update(f.readBytes()) }
    "$base-dev." + digest.digest().take(3).joinToString("") { "%02x".format(it) }
}
