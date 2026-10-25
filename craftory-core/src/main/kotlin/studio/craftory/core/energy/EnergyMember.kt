package studio.craftory.core.energy

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

internal class EnergyMember(
    var typeId: String = "",
    var stored: Long = 0L,
    var storeRank: Int = -1,
) : Component<ChunkStore> {
    override fun clone(): Component<ChunkStore> = EnergyMember(typeId, stored, storeRank)

    companion object {
        lateinit var type: ComponentType<ChunkStore, EnergyMember>

        val CODEC: BuilderCodec<EnergyMember> =
            BuilderCodec
                .builder(EnergyMember::class.java, ::EnergyMember)
                .append(
                    KeyedCodec("TypeId", Codec.STRING),
                    { member, value -> member.typeId = value },
                    { it.typeId },
                ).add()
                .append(
                    KeyedCodec("Stored", Codec.LONG),
                    { member, value -> member.stored = value },
                    { it.stored },
                ).add()
                .append(
                    KeyedCodec("StoreRank", Codec.INTEGER),
                    { member, value -> member.storeRank = value },
                    { it.storeRank },
                ).add()
                .build()
    }
}
