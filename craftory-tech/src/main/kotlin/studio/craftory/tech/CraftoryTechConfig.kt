package studio.craftory.tech

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec

class CraftoryTechConfig(
    var scrapRush: ScrapRushSettings = ScrapRushSettings(),
    var resonatorCrystalUses: Long = 200,
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
                    KeyedCodec("ResonatorCrystalUses", Codec.LONG),
                    { config, value -> config.resonatorCrystalUses = value.coerceAtLeast(1) },
                    { it.resonatorCrystalUses },
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
