package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class BurnerState(
    var burnTicks: Long = 0,
    var burning: Boolean = false,
    var lastAdvancedTick: Long = 0,
    var visualBurning: Boolean? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = BurnerState(burnTicks, burning, lastAdvancedTick, visualBurning)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "burnTicks" to burnTicks.toString(),
            "burning" to burning.toString(),
            "lastAdvancedTick" to lastAdvancedTick.toString(),
            "visualBurning" to visualBurning.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, BurnerState>

        val CODEC: BuilderCodec<BurnerState> =
            BuilderCodec
                .builder(BurnerState::class.java, ::BurnerState)
                .append(
                    KeyedCodec("BurnTicks", Codec.LONG),
                    { state, value -> state.burnTicks = value },
                    { it.burnTicks },
                ).add()
                .append(KeyedCodec("Burning", Codec.BOOLEAN), { state, value -> state.burning = value }, { it.burning })
                .add()
                .build()
    }
}
