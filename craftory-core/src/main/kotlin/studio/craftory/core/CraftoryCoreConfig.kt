package studio.craftory.core

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec

class CraftoryCoreConfig(
    var anchorMaxChargeMinutes: Int = DEFAULT_ANCHOR_MAX_CHARGE_MINUTES,
) {
    companion object {
        const val DEFAULT_ANCHOR_MAX_CHARGE_MINUTES = 240

        val CODEC: BuilderCodec<CraftoryCoreConfig> =
            BuilderCodec
                .builder(CraftoryCoreConfig::class.java, ::CraftoryCoreConfig)
                .append(
                    KeyedCodec("AnchorMaxChargeMinutes", Codec.INTEGER),
                    { config, value -> config.anchorMaxChargeMinutes = value },
                    { it.anchorMaxChargeMinutes },
                ).add()
                .build()
    }
}
