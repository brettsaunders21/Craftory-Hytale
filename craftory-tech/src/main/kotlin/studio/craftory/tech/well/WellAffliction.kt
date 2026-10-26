package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.WellRiskSettings

class WellAffliction(
    var mothIds: Array<String> = emptyArray(),
    var voidIds: Array<String> = emptyArray(),
    var raidNpcIds: Array<String> = emptyArray(),
    var walkerId: String = "",
    var raidStartedTick: Long = 0,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> =
        WellAffliction(mothIds.copyOf(), voidIds.copyOf(), raidNpcIds.copyOf(), walkerId, raidStartedTick)

    companion object {
        lateinit var type: ComponentType<ChunkStore, WellAffliction>

        val CODEC: BuilderCodec<WellAffliction> =
            BuilderCodec
                .builder(WellAffliction::class.java, ::WellAffliction)
                .append(
                    KeyedCodec("MothIds", Codec.STRING_ARRAY),
                    { state, value -> state.mothIds = value },
                    { it.mothIds },
                ).add()
                .append(
                    KeyedCodec("VoidIds", Codec.STRING_ARRAY),
                    { state, value -> state.voidIds = value },
                    { it.voidIds },
                ).add()
                .append(
                    KeyedCodec("RaidNpcIds", Codec.STRING_ARRAY),
                    { state, value -> state.raidNpcIds = value },
                    { it.raidNpcIds },
                ).add()
                .append(
                    KeyedCodec("WalkerId", Codec.STRING),
                    { state, value -> state.walkerId = value },
                    { it.walkerId },
                ).add()
                .append(
                    KeyedCodec("RaidStartedTick", Codec.LONG),
                    { state, value -> state.raidStartedTick = value },
                    { it.raidStartedTick },
                ).add()
                .build()
    }
}

internal object WellAfflictionRules {
    fun shouldCleanse(
        corrupted: Boolean,
        hasNearbyAlignedWell: Boolean,
        mothsConfirmedDead: Boolean,
    ): Boolean = corrupted && (hasNearbyAlignedWell || mothsConfirmedDead)

    fun strengthAfterSiphon(strength: WellStrength): WellStrength =
        when (strength) {
            WellStrength.PRIMAL -> WellStrength.ROARING
            WellStrength.ROARING -> WellStrength.STEADY
            WellStrength.STEADY -> WellStrength.FLICKER
            WellStrength.FLICKER -> WellStrength.FLICKER
        }
}

internal object WellAfflictionRuntimeConfig {
    var settings = WellRiskSettings()
}
