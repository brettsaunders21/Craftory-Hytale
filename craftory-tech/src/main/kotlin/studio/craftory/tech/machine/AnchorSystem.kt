package studio.craftory.tech.machine

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds

internal class AnchorLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(AnchorState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val state = commandBuffer.getComponent(ref, AnchorState.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = position(blockState, commandBuffer) ?: return
        val active = state.chargeSeconds > 0 && CraftoryCore.anchorMaxChargeMinutes() > 0
        val id = BlockPos.pack(position.x, position.y, position.z)
        state.coverageId = id
        state.coverageActive = false
        if (active) {
            MachineActions.activateAnchor(store.externalData.world, state, blockState, store, position)
        } else {
            CraftoryCore.setAnchorCoverage(store, id, position.x shr 5, position.z shr 5, false)
        }
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = commandBuffer.getComponent(ref, AnchorState.type) ?: return
        val world = store.externalData.world
        val id =
            state.coverageId
                ?: commandBuffer
                    .getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                    ?.let { position(it, commandBuffer) }
                    ?.let { BlockPos.pack(it.x, it.y, it.z) }
        if (id != null) CraftoryCore.removeAnchorCoverage(store, id)
        state.coverageId = null
        releaseKeepLoaded(world, state)
    }
}

internal class AnchorSystem : EntityTickingSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(AnchorState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(
            SystemDependency(Order.BEFORE, CrankSystem::class.java),
            SystemDependency(Order.BEFORE, BurnerSystem::class.java),
            SystemDependency(Order.BEFORE, ScorchOvenSystem::class.java),
            SystemDependency(Order.BEFORE, GnasherSystem::class.java),
            SystemDependency(Order.BEFORE, studio.craftory.core.api.EnergySolveSystem::class.java),
        )

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        super.tick(dt, chunk, store, commandBuffer)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val state = chunk.getComponent(index, AnchorState.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = position(blockState, commandBuffer) ?: return
        val world = store.externalData.world
        val maxChargeSeconds = CraftoryCore.anchorMaxChargeMinutes().toLong() * SECONDS_PER_MINUTE
        val clampedCharge = AnchorChargeMath.clamp(state.chargeSeconds, maxChargeSeconds)
        if (state.chargeSeconds != clampedCharge) {
            state.chargeSeconds = clampedCharge
            blockState.markNeedsSaving(commandBuffer)
        }
        val active = state.chargeSeconds > 0 && maxChargeSeconds > 0
        val id = BlockPos.pack(position.x, position.y, position.z)
        state.coverageId = id
        if (state.coverageActive != active) {
            state.coverageActive = active
            CraftoryCore.setAnchorCoverage(store, id, position.x shr 5, position.z shr 5, active)
        }
        updateArea(world, state, position, active)
        val drain = active && !hasPlayerInArea(world, position)
        val result = AnchorChargeMath.drain(state.chargeSeconds, state.drainRemainderSeconds, dt, drain)
        if (state.chargeSeconds != result.chargeSeconds) {
            state.chargeSeconds = result.chargeSeconds
            blockState.markNeedsSaving(commandBuffer)
        }
        state.drainRemainderSeconds = result.remainderSeconds
        val shouldShowActive = state.chargeSeconds > 0 && maxChargeSeconds > 0
        if (state.visualActive != shouldShowActive) {
            state.visualActive = shouldShowActive
            updateVisual(world, blockState, position, shouldShowActive)
        }
        if (active && state.chargeSeconds == 0L) {
            CraftoryCore.setAnchorCoverage(store, id, position.x shr 5, position.z shr 5, false)
            state.coverageActive = false
            releaseKeepLoaded(world, state)
        }
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun updateArea(
        world: World,
        state: AnchorState,
        position: BlockPos,
        active: Boolean,
    ) {
        val grid = world.chunkStore
        val store = grid.store
        val nonTickingType = ChunkStore.REGISTRY.getNonTickingComponentType()
        val centerChunkX = position.x shr 5
        val centerChunkZ = position.z shr 5
        for (chunkX in centerChunkX - 1..centerChunkX + 1) {
            for (chunkZ in centerChunkZ - 1..centerChunkZ + 1) {
                val chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ)
                val chunkRef = grid.getChunkReference(chunkIndex)
                if (chunkRef != null && chunkRef.isValid) {
                    val worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType())
                    if (worldChunk != null) {
                        if (active && state.keepLoadedChunks.add(chunkIndex)) worldChunk.addKeepLoaded()
                        if (active) {
                            worldChunk.resetActiveTimer()
                            if (store.getArchetype(chunkRef).contains(nonTickingType)) {
                                grid.getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING)
                            }
                        } else if (state.keepLoadedChunks.remove(chunkIndex)) {
                            worldChunk.removeKeepLoaded()
                        }
                    }
                } else {
                    state.keepLoadedChunks.remove(chunkIndex)
                }
                if (!active) continue
                grid.getLoadedSectionReferences(chunkX, chunkZ).forEach { sectionRef ->
                    if (!sectionRef.isValid) return@forEach
                    val section = store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return@forEach
                    section.resetActiveTimer()
                    if (store.getArchetype(sectionRef).contains(nonTickingType)) {
                        grid.getChunkSectionReferenceAsync(
                            section.x,
                            section.y,
                            section.z,
                            GetChunkFlags.SET_TICKING,
                        )
                    }
                }
            }
        }
        if (!active) releaseKeepLoaded(world, state)
    }

    private fun hasPlayerInArea(
        world: World,
        position: BlockPos,
    ): Boolean {
        val grid = world.chunkStore
        val centerX = position.x shr 5
        val centerZ = position.z shr 5
        for (chunkX in centerX - 1..centerX + 1) {
            for (chunkZ in centerZ - 1..centerZ + 1) {
                grid.getLoadedSectionReferences(chunkX, chunkZ).forEach { sectionRef ->
                    if (!sectionRef.isValid) return@forEach
                    val section = grid.store.getComponent(sectionRef, ChunkSection.getComponentType()) ?: return@forEach
                    if (
                        world.playerRefs.any { player ->
                            player.chunkTracker.shouldBeVisible(section.x, section.y, section.z)
                        }
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun updateVisual(
        world: World,
        blockState: BlockModule.BlockStateInfo,
        position: BlockPos,
        active: Boolean,
    ) {
        world.execute {
            BlockOperations.setBlockInteractionState(
                world.chunkStore,
                blockState.sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.fromString(CraftoryIds.WAKEBELL_ANCHOR) ?: return@execute,
                if (active) "On" else "Off",
                true,
            )
        }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60L
    }
}

private fun position(
    blockState: BlockModule.BlockStateInfo,
    store: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
): BlockPos? {
    if (!blockState.sectionRef.isValid) return null
    val section = store.getComponent(blockState.sectionRef, ChunkSection.getComponentType()) ?: return null
    return BlockPos(
        ChunkUtil.worldCoordFromLocalCoord(section.x, ChunkUtil.xFromIndex(blockState.index)),
        ChunkUtil.worldCoordFromLocalCoord(section.y, ChunkUtil.yFromIndex(blockState.index)),
        ChunkUtil.worldCoordFromLocalCoord(section.z, ChunkUtil.zFromIndex(blockState.index)),
    )
}

private fun releaseKeepLoaded(
    world: World,
    state: AnchorState,
) {
    val grid = world.chunkStore
    val store = grid.store
    state.keepLoadedChunks.toList().forEach { chunkIndex ->
        val ref = grid.getChunkReference(chunkIndex)
        if (ref != null && ref.isValid) {
            store.getComponent(ref, WorldChunk.getComponentType())?.removeKeepLoaded()
        }
    }
    state.keepLoadedChunks.clear()
}
