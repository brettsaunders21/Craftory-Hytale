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
import studio.craftory.core.api.BlockFootprints
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyFaces
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
        val converter = CraftoryCore.converterType(member.typeId)
        if (converter != null) {
            val neighbours = findNeighbours(store, position, id, type.linkFaces, converter, energy)
            energy.addConverter(id, converter, neighbours[0], neighbours[1], member, ref)
        } else {
            val neighbours = findNeighbours(store, position, id, type.linkFaces, null, energy)
            energy.addMember(id, type.spec, neighbours[0], member, ref)
        }
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
        if (CraftoryCore.converterType(member.typeId) != null) {
            energy.removeConverter(id, member, blockState, reason, commandBuffer)
        } else if (energy.containsPhysical(id)) {
            energy.removeMember(id, member, blockState, reason, commandBuffer)
        }
    }

    private companion object {
        val DIRECTIONS =
            arrayOf(
                Direction(1, 0, 0, EnergyFaces.EAST),
                Direction(-1, 0, 0, EnergyFaces.WEST),
                Direction(0, 1, 0, EnergyFaces.UP),
                Direction(0, -1, 0, EnergyFaces.DOWN),
                Direction(0, 0, 1, EnergyFaces.SOUTH),
                Direction(0, 0, -1, EnergyFaces.NORTH),
            )
    }

    private data class Direction(
        val x: Int,
        val y: Int,
        val z: Int,
        val face: Int,
    )

    private data class LinkNode(
        val id: Long,
        val faces: Int,
        val converterOutput: Boolean = false,
    )

    private fun findNeighbours(
        store: Store<ChunkStore>,
        position: Vector3i,
        positionId: Long,
        linkFaces: Int,
        converter: studio.craftory.core.api.energy.EnergyConverterType?,
        energy: EnergyWorld,
    ): Array<LongArray> {
        val ownNodes = linkNodes(positionId, linkFaces, converter, energy)
        val ownCells = BlockFootprints.cells(store, position.x, position.y, position.z)
        val ownCellIds = ownCells.mapTo(HashSet()) { BlockPos.pack(it.x, it.y, it.z) }
        val neighbours = Array(ownNodes.size) { LinkedHashSet<Long>() }
        ownCells.forEach { ownCell ->
            DIRECTIONS.forEach directionLoop@{ direction ->
                if (ownNodes.none { it.faces and direction.face != 0 }) return@directionLoop
                val otherNodes =
                    neighbourNodes(store, energy, positionId, ownCellIds, ownCell, direction) ?: return@directionLoop
                connectNodes(ownNodes, otherNodes, direction, energy, neighbours)
            }
        }
        return Array(ownNodes.size) { index -> neighbours[index].toLongArray() }
    }

    private fun linkNodes(
        positionId: Long,
        linkFaces: Int,
        converter: studio.craftory.core.api.energy.EnergyConverterType?,
        energy: EnergyWorld,
    ): Array<LinkNode> =
        if (converter == null) {
            arrayOf(LinkNode(positionId, linkFaces))
        } else {
            arrayOf(
                LinkNode(positionId, converter.inputFaces),
                LinkNode(energy.converterOutputId(positionId), converter.outputFaces, converterOutput = true),
            )
        }

    private fun neighbourNodes(
        store: Store<ChunkStore>,
        energy: EnergyWorld,
        positionId: Long,
        ownCellIds: Set<Long>,
        ownCell: Vector3i,
        direction: Direction,
    ): Array<LinkNode>? {
        val neighbourCell = Vector3i(ownCell.x + direction.x, ownCell.y + direction.y, ownCell.z + direction.z)
        if (BlockPos.pack(neighbourCell.x, neighbourCell.y, neighbourCell.z) in ownCellIds) return null
        val origin = BlockFootprints.origin(store, neighbourCell.x, neighbourCell.y, neighbourCell.z)
        val originId = BlockPos.pack(origin.x, origin.y, origin.z)
        if (originId == positionId) return null
        val sectionRef =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(origin.x, origin.y, origin.z)
                ?: return null
        val neighbourRef = BlockModule.getBlockEntity(store, sectionRef, origin.x, origin.y, origin.z) ?: return null
        val neighbour = store.getComponent(neighbourRef, EnergyMember.type) ?: return null
        val type = CraftoryCore.energyType(neighbour.typeId) ?: return null
        return linkNodes(originId, type.linkFaces, CraftoryCore.converterType(neighbour.typeId), energy)
    }

    private fun connectNodes(
        ownNodes: Array<LinkNode>,
        otherNodes: Array<LinkNode>,
        direction: Direction,
        energy: EnergyWorld,
        neighbours: Array<LinkedHashSet<Long>>,
    ) {
        ownNodes.forEachIndexed { ownIndex, ownNode ->
            otherNodes.forEach { otherNode ->
                val active =
                    if (otherNode.converterOutput) {
                        energy.containsConverterOutput(otherNode.id)
                    } else {
                        energy.containsPhysical(otherNode.id)
                    }
                if (active && EnergyFaces.connects(ownNode.faces, otherNode.faces, direction.face)) {
                    neighbours[ownIndex].add(otherNode.id)
                }
            }
        }
    }
}
