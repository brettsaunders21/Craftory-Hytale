plugins {
    `java-library`
    id("craftory.kotlin")
    id("craftory.hytale-mod")
}

hytaleTools {
    modId = "CraftoryCore"
    modDescription = "Core services for Craftory Hytale plugins."
    mainClass = "studio.craftory.core.CraftoryCorePlugin"
}

dependencies {
    api(project(":craftory-engine"))
    testRuntimeOnly("com.hypixel.hytale:Server:0.7.4")
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
    exclude("studio/craftory/core/api/tick/**", "studio/craftory/engine/tick/**")
}
