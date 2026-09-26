import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the bridge is. 127.0.0.1 is right on the device itself; override with
// -Pzomboidds.host=10.0.2.2 to reach a mock server on your PC from the emulator (debug builds only:
// release builds allow cleartext to 127.0.0.1 alone, see src/main/res/xml/network_security_config.xml).
val bridgeHost = providers.gradleProperty("zomboidds.host").getOrElse("127.0.0.1")
val bridgePort = providers.gradleProperty("zomboidds.port").getOrElse("7786")

// The app ships the mod and installs it into Zomdroid (setup wizard). Its version comes from mod.info.
val bundledModVersion = rootProject.file("mod/ZomboidDS/42/mod.info").readLines()
    .first { it.startsWith("modversion=") }.substringAfter('=').trim()

// Release signing: a keystore kept outside the repository, described by keystore.properties in the
// project root (storeFile, storePassword, keyAlias, keyPassword; not committed). Without it,
// release builds stay unsigned.
val keystoreProperties: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

val modZip by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

/**
 * Copies the mod zip built by :bridge:adapter-b42 into a generated assets folder, with the licence
 * texts the app shows under "Open-source licences" (assets/legal/).
 */
abstract class BundleModTask : DefaultTask() {
    @get:InputFiles
    abstract val modZip: ConfigurableFileCollection

    @get:InputFiles
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        modZip.singleFile.copyTo(File(out, "ZomboidDS.zip"))
        legalFiles.forEach { it.copyTo(File(out, "legal/${it.name}")) }
    }
}

val bundleMod by tasks.registering(BundleModTask::class) {
    modZip.from(configurations.named("modZip"))
    legalFiles.from(
        rootProject.file("LICENSE"),
        rootProject.file("THIRD_PARTY_NOTICES.md"),
        rootProject.file("licenses/Apache-2.0.txt"),
    )
}

android {
    namespace = "dev.zomboidds.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.zomboidds.companion"
        minSdk = 30 // same as Zomdroid
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"

        buildConfigField("String", "BRIDGE_HOST", "\"$bridgeHost\"")
        buildConfigField("int", "BRIDGE_PORT", bridgePort)
        buildConfigField("String", "BUNDLED_MOD_VERSION", "\"$bundledModVersion\"")
        for ((field, property) in listOf(
            "ZOMBIE_BUDDY_VERSION" to "zombieBuddy.version",
            "ZOMBIE_BUDDY_JAR_SHA256" to "zombieBuddy.jarSha256",
            "ZOMBIE_BUDDY_MOD_FILES_SHA256" to "zombieBuddy.modFilesSha256",
        )) {
            buildConfigField("String", field, "\"${providers.gradleProperty(property).get()}\"")
        }
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // android.util.Log etc. are stubs in local unit tests; let them no-op instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleMod, BundleModTask::outputDir)
    }
}

dependencies {
    modZip(project(path = ":bridge:adapter-b42", configuration = "modZipElements"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit4)
    testImplementation(project(":bridge:mock-server")) // end-to-end tests against the real bridge + fake game
    testImplementation(libs.kotlinx.coroutines.test)
}
