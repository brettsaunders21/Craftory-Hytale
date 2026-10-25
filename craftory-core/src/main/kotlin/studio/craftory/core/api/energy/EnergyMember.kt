package studio.craftory.core.api.energy

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class EnergyMember(
    var typeId: String = "",
    stored: Long = 0L,
    storeRank: Int = -1,
    var production: Long = 0L,
    var productionRank: Int = 0,
    var demand: Long = 0L,
    var delivered: Long = 0L,
    var deliveredRank: Int = -1,
    var networkId: Int = -1,
    var lastSavedTick: Long = 0L,
    var unsaved: Boolean = false,
    var lastSavedStored: Long = 0L,
    var lastSavedStoreRank: Int = -1,
) : Component<ChunkStore>,
    Inspectable {
    /** The energy solver owns persisted charge and its grade. */
    var stored: Long = stored
        internal set

    /** The energy solver owns persisted charge and its grade. */
    var storeRank: Int = storeRank
        internal set

    override fun clone(): Component<ChunkStore> =
        EnergyMember(
            typeId,
            stored,
            storeRank,
            production,
            productionRank,
            demand,
            delivered,
            deliveredRank,
            networkId,
            lastSavedTick,
            unsaved,
            lastSavedStored,
            lastSavedStoreRank,
        )

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "typeId" to typeId,
            "stored" to stored.toString(),
            "storeRank" to storeRank.toString(),
            "production" to production.toString(),
            "productionRank" to productionRank.toString(),
            "demand" to demand.toString(),
            "delivered" to delivered.toString(),
            "deliveredRank" to deliveredRank.toString(),
            "networkId" to networkId.toString(),
            "lastSavedTick" to lastSavedTick.toString(),
            "unsaved" to unsaved.toString(),
            "lastSavedStored" to lastSavedStored.toString(),
            "lastSavedStoreRank" to lastSavedStoreRank.toString(),
        )

    fun shouldPersist(
        currentTick: Long,
        intervalTicks: Long,
        immediate: Boolean,
    ): Boolean = immediate || (unsaved && currentTick - lastSavedTick >= intervalTicks)

    fun markSaved(currentTick: Long) {
        lastSavedTick = currentTick
        lastSavedStored = stored
        lastSavedStoreRank = storeRank
        unsaved = false
    }

    fun persist(
        blockState: BlockModule.BlockStateInfo,
        accessor: ComponentAccessor<ChunkStore>,
        currentTick: Long,
        intervalTicks: Long,
        immediate: Boolean,
    ) {
        if (immediate) unsaved = true
        if (!shouldPersist(currentTick, intervalTicks, immediate)) return
        blockState.markNeedsSaving(accessor)
        markSaved(currentTick)
    }

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
