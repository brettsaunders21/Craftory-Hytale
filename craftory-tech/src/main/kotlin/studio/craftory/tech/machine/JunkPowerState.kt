package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class JunkPowerState(
    var machine: String = "",
    var spinTicks: Long = 0,
    var burnTicks: Long = 0,
    var burning: Boolean = false,
    var oreQueue: String = "",
    var grindPulses: Int = 0,
    var jammed: Boolean = false,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> =
        JunkPowerState(machine, spinTicks, burnTicks, burning, oreQueue, grindPulses, jammed)

    companion object {
        lateinit var type: ComponentType<ChunkStore, JunkPowerState>

        val CODEC: BuilderCodec<JunkPowerState> =
            BuilderCodec
                .builder(JunkPowerState::class.java, ::JunkPowerState)
                .append(KeyedCodec("Machine", Codec.STRING), { state, value -> state.machine = value }, { it.machine })
                .add()
                .append(
                    KeyedCodec("SpinTicks", Codec.LONG),
                    { state, value -> state.spinTicks = value },
                    { it.spinTicks },
                ).add()
                .append(
                    KeyedCodec("BurnTicks", Codec.LONG),
                    { state, value -> state.burnTicks = value },
                    { it.burnTicks },
                ).add()
                .append(KeyedCodec("Burning", Codec.BOOLEAN), { state, value -> state.burning = value }, { it.burning })
                .add()
                .append(
                    KeyedCodec("OreQueue", Codec.STRING),
                    { state, value -> state.oreQueue = value },
                    { it.oreQueue },
                ).add()
                .append(
                    KeyedCodec("GrindPulses", Codec.INTEGER),
                    { state, value -> state.grindPulses = value },
                    { it.grindPulses },
                ).add()
                .append(KeyedCodec("Jammed", Codec.BOOLEAN), { state, value -> state.jammed = value }, { it.jammed })
                .add()
                .build()
    }
}
