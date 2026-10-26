plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.azuredoom.com/mods")
}

dependencies {
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.hytale.tools.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
}
