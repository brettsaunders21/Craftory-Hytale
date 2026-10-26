package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class StormVaneState(
    var visualOn: Boolean = false,
    var lastUpdateTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = StormVaneState(visualOn, lastUpdateTick)

    override fun describe(): List<Pair<String, String>> =
        listOf("visualOn" to visualOn.toString(), "lastUpdateTick" to lastUpdateTick.toString())

    companion object {
        lateinit var type: ComponentType<ChunkStore, StormVaneState>

        val CODEC: BuilderCodec<StormVaneState> =
            BuilderCodec
                .builder(StormVaneState::class.java, ::StormVaneState)
                .append(
                    KeyedCodec("VisualOn", Codec.BOOLEAN),
                    { state, value -> state.visualOn = value },
                    { it.visualOn },
                ).add()
                .append(
                    KeyedCodec("LastUpdateTick", Codec.LONG),
                    { state, value -> state.lastUpdateTick = value },
                    { it.lastUpdateTick },
                ).add()
                .build()
    }
}

class FizzDamperState(
    var visualOn: Boolean = false,
    var lastFoamTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = FizzDamperState(visualOn, lastFoamTick)

    override fun describe(): List<Pair<String, String>> =
        listOf("visualOn" to visualOn.toString(), "lastFoamTick" to lastFoamTick.toString())

    companion object {
        lateinit var type: ComponentType<ChunkStore, FizzDamperState>

        val CODEC: BuilderCodec<FizzDamperState> =
            BuilderCodec
                .builder(FizzDamperState::class.java, ::FizzDamperState)
                .append(
                    KeyedCodec("VisualOn", Codec.BOOLEAN),
                    { state, value -> state.visualOn = value },
                    { it.visualOn },
                ).add()
                .append(
                    KeyedCodec("LastFoamTick", Codec.LONG),
                    { state, value -> state.lastFoamTick = value },
                    { it.lastFoamTick },
                ).add()
                .build()
    }
}

class TetherStakeState(
    var wellId: String = "",
    var leaning: Boolean = false,
    var placerUuid: String = "",
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = TetherStakeState(wellId, leaning, placerUuid)

    override fun describe(): List<Pair<String, String>> =
        listOf("wellId" to wellId, "leaning" to leaning.toString(), "placerUuid" to placerUuid)

    companion object {
        lateinit var type: ComponentType<ChunkStore, TetherStakeState>

        val CODEC: BuilderCodec<TetherStakeState> =
            BuilderCodec
                .builder(TetherStakeState::class.java, ::TetherStakeState)
                .append(KeyedCodec("WellId", Codec.STRING), { state, value -> state.wellId = value }, { it.wellId })
                .add()
                .append(KeyedCodec("Lean", Codec.BOOLEAN), { state, value -> state.leaning = value }, { it.leaning })
                .add()
                .append(
                    KeyedCodec("PlacerUuid", Codec.STRING),
                    { state, value -> state.placerUuid = value },
                    { it.placerUuid },
                ).add()
                .build()
    }
}
