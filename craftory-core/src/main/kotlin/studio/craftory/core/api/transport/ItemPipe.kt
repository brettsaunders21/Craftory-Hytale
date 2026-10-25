package studio.craftory.core.api.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

class ItemPipe(
    var colour: Int = 0,
    var items: String = "",
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = ItemPipe(colour, items)

    companion object {
        lateinit var type: ComponentType<ChunkStore, ItemPipe>

        val CODEC: BuilderCodec<ItemPipe> =
            BuilderCodec
                .builder(ItemPipe::class.java, ::ItemPipe)
                .append(
                    KeyedCodec("Colour", Codec.INTEGER),
                    { pipe, value -> pipe.colour = value.coerceIn(0, 4) },
                    { it.colour },
                ).add()
                .append(
                    KeyedCodec("Items", Codec.STRING),
                    { pipe, value -> pipe.items = value },
                    { it.items },
                ).add()
                .build()
    }
}
