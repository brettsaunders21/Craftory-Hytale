package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import java.util.UUID

class MachineOwner(
    var placerUuid: UUID? = null,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = MachineOwner(placerUuid)

    companion object {
        lateinit var type: ComponentType<ChunkStore, MachineOwner>

        val CODEC: BuilderCodec<MachineOwner> =
            BuilderCodec
                .builder(MachineOwner::class.java, ::MachineOwner)
                .append(
                    KeyedCodec("PlacerUuid", Codec.STRING),
                    { owner, value -> owner.placerUuid = value.takeIf(String::isNotEmpty)?.let(UUID::fromString) },
                    { it.placerUuid?.toString().orEmpty() },
                ).add()
                .build()
    }
}
