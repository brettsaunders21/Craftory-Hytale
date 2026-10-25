plugins {
    id("craftory.kotlin")
    id("craftory.hytale-mod")
}

hytaleTools {
    modId = "CraftoryCore"
    modDescription = "Core services for Craftory Hytale plugins."
    mainClass = "studio.craftory.core.CraftoryCorePlugin"
}

dependencies {
    implementation(project(":craftory-engine"))
}

tasks.named<Jar>("jar") {
    val runtimeClasspath = configurations.named("runtimeClasspath")

    from({
        runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
