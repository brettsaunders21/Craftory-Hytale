package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.well.Glimmerwell

class WellheadState(
    var wellPosition: Long = NO_WELL,
    var harmony: Int = 0,
    var pumping: Boolean = false,
    var wellBirthTick: Long = NO_WELL_BIRTH_TICK,
    var wellTemperament: String = "",
) : Component<ChunkStore> {
    var lastHarmonyTick: Long = Long.MIN_VALUE

    internal fun matchesWell(well: Glimmerwell): Boolean =
        wellBirthTick != NO_WELL_BIRTH_TICK &&
            wellBirthTick == well.birthTick &&
            wellTemperament == well.temperament.name

    override fun clone(): Component<ChunkStore> =
        WellheadState(wellPosition, harmony, pumping, wellBirthTick, wellTemperament)

    companion object {
        const val NO_WELL = Long.MIN_VALUE
        const val NO_WELL_BIRTH_TICK = Long.MIN_VALUE
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
                .append(
                    KeyedCodec("WellBirthTick", Codec.LONG),
                    { state, value -> state.wellBirthTick = value },
                    { it.wellBirthTick },
                ).add()
                .append(
                    KeyedCodec("WellTemperament", Codec.STRING),
                    { state, value -> state.wellTemperament = value },
                    { it.wellTemperament },
                ).add()
                .build()
    }
}
