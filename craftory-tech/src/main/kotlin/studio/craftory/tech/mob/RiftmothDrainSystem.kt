package studio.craftory.tech.mob

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.mob.MobHome
import studio.craftory.tech.CraftoryIds
import kotlin.math.floor

internal class RiftmothDrainSystem : EntityTickingSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> = Query.and(MobHome.type, TransformComponent.getComponentType())

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val mobHome = chunk.getComponent(index, MobHome.type) ?: return
        if (mobHome.roleId != CraftoryIds.RIFTMOTH) return
        val world = store.externalData.world
        if ((world.tick + index.toLong()) % TICKS_PER_SECOND != 0L) return
        val position = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: return
        val originX = floor(position.x).toInt()
        val originY = floor(position.y).toInt()
        val originZ = floor(position.z).toInt()
        val chunkStore = world.chunkStore.store

        for (offset in STORAGE_SEARCH_OFFSETS) {
            val x = originX + offset.x
            val y = originY + offset.y
            val z = originZ + offset.z
            val ref = BlockModule.getBlockEntity(world, x, y, z)
            val member = ref?.let { chunkStore.getComponent(it, EnergyMember.type) }
            val role = member?.let { CraftoryCore.energyType(it.typeId)?.spec?.role } ?: 0
            if ((role and MemberSpec.STORAGE) != 0) {
                val removed =
                    CraftoryCore.energyAccess(world).requestDebit(
                        BlockPos.pack(x, y, z),
                        RIFTMOTH_DRAIN_PER_SECOND,
                    )
                if (removed > 0) return
            }
        }
    }

    private companion object {
        const val TICKS_PER_SECOND = 20L
        val STORAGE_SEARCH_OFFSETS =
            buildList {
                for (x in -1..1) {
                    for (y in -1..1) {
                        for (z in -1..1) add(BlockOffset(x, y, z))
                    }
                }
            }
    }
}

private data class BlockOffset(
    val x: Int,
    val y: Int,
    val z: Int,
)

internal const val RIFTMOTH_DRAIN_PER_SECOND = 20L
