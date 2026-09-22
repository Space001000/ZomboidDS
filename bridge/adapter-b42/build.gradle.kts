import java.util.Properties

plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

// --- What we compile against (both provided at runtime by the game) --------------------------

// The game itself: pz.gameDir in local.properties (machine-specific), or -Ppz.gameDir=...
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val pzGameDir: String? = providers.gradleProperty("pz.gameDir").orNull ?: localProperties.getProperty("pz.gameDir")
val gameJar = pzGameDir?.let { file("$it/projectzomboid.jar") }

// ZombieBuddy: pinned in gradle.properties (the version tested on device), downloaded once from its GitHub release.
val zombieBuddyVersion = providers.gradleProperty("zombieBuddy.version").get()
val zombieBuddyJar = layout.buildDirectory.file("zombiebuddy/ZombieBuddy-$zombieBuddyVersion.jar")
val downloadZombieBuddy by tasks.registering {
    val url = "https://github.com/zed-0xff/ZombieBuddy/releases/download/v$zombieBuddyVersion/ZombieBuddy.jar"
    val target = zombieBuddyJar
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        file.parentFile.mkdirs()
        uri(url).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
    }
}

val checkGameJar by tasks.registering {
    val jar = gameJar
    doLast {
        if (jar == null || !jar.isFile) {
            throw GradleException(
                "The B42 adapter compiles against the game. Set pz.gameDir in local.properties to the " +
                    "Project Zomboid folder containing projectzomboid.jar (current: ${jar ?: "not set"}).")
        }
    }
}

dependencies {
    implementation(project(":bridge:core"))

    gameJar?.let { compileOnly(files(it)) }
    compileOnly(files(zombieBuddyJar) { builtBy(downloadZombieBuddy) })

    gameJar?.let { testImplementation(files(it)) } // real Kahlua tables in tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// B42.20's class files are Java 25 (major 69); only a JDK 25 can read them.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.compileJava {
    dependsOn(checkGameJar)
}

// --- Packaging --------------------------------------------------------------------------------

tasks.jar {
    enabled = false
}

// One jar with the bridge core and its libraries. Libraries are moved to our own namespace, outside
// the mod package, so they can't clash with other mods and ZombieBuddy doesn't scan them.
tasks.shadowJar {
    archiveFileName.set("ZomboidDS.jar")
    relocate("com.google.gson", "dev.zomboidds.shaded.gson")
    relocate("fi.iki.elonen", "dev.zomboidds.shaded.nanohttpd")
    exclude("META-INF/maven/**")
}

// The installable mod: mod/ZomboidDS with the jar where mod.info's javaJarFile expects it.
// This zip is what the companion app installs (and what we push during development).
val modZip by tasks.registering(Zip::class) {
    archiveFileName.set("ZomboidDS.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("ZomboidDS") {
        from(rootProject.file("mod/ZomboidDS"))
    }
    into("ZomboidDS/42/media/java/client") {
        from(tasks.shadowJar)
    }
}

tasks.assemble {
    dependsOn(modZip)
}

// Lets the companion app bundle the mod zip (see companion-app/build.gradle.kts).
val modZipElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts {
    add(modZipElements.name, modZip)
}
