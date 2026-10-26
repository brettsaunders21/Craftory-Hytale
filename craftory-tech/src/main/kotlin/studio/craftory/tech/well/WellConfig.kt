package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec

data class WellConfig(
    var stormcallIntervalSeconds: Int = 180,
    var stormcallChance: Double = 0.35,
    var rainFlickerChance: Double = 0.1,
    var killFlickerChance: Double = 0.05,
    var playerFlickerCooldownSeconds: Int = 120,
    var scanPeriodTicks: Int = 40,
    var scanWindowTicks: Int = 8,
    var scanTimeoutTicks: Int = 600,
    var pulseRadius: Int = 32,
    var strongWildWellRegionBudget: Int = 1,
    var jarAmbushChance: Double = 0.15,
) {
    companion object {
        val CODEC: BuilderCodec<WellConfig> =
            BuilderCodec
                .builder(WellConfig::class.java, ::WellConfig)
                .append(
                    KeyedCodec("StormcallIntervalSeconds", Codec.INTEGER),
                    { config, value -> config.stormcallIntervalSeconds = value },
                    { it.stormcallIntervalSeconds },
                ).add()
                .append(
                    KeyedCodec("StormcallChance", Codec.DOUBLE),
                    { config, value -> config.stormcallChance = value },
                    { it.stormcallChance },
                ).add()
                .append(
                    KeyedCodec("RainFlickerChance", Codec.DOUBLE),
                    { config, value -> config.rainFlickerChance = value },
                    { it.rainFlickerChance },
                ).add()
                .append(
                    KeyedCodec("KillFlickerChance", Codec.DOUBLE),
                    { config, value -> config.killFlickerChance = value },
                    { it.killFlickerChance },
                ).add()
                .append(
                    KeyedCodec("PlayerFlickerCooldownSeconds", Codec.INTEGER),
                    { config, value -> config.playerFlickerCooldownSeconds = value },
                    { it.playerFlickerCooldownSeconds },
                ).add()
                .append(
                    KeyedCodec("ScanPeriodTicks", Codec.INTEGER),
                    { config, value -> config.scanPeriodTicks = value },
                    { it.scanPeriodTicks },
                ).add()
                .append(
                    KeyedCodec("ScanWindowTicks", Codec.INTEGER),
                    { config, value -> config.scanWindowTicks = value },
                    { it.scanWindowTicks },
                ).add()
                .append(
                    KeyedCodec("ScanTimeoutTicks", Codec.INTEGER),
                    { config, value -> config.scanTimeoutTicks = value },
                    { it.scanTimeoutTicks },
                ).add()
                .append(
                    KeyedCodec("PulseRadius", Codec.INTEGER),
                    { config, value -> config.pulseRadius = value },
                    { it.pulseRadius },
                ).add()
                .append(
                    KeyedCodec("StrongWildWellRegionBudget", Codec.INTEGER),
                    { config, value -> config.strongWildWellRegionBudget = value },
                    { it.strongWildWellRegionBudget },
                ).add()
                .append(
                    KeyedCodec("JarAmbushChance", Codec.DOUBLE),
                    { config, value -> config.jarAmbushChance = value },
                    { it.jarAmbushChance },
                ).add()
                .build()
    }
}
