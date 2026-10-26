package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class RattlewheelState(
    var waterFlow: Int = 0,
    var windMode: Boolean = false,
    var y: Int = 0,
    var weatherMultiplier: Double = 1.0,
    var fizzPerSecond: Long = 0,
    var lastSampleTick: Long = Long.MIN_VALUE,
    var visualOn: Boolean? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> =
        RattlewheelState(waterFlow, windMode, y, weatherMultiplier, fizzPerSecond, lastSampleTick, visualOn)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "waterFlow" to waterFlow.toString(),
            "windMode" to windMode.toString(),
            "y" to y.toString(),
            "weatherMultiplier" to weatherMultiplier.toString(),
            "fizzPerSecond" to fizzPerSecond.toString(),
            "lastSampleTick" to lastSampleTick.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, RattlewheelState>

        val CODEC: BuilderCodec<RattlewheelState> =
            BuilderCodec
                .builder(RattlewheelState::class.java, ::RattlewheelState)
                .append(
                    KeyedCodec("WaterFlow", Codec.INTEGER),
                    { state, value -> state.waterFlow = value },
                    { it.waterFlow },
                ).add()
                .append(
                    KeyedCodec("WindMode", Codec.BOOLEAN),
                    { state, value -> state.windMode = value },
                    { it.windMode },
                ).add()
                .append(KeyedCodec("Y", Codec.INTEGER), { state, value -> state.y = value }, { it.y })
                .add()
                .append(
                    KeyedCodec("WeatherMultiplier", Codec.DOUBLE),
                    { state, value -> state.weatherMultiplier = value },
                    { it.weatherMultiplier },
                ).add()
                .append(
                    KeyedCodec("FizzPerSecond", Codec.LONG),
                    { state, value -> state.fizzPerSecond = value },
                    { it.fizzPerSecond },
                ).add()
                .append(
                    KeyedCodec("LastSampleTick", Codec.LONG),
                    { state, value -> state.lastSampleTick = value },
                    { it.lastSampleTick },
                ).add()
                .build()
    }
}
