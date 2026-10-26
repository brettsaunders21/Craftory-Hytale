package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class SnatcherClawState(
    var filterItemId: String = "",
    var filterColour: Int = 0,
    var fizzBuffer: Long = 0,
    var lastFizzSolveTick: Long = Long.MIN_VALUE,
    var lastExtractTick: Long = 0,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> =
        SnatcherClawState(filterItemId, filterColour, fizzBuffer, lastFizzSolveTick, lastExtractTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "filterItemId" to filterItemId,
            "filterColour" to filterColour.toString(),
            "fizzBuffer" to fizzBuffer.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, SnatcherClawState>

        val CODEC: BuilderCodec<SnatcherClawState> =
            BuilderCodec
                .builder(SnatcherClawState::class.java, ::SnatcherClawState)
                .append(
                    KeyedCodec("FilterItemId", Codec.STRING),
                    { state, value -> state.filterItemId = value },
                    { it.filterItemId },
                ).add()
                .append(
                    KeyedCodec("FilterColour", Codec.INTEGER),
                    { state, value -> state.filterColour = value.coerceIn(0, 4) },
                    { it.filterColour },
                ).add()
                .append(
                    KeyedCodec("FizzBuffer", Codec.LONG),
                    { state, value -> state.fizzBuffer = value.coerceIn(0, FIZZ_PER_ITEM) },
                    { it.fizzBuffer },
                ).add()
                .append(
                    KeyedCodec("LastFizzSolveTick", Codec.LONG),
                    { state, value -> state.lastFizzSolveTick = value },
                    { it.lastFizzSolveTick },
                ).add()
                .append(
                    KeyedCodec("LastExtractTick", Codec.LONG),
                    { state, value -> state.lastExtractTick = value },
                    { it.lastExtractTick },
                ).add()
                .build()

        const val FIZZ_PER_ITEM = 2L
        const val EXTRACT_INTERVAL_TICKS = 20L
    }
}
