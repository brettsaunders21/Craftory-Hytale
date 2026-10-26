pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.azuredoom.com/mods")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven.azuredoom.com/mods")
        maven("https://maven.hytale.com/release")
        mavenCentral()
    }
}

rootProject.name = "Craftory-Hytale"

include(
    ":craftory-engine",
    ":craftory-core",
    ":craftory-tech",
)
