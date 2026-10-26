import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.detekt")
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val detektConfigs =
    buildList {
        add(rootProject.file("config/detekt/detekt.yml"))
        file("config/detekt/detekt.yml").takeIf { it.isFile }?.let(::add)
    }

dependencies {
    testImplementation(versionCatalog.findLibrary("junit-jupiter").get())
    testRuntimeOnly(versionCatalog.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

detekt {
    toolVersion = versionCatalog.findVersion("detekt").get().requiredVersion
    buildUponDefaultConfig = true
    config.setFrom(detektConfigs)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "25"
}
