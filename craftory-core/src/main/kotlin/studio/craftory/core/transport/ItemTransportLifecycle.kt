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
import studio.craftory.core.api.transport.ItemPipe
import studio.craftory.engine.transport.NodeRemovalReason

internal class ItemTransportLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> = Query.and(ItemPipe.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val pipe = commandBuffer.getComponent(ref, ItemPipe.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(ItemTransportWorld.type)
        runtime.bind(store)
        runtime.addPipe(BlockPos.pack(position.x, position.y, position.z), ref, pipe)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val pipe = commandBuffer.getComponent(ref, ItemPipe.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(ItemTransportWorld.type)
        runtime.bind(store)
        runtime.removePipe(
            BlockPos.pack(position.x, position.y, position.z),
            pipe,
            when (reason) {
                RemoveReason.UNLOAD -> NodeRemovalReason.UNLOAD
                RemoveReason.REMOVE, RemoveReason.BUILDER_TOOLS_UNDO -> NodeRemovalReason.BREAK
            },
            blockState,
            commandBuffer,
        )
    }
}
