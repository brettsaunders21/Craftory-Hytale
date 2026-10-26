package studio.craftory.core.api.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class FluidPipe(
    var fluid: String = "",
    var amount: Long = 0,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = FluidPipe(fluid, amount)

    companion object {
        lateinit var type: ComponentType<ChunkStore, FluidPipe>

        val CODEC: BuilderCodec<FluidPipe> =
            BuilderCodec
                .builder(FluidPipe::class.java, ::FluidPipe)
                .append(
                    KeyedCodec("Fluid", Codec.STRING),
                    { pipe, value -> pipe.fluid = value },
                    { it.fluid },
                ).add()
                .append(
                    KeyedCodec("Amount", Codec.LONG),
                    { pipe, value -> pipe.amount = value.coerceAtLeast(0) },
                    { it.amount },
                ).add()
                .build()
    }
}
