pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.azuredoom.com/mods")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://maven.azuredoom.com/mods")
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "craftory-build-logic"
