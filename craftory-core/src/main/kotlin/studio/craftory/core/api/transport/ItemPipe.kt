package studio.craftory.core.api.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.engine.transport.TransitItemCodec

data class ItemPipeTransitRecord(
    val id: Long,
    val itemId: String?,
    val fromPos: Long,
    val toPos: Long,
    val progress: Int,
)

class ItemPipe(
    var colour: Int = 0,
    var items: String = "",
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = ItemPipe(colour, items)

    fun transitRecords(): List<ItemPipeTransitRecord> =
        TransitItemCodec.decode(items).map { record ->
            ItemPipeTransitRecord(
                record.id,
                record.itemId,
                record.fromPos,
                record.toPos,
                record.progress,
            )
        }

    fun containsTransitItem(
        transitId: Long,
        itemId: String,
        from: BlockPos,
        to: BlockPos,
    ): Boolean =
        TransitItemCodec.decode(items).any { record ->
            record.id == transitId &&
                record.itemId == itemId &&
                record.fromPos == from.pack() &&
                record.toPos == to.pack()
        }

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
