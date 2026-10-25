package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class GnasherState(
    var oreQueue: String = "",
    progressPulses: Int = 0,
    var jammed: Boolean = false,
    var lastAutoCrankTick: Long = Long.MIN_VALUE,
    var lastPulseTick: Long? = null,
) : Component<ChunkStore>,
    Inspectable {
    var progressPulses: Int = progressPulses.coerceIn(0, GnasherMachine.PULSES_PER_ORE)
        set(value) {
            field = value.coerceIn(0, GnasherMachine.PULSES_PER_ORE)
        }

    override fun clone(): Component<ChunkStore> =
        GnasherState(oreQueue, progressPulses, jammed, lastAutoCrankTick, lastPulseTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "oreQueue" to oreQueue,
            "queuedOreCount" to GritTable.queuedOreCount(oreQueue).toString(),
            "progressPulses" to progressPulses.toString(),
            "jammed" to jammed.toString(),
            "lastAutoCrankTick" to lastAutoCrankTick.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, GnasherState>

        val CODEC: BuilderCodec<GnasherState> =
            BuilderCodec
                .builder(GnasherState::class.java, ::GnasherState)
                .append(
                    KeyedCodec("OreQueue", Codec.STRING),
                    { state, value -> state.oreQueue = value },
                    { it.oreQueue },
                ).add()
                .append(
                    KeyedCodec("ProgressPulses", Codec.INTEGER),
                    { state, value -> state.progressPulses = value },
                    { it.progressPulses },
                ).add()
                .append(
                    KeyedCodec("Jammed", Codec.BOOLEAN),
                    { state, value -> state.jammed = value },
                    { it.jammed },
                ).add()
                .build()
    }
}
