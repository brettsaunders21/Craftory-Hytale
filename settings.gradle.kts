pluginManagement {
    includeBuild("build-logic")

    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
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
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "Craftory-Hytale"

include(
    ":craftory-engine",
    ":craftory-core",
    ":craftory-tech",
)
