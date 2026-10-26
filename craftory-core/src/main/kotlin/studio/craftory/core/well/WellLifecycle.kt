package studio.craftory.core.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellIndex

internal class WellLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(Glimmerwell.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        store.getResource(WellIndex.type).add(BlockPos.pack(position.x, position.y, position.z), ref)
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
        val packed = BlockPos.pack(position.x, position.y, position.z)
        store.getResource(WellIndex.type).remove(packed)
        if (reason == RemoveReason.REMOVE) {
            CraftoryCore.wells().fireWellRemoved(store.externalData.world, BlockPos(position.x, position.y, position.z))
        }
    }
}
