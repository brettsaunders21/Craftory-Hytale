package studio.craftory.core.api.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class FluidTank(
    var fluid: String = "",
    var amount: Long = 0,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = FluidTank(fluid, amount)

    companion object {
        lateinit var type: ComponentType<ChunkStore, FluidTank>

        val CODEC: BuilderCodec<FluidTank> =
            BuilderCodec
                .builder(FluidTank::class.java, ::FluidTank)
                .append(
                    KeyedCodec("Fluid", Codec.STRING),
                    { tank, value -> tank.fluid = value },
                    { it.fluid },
                ).add()
                .append(
                    KeyedCodec("Amount", Codec.LONG),
                    { tank, value -> tank.amount = value.coerceAtLeast(0) },
                    { it.amount },
                ).add()
                .build()
    }
}
