plugins {
    id("craftory.kotlin")
    id("craftory.hytale-mod")
}

hytaleTools {
    modId = "CraftoryTech"
    modDescription = "Technology modules for Craftory Hytale."
    mainClass = "studio.craftory.tech.CraftoryTechPlugin"
    manifestDependencies = "Hytale:AssetModule=*,Craftory:CraftoryCore=*"
}

dependencies {
    add("vineCompileOnly", project(":craftory-core"))
    testImplementation(project(":craftory-core"))
    testImplementation("com.hypixel.hytale:Server:0.7.4")
}
