package studio.craftory.core.energy

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
import studio.craftory.core.api.energy.EnergyMember

internal class EnergyMemberLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(EnergyMember.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.LOAD && reason != AddReason.SPAWN) return
        val member = commandBuffer.getComponent(ref, EnergyMember.type) ?: return
        val type = CraftoryCore.energyType(member.typeId) ?: return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val id = BlockPos.pack(position.x, position.y, position.z)
        val energy = store.getResource(EnergyWorld.type)
        energy.bind(store)
        val neighbours =
            findNeighbours(store, position)
                .filter(energy::contains)
                .toLongArray()
        energy.addMember(id, type.spec, neighbours, member, ref)
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
        if (energy.contains(id)) energy.removeMember(id, member, blockState, reason, commandBuffer)
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
        position: Vector3i,
    ): LongArray {
        val neighbours = LongArray(FACES.size)
        var count = 0
        FACES.forEach { face ->
            val id = findNeighbourId(store, position, face) ?: return@forEach
            neighbours[count++] = id
        }
        return neighbours.copyOf(count)
    }

    private fun findNeighbourId(
        store: Store<ChunkStore>,
        position: Vector3i,
        face: IntArray,
    ): Long? {
        val x = position.x + face[0]
        val y = position.y + face[1]
        val z = position.z + face[2]
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return null
        val neighbourRef =
            BlockModule.getBlockEntity(
                store,
                sectionRef,
                x,
                y,
                z,
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
