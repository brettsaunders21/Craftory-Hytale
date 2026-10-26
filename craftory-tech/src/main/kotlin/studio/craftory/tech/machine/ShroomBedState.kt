package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class ShroomBedState(
    var growth: Double = 0.0,
    var stored: Int = 0,
    var twitch: Int = 0,
    var lastUpdateTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = ShroomBedState(growth, stored, twitch, lastUpdateTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "growth" to growth.toString(),
            "stored" to stored.toString(),
            "twitch" to twitch.toString(),
            "lastUpdateTick" to lastUpdateTick.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, ShroomBedState>

        val CODEC: BuilderCodec<ShroomBedState> =
            BuilderCodec
                .builder(ShroomBedState::class.java, ::ShroomBedState)
                .append(
                    KeyedCodec("Growth", Codec.DOUBLE),
                    { state, value -> state.growth = value.coerceIn(0.0, 1000.0) },
                    { it.growth },
                ).add()
                .append(
                    KeyedCodec("Stored", Codec.INTEGER),
                    { state, value -> state.stored = value.coerceIn(0, 9) },
                    { it.stored },
                ).add()
                .append(
                    KeyedCodec("Twitch", Codec.INTEGER),
                    { state, value -> state.twitch = value.coerceIn(0, 100) },
                    { it.twitch },
                ).add()
                .append(
                    KeyedCodec("LastUpdateTick", Codec.LONG),
                    { state, value -> state.lastUpdateTick = value },
                    { it.lastUpdateTick },
                ).add()
                .build()
    }
}
