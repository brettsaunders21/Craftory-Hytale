package studio.craftory.core.multiblock

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.core.api.multiblock.MultiblockGeometry

internal class MultiblockLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(MultiblockController.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val controller = commandBuffer.getComponent(ref, MultiblockController.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val template = CraftoryCore.multiblocks().template(controller.templateId) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        if (controller.rotation !in 0 until MultiblockGeometry.ROTATIONS) {
            controller.rotation = placedQuarterTurns(store, blockState, position)
            blockState.markNeedsSaving(commandBuffer)
        }
        val controllerPos = BlockPos(position.x, position.y, position.z)
        val parts =
            MultiblockGeometry
                .worldParts(template, controllerPos, controller.rotation)
                .map { it.position.pack() }
                .toLongArray()
        store.getResource(MultiblockIndex.type).add(controllerPos.pack(), parts)
        val world = store.externalData.world
        controller.readySinceTick = world.tick
        world.execute {
            if (ref.isValid) CraftoryCore.multiblocks().recheck(world.chunkStore.store, ref)
        }
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        store.getResource(MultiblockIndex.type).remove(BlockPos.pack(position.x, position.y, position.z))
    }

    private fun placedQuarterTurns(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
    ): Int {
        val section = store.getComponent(blockState.sectionRef, BlockSection.getComponentType()) ?: return 0
        val rotation = RotationTuple.get(section.getRotationIndex(position.x, position.y, position.z))
        return Math.floorMod(rotation.yaw().degrees / DEGREES_PER_TURN, MultiblockGeometry.ROTATIONS)
    }

    private companion object {
        const val DEGREES_PER_TURN = 90
    }
}
