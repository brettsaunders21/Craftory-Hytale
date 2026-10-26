package studio.craftory.tech

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec

class CraftoryTechConfig(
    var scrapRush: ScrapRushSettings = ScrapRushSettings(),
    var grizzlecogVisits: GrizzlecogVisitsSettings = GrizzlecogVisitsSettings(),
    var resonatorCrystalUses: Long = 200,
    var wellRisk: WellRiskSettings = WellRiskSettings(),
) {
    companion object {
        val CODEC: BuilderCodec<CraftoryTechConfig> =
            BuilderCodec
                .builder(CraftoryTechConfig::class.java, ::CraftoryTechConfig)
                .append(
                    KeyedCodec("ScrapRush", ScrapRushSettings.CODEC),
                    { config, value -> config.scrapRush = value },
                    { it.scrapRush },
                ).add()
                .append(
                    KeyedCodec("GrizzlecogVisits", GrizzlecogVisitsSettings.CODEC),
                    { config, value -> config.grizzlecogVisits = value },
                    { it.grizzlecogVisits },
                ).add()
                .append(
                    KeyedCodec("ResonatorCrystalUses", Codec.LONG),
                    { config, value -> config.resonatorCrystalUses = value.coerceAtLeast(1) },
                    { it.resonatorCrystalUses },
                ).add()
                .append(
                    KeyedCodec("WellRisk", WellRiskSettings.CODEC),
                    { config, value -> config.wellRisk = value },
                    { it.wellRisk },
                ).add()
                .build()
    }
}

class WellRiskSettings(
    var riftmothIntervalSeconds: Int = 300,
    var riftmothChancePercent: Int = 20,
    var riftmothFeedSecondsToCorrupt: Int = 60,
    var voidSpawnIntervalSeconds: Int = 120,
    var voidSpawnCap: Int = 3,
    var poacherRaidIntervalSeconds: Int = 1200,
    var poacherRaidChancePercent: Int = 10,
    var siphonSeconds: Int = 120,
) {
    companion object {
        val CODEC: BuilderCodec<WellRiskSettings> =
            BuilderCodec
                .builder(WellRiskSettings::class.java, ::WellRiskSettings)
                .append(
                    KeyedCodec("RiftmothIntervalSeconds", Codec.INTEGER),
                    { settings, value -> settings.riftmothIntervalSeconds = value.coerceAtLeast(0) },
                    { it.riftmothIntervalSeconds },
                ).add()
                .append(
                    KeyedCodec("RiftmothChancePercent", Codec.INTEGER),
                    { settings, value -> settings.riftmothChancePercent = value.coerceIn(0, 100) },
                    { it.riftmothChancePercent },
                ).add()
                .append(
                    KeyedCodec("RiftmothFeedSecondsToCorrupt", Codec.INTEGER),
                    { settings, value -> settings.riftmothFeedSecondsToCorrupt = value.coerceAtLeast(1) },
                    { it.riftmothFeedSecondsToCorrupt },
                ).add()
                .append(
                    KeyedCodec("VoidSpawnIntervalSeconds", Codec.INTEGER),
                    { settings, value -> settings.voidSpawnIntervalSeconds = value.coerceAtLeast(1) },
                    { it.voidSpawnIntervalSeconds },
                ).add()
                .append(
                    KeyedCodec("VoidSpawnCap", Codec.INTEGER),
                    { settings, value -> settings.voidSpawnCap = value.coerceAtLeast(0) },
                    { it.voidSpawnCap },
                ).add()
                .append(
                    KeyedCodec("PoacherRaidIntervalSeconds", Codec.INTEGER),
                    { settings, value -> settings.poacherRaidIntervalSeconds = value.coerceAtLeast(0) },
                    { it.poacherRaidIntervalSeconds },
                ).add()
                .append(
                    KeyedCodec("PoacherRaidChancePercent", Codec.INTEGER),
                    { settings, value -> settings.poacherRaidChancePercent = value.coerceIn(0, 100) },
                    { it.poacherRaidChancePercent },
                ).add()
                .append(
                    KeyedCodec("SiphonSeconds", Codec.INTEGER),
                    { settings, value -> settings.siphonSeconds = value.coerceAtLeast(1) },
                    { it.siphonSeconds },
                ).add()
                .build()
    }
}

class GrizzlecogVisitsSettings(
    var enabled: Boolean = true,
    var averageIntervalInGameDays: Double = 2.0,
    var nearbyRadius: Int = 64,
    var spawnDistanceMin: Int = 8,
    var spawnDistanceMax: Int = 12,
) {
    companion object {
        val CODEC: BuilderCodec<GrizzlecogVisitsSettings> =
            BuilderCodec
                .builder(GrizzlecogVisitsSettings::class.java, ::GrizzlecogVisitsSettings)
                .append(
                    KeyedCodec("Enabled", Codec.BOOLEAN),
                    { config, value -> config.enabled = value },
                    { it.enabled },
                ).add()
                .append(
                    KeyedCodec("AverageIntervalInGameDays", Codec.DOUBLE),
                    { config, value -> config.averageIntervalInGameDays = value.coerceAtLeast(0.001) },
                    { it.averageIntervalInGameDays },
                ).add()
                .append(
                    KeyedCodec("NearbyRadius", Codec.INTEGER),
                    { config, value -> config.nearbyRadius = value.coerceAtLeast(0) },
                    { it.nearbyRadius },
                ).add()
                .append(
                    KeyedCodec("SpawnDistanceMin", Codec.INTEGER),
                    { config, value -> config.spawnDistanceMin = value.coerceAtLeast(1) },
                    { it.spawnDistanceMin },
                ).add()
                .append(
                    KeyedCodec("SpawnDistanceMax", Codec.INTEGER),
                    { config, value ->
                        config.spawnDistanceMax = value.coerceAtLeast(config.spawnDistanceMin)
                    },
                    { it.spawnDistanceMax },
                ).add()
                .build()
    }
}

class ScrapRushSettings(
    var enabled: Boolean = true,
    var averageIntervalInGameDays: Double = 2.0,
    var crewRadius: Int = 64,
    var minGoblins: Int = 3,
    var maxGoblins: Int = 5,
    var stealSeconds: Int = 10,
) {
    companion object {
        val CODEC: BuilderCodec<ScrapRushSettings> =
            BuilderCodec
                .builder(ScrapRushSettings::class.java, ::ScrapRushSettings)
                .append(
                    KeyedCodec("Enabled", Codec.BOOLEAN),
                    { config, value -> config.enabled = value },
                    { it.enabled },
                ).add()
                .append(
                    KeyedCodec("AverageIntervalInGameDays", Codec.DOUBLE),
                    { config, value -> config.averageIntervalInGameDays = value },
                    { it.averageIntervalInGameDays },
                ).add()
                .append(
                    KeyedCodec("CrewRadius", Codec.INTEGER),
                    { config, value -> config.crewRadius = value },
                    { it.crewRadius },
                ).add()
                .append(
                    KeyedCodec("MinGoblins", Codec.INTEGER),
                    { config, value -> config.minGoblins = value },
                    { it.minGoblins },
                ).add()
                .append(
                    KeyedCodec("MaxGoblins", Codec.INTEGER),
                    { config, value -> config.maxGoblins = value },
                    { it.maxGoblins },
                ).add()
                .append(
                    KeyedCodec("StealSeconds", Codec.INTEGER),
                    { config, value -> config.stealSeconds = value },
                    { it.stealSeconds },
                ).add()
                .build()
    }
}
