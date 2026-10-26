package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class WellheadState(
    var wellPosition: Long = NO_WELL,
    var harmony: Int = 0,
    var pumping: Boolean = false,
) : Component<ChunkStore> {
    var lastHarmonyTick: Long = Long.MIN_VALUE

    override fun clone(): Component<ChunkStore> = WellheadState(wellPosition, harmony, pumping)

    companion object {
        const val NO_WELL = Long.MIN_VALUE
        lateinit var type: ComponentType<ChunkStore, WellheadState>
        val CODEC: BuilderCodec<WellheadState> =
            BuilderCodec
                .builder(WellheadState::class.java, ::WellheadState)
                .append(
                    KeyedCodec("WellPosition", Codec.LONG),
                    { state, value -> state.wellPosition = value },
                    { it.wellPosition },
                ).add()
                .append(
                    KeyedCodec("Harmony", Codec.INTEGER),
                    { state, value -> state.harmony = value },
                    { it.harmony },
                ).add()
                .append(
                    KeyedCodec("Pumping", Codec.BOOLEAN),
                    { state, value -> state.pumping = value },
                    { it.pumping },
                ).add()
                .build()
    }
}
