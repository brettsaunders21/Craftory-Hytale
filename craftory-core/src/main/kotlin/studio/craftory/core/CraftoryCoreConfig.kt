package studio.craftory.core

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import studio.craftory.core.api.multiblock.GhostMode

class CraftoryCoreConfig(
    var anchorMaxChargeMinutes: Int = DEFAULT_ANCHOR_MAX_CHARGE_MINUTES,
    var ghostMode: String = GhostMode.DEFAULT.configValue,
    var transitDisplayCapPerChunk: Int = DEFAULT_TRANSIT_DISPLAY_CAP_PER_CHUNK,
) {
    companion object {
        const val DEFAULT_ANCHOR_MAX_CHARGE_MINUTES = 240
        const val DEFAULT_TRANSIT_DISPLAY_CAP_PER_CHUNK = 16

        val CODEC: BuilderCodec<CraftoryCoreConfig> =
            BuilderCodec
                .builder(CraftoryCoreConfig::class.java, ::CraftoryCoreConfig)
                .append(
                    KeyedCodec("AnchorMaxChargeMinutes", Codec.INTEGER),
                    { config, value -> config.anchorMaxChargeMinutes = value },
                    { it.anchorMaxChargeMinutes },
                ).add()
                .append(
                    KeyedCodec("GhostMode", Codec.STRING),
                    { config, value -> config.ghostMode = value },
                    { it.ghostMode },
                ).add()
                .append(
                    KeyedCodec("TransitDisplayCapPerChunk", Codec.INTEGER),
                    { config, value -> config.transitDisplayCapPerChunk = value.coerceAtLeast(0) },
                    { it.transitDisplayCapPerChunk },
                ).add()
                .build()
    }
}
