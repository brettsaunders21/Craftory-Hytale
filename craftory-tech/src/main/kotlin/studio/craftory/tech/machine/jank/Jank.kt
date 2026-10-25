package studio.craftory.tech.machine.jank

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class Jank(
    var value: Double = 0.0,
    var stallTicks: Int = 0,
    var stolen: Boolean = false,
    var effectTicks: Int = 0,
) : Component<ChunkStore>,
    Inspectable {
    val isStalled: Boolean
        get() = stallTicks > 0 || stolen

    override fun clone(): Component<ChunkStore> = Jank(value, stallTicks, stolen, effectTicks)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "value" to value.toString(),
            "stallTicks" to stallTicks.toString(),
            "stolen" to stolen.toString(),
        )

    fun pureState(): JankState = JankState(value, stallTicks, stolen)

    fun apply(state: JankState) {
        value = state.value
        stallTicks = state.stallTicks
        stolen = state.stolen
    }

    companion object {
        lateinit var type: ComponentType<ChunkStore, Jank>

        val CODEC: BuilderCodec<Jank> =
            BuilderCodec
                .builder(Jank::class.java, ::Jank)
                .append(KeyedCodec("Value", Codec.DOUBLE), { jank, value -> jank.value = value }, { it.value })
                .add()
                .append(
                    KeyedCodec("StallTicks", Codec.INTEGER),
                    { jank, value -> jank.stallTicks = value },
                    { it.stallTicks },
                ).add()
                .append(KeyedCodec("Stolen", Codec.BOOLEAN), { jank, value -> jank.stolen = value }, { it.stolen })
                .add()
                .build()
    }
}
