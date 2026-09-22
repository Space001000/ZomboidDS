pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle download the JDK 25 needed to read the game's class files, if none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ZomboidDualscreen"

// Android companion app (bottom screen)
include(":companion-app")

// Game-side bridge (runs inside the Project Zomboid JVM)
include(":bridge:core")          // version-neutral server, protocol, ports
include(":bridge:adapter-b42")   // Build 42 adapter + ZombieBuddy entry point -> ZomboidDS.jar and the mod zip
include(":bridge:mock-server")   // desktop stand-in for the game, for app development
