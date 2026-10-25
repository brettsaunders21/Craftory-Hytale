package studio.craftory.core.energy

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.CraftoryCore

internal class EnergyMemberLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val member = commandBuffer.getComponent(ref, EnergyMember.type) ?: return
        val type = CraftoryCore.energyType(member.typeId) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val id = BlockPos.pack(position.x, position.y, position.z)
        val chunks = store.externalData
        val energy = store.getResource(EnergyWorld.type)
        energy.bind(chunks.world, store)
        val neighbours =
            findNeighbours(store, blockState, position)
                .filter(energy::contains)
                .toLongArray()
        energy.addMember(id, type.spec, neighbours, member, blockState)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val member = commandBuffer.getComponent(ref, EnergyMember.type) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val energy = store.getResource(EnergyWorld.type)
        val id = BlockPos.pack(position.x, position.y, position.z)
        if (energy.contains(id)) energy.removeMember(id, member, blockState, commandBuffer)
    }

    private companion object {
        val FACES =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
            )
    }

    private fun findNeighbours(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
    ): LongArray {
        val neighbours = LongArray(FACES.size)
        var count = 0
        FACES.forEach { face ->
            val id = findNeighbourId(store, blockState, position, face) ?: return@forEach
            neighbours[count++] = id
        }
        return neighbours.copyOf(count)
    }

    private fun findNeighbourId(
        store: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
        position: Vector3i,
        face: IntArray,
    ): Long? {
        val neighbourRef =
            BlockModule.getBlockEntity(
                store,
                blockState.sectionRef,
                position.x + face[0],
                position.y + face[1],
                position.z + face[2],
            ) ?: return null
        val neighbour = store.getComponent(neighbourRef, EnergyMember.type) ?: return null
        if (CraftoryCore.energyType(neighbour.typeId) == null) return null
        val neighbourState =
            store.getComponent(neighbourRef, BlockModule.BlockStateInfo.getComponentType()) ?: return null
        val neighbourPosition = Vector3i()
        if (!neighbourState.fillWorldPos(store, neighbourPosition)) return null
        return BlockPos.pack(neighbourPosition.x, neighbourPosition.y, neighbourPosition.z)
    }
}

internal class EnergyWorldTickSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val chunks = store.externalData
        store.getResource(EnergyWorld.type).tick(chunks.world, store)
    }
}
