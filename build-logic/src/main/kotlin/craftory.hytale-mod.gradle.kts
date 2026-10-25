plugins {
    id("com.azuredoom.hytale-tools")
}

hytaleTools {
    javaVersion = 25
    manifestServerVersion = ">=0.7.0 <0.8.0"
    modCredits = "Brett_Saunders"
    modUrl = "https://github.com/brettsaunders21/Craftory-Hytale"
    manifestDependencies = "Hytale:AssetModule=*"
    includesPack = true
}
