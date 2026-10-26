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
import studio.craftory.core.api.transport.FluidPipe
import studio.craftory.core.api.transport.FluidTank
import studio.craftory.engine.transport.FluidNodeRemovalReason

internal class FluidPipeLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(FluidPipe.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val pipe =
            commandBuffer.getComponent(ref, FluidPipe.type)
                ?: store.getComponent(ref, FluidPipe.type)
                ?: return
        val blockState =
            commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(FluidTransportWorld.type)
        runtime.bind(store)
        runtime.addPipe(BlockPos.pack(position.x, position.y, position.z), ref, pipe)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val pipe =
            commandBuffer.getComponent(ref, FluidPipe.type)
                ?: store.getComponent(ref, FluidPipe.type)
                ?: return
        val blockState =
            commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(FluidTransportWorld.type)
        runtime.bind(store)
        runtime.removePipe(
            BlockPos.pack(position.x, position.y, position.z),
            pipe,
            removalReason(reason),
            blockState,
            commandBuffer,
        )
    }
}

internal class FluidTankLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(FluidTank.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val tank =
            commandBuffer.getComponent(ref, FluidTank.type)
                ?: store.getComponent(ref, FluidTank.type)
                ?: return
        val blockState =
            commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(FluidTransportWorld.type)
        runtime.bind(store)
        runtime.addTank(BlockPos.pack(position.x, position.y, position.z), ref, tank)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val tank =
            commandBuffer.getComponent(ref, FluidTank.type)
                ?: store.getComponent(ref, FluidTank.type)
                ?: return
        val blockState =
            commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val runtime = store.getResource(FluidTransportWorld.type)
        runtime.bind(store)
        runtime.removeTank(
            BlockPos.pack(position.x, position.y, position.z),
            tank,
            removalReason(reason),
            blockState,
            commandBuffer,
        )
    }
}

private fun removalReason(reason: RemoveReason): FluidNodeRemovalReason =
    when (reason) {
        RemoveReason.UNLOAD -> FluidNodeRemovalReason.UNLOAD
        RemoveReason.REMOVE, RemoveReason.BUILDER_TOOLS_UNDO -> FluidNodeRemovalReason.BREAK
    }
