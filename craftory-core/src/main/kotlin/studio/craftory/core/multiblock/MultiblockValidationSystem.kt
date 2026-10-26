package studio.craftory.core.multiblock

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.multiblock.MultiblockController

/**
 * Safety net for block changes that raise no player place/break event (explosions, server edits)
 * and for footprint sections that loaded after their controller: active controllers rescan on a slow, staggered clock.
 */
internal class MultiblockValidationSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(MultiblockController.type, BlockModule.BlockStateInfo.getComponentType())

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val controller = chunk.getComponent(index, MultiblockController.type) ?: return
        val world = store.externalData.world
        if (world.tick < controller.nextValidationTick) return
        controller.nextValidationTick = world.tick + INTERVAL_TICKS
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState)) return
        val ref = chunk.getReferenceTo(index)
        world.execute {
            if (ref.isValid) CraftoryCore.multiblocks().recheck(world.chunkStore.store, ref)
        }
    }

    companion object {
        const val INTERVAL_TICKS = 40L
    }
}
