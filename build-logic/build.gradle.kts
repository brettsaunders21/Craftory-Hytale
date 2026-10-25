plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.azuredoom.com/mods")
}

dependencies {
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.hytale.tools.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}
