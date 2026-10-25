package studio.craftory.tech.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import java.util.UUID

class HurlStation(
    var name: String = "",
) : Component<ChunkStore> {
    fun rename(
        value: String,
        ref: Ref<ChunkStore>,
        store: Store<ChunkStore>,
    ) {
        name = value.trim().filterNot(Char::isISOControl).take(64)
        store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    }

    fun displayName(position: Long): String {
        val pos = BlockPos.unpack(position)
        return name.ifBlank { "Station ${pos.x},${pos.y},${pos.z}" }
    }

    override fun clone(): Component<ChunkStore> = HurlStation(name)

    companion object {
        lateinit var type: ComponentType<ChunkStore, HurlStation>
        val CODEC: BuilderCodec<HurlStation> =
            BuilderCodec
                .builder(HurlStation::class.java, ::HurlStation)
                .append(KeyedCodec("Name", Codec.STRING), { station, value -> station.name = value }, { it.name })
                .add()
                .build()
    }
}

internal object StationPermissions {
    fun mayVisit(
        owner: UUID?,
        crew: Set<UUID>,
    ): Boolean = owner == null || owner in crew

    fun mayRename(
        owner: UUID?,
        player: UUID,
        crew: Set<UUID>,
        bypass: Boolean,
    ): Boolean = bypass || owner == player || (owner != null && owner in crew)
}
