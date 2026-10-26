package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class BoilerState(
    var pressure: Int = 0,
    var water: Long = 0,
    var fuelTicks: Long = 0,
    var online: Boolean = true,
    var lastWhistleTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = BoilerState(pressure, water, fuelTicks, online, lastWhistleTick)

    companion object {
        lateinit var type: ComponentType<ChunkStore, BoilerState>
        val CODEC: BuilderCodec<BoilerState> =
            BuilderCodec
                .builder(BoilerState::class.java, ::BoilerState)
                .append(
                    KeyedCodec("Pressure", Codec.INTEGER),
                    { state, value -> state.pressure = value.coerceIn(0, 100) },
                    { it.pressure },
                ).add()
                .append(
                    KeyedCodec("Water", Codec.LONG),
                    { state, value -> state.water = value.coerceIn(0, 4_000) },
                    { it.water },
                ).add()
                .append(
                    KeyedCodec("FuelTicks", Codec.LONG),
                    { state, value -> state.fuelTicks = value.coerceAtLeast(0) },
                    { it.fuelTicks },
                ).add()
                .append(KeyedCodec("Online", Codec.BOOLEAN), { state, value -> state.online = value }, { it.online })
                .add()
                .append(
                    KeyedCodec("LastWhistleTick", Codec.LONG),
                    { state, value -> state.lastWhistleTick = value },
                    { it.lastWhistleTick },
                ).add()
                .build()
    }
}
