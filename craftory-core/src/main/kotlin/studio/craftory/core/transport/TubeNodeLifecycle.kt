package studio.craftory.core.transport

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
import studio.craftory.core.api.transport.TubeNode

internal class TubeNodeLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> = Query.and(TubeNode.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val node = commandBuffer.getComponent(ref, TubeNode.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        store.externalData.world.debugAssertInTickingThread()
        store.getResource(TubeWorld.type).add(BlockPos.pack(position.x, position.y, position.z), node)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason == RemoveReason.UNLOAD) return
        commandBuffer.getComponent(ref, TubeNode.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        store.externalData.world.debugAssertInTickingThread()
        store.getResource(TubeWorld.type).remove(BlockPos.pack(position.x, position.y, position.z))
    }
}
