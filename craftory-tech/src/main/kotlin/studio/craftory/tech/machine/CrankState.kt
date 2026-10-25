package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class CrankState(
    var spinTicks: Long = 0,
    var lastAdvancedTick: Long = 0,
    var lastPulseTick: Long? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = CrankState(spinTicks, lastAdvancedTick, lastPulseTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "spinTicks" to spinTicks.toString(),
            "lastAdvancedTick" to lastAdvancedTick.toString(),
            "lastPulseTick" to lastPulseTick.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, CrankState>

        val CODEC: BuilderCodec<CrankState> =
            BuilderCodec
                .builder(CrankState::class.java, ::CrankState)
                .append(
                    KeyedCodec("SpinTicks", Codec.LONG),
                    { state, value -> state.spinTicks = value },
                    { it.spinTicks },
                ).add()
                .append(
                    KeyedCodec("LastAdvancedTick", Codec.LONG),
                    { state, value -> state.lastAdvancedTick = value },
                    { it.lastAdvancedTick },
                ).add()
                .build()
    }
}
