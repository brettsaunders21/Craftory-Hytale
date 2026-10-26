plugins {
    base
    alias(libs.plugins.hytale.workspace)
    alias(libs.plugins.spotless)
}

hytaleWorkspace {
    modProjects = listOf(":craftory-core", ":craftory-tech")
    hostProject = ":craftory-core"
    manifestGroup = "Craftory"
    hytaleVersion =
        libs.versions.hytale.server
            .get()
    patchline = "release"
}

gradle.projectsEvaluated {
    val assetDownloads = subprojects.mapNotNull { it.tasks.findByName("downloadAssetsZip") }
    subprojects.forEach { project ->
        val generateAssetsBinary = project.tasks.findByName("generateAssetsBinary") ?: return@forEach
        assetDownloads
            .filter { it.project != project }
            .forEach { downloadTask -> generateAssetsBinary.mustRunAfter(downloadTask) }
    }
}

spotless {
    kotlin {
        target("build-logic/src/**/*.kt", "craftory-*/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }

    kotlinGradle {
        target(
            "*.gradle.kts",
            "build-logic/build.gradle.kts",
            "build-logic/settings.gradle.kts",
            "build-logic/src/main/kotlin/*.gradle.kts",
            "craftory-*/build.gradle.kts",
        )
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
    dependsOn(subprojects.map { it.tasks.named("check") })
}

tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}
