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
        val ownNodes =
            if (converter == null) {
                arrayOf(LinkNode(positionId, linkFaces))
            } else {
                arrayOf(
                    LinkNode(positionId, converter.inputFaces),
                    LinkNode(energy.converterOutputId(positionId), converter.outputFaces, converterOutput = true),
                )
            }
        val neighbours = Array(ownNodes.size) { ArrayList<Long>(6) }
        DIRECTIONS.forEach { direction ->
            if (ownNodes.none { it.faces and direction.face != 0 }) return@forEach
            val x = position.x + direction.x
            val y = position.y + direction.y
            val z = position.z + direction.z
            val sectionRef =
                store.externalData.world.chunkStore
                    .getChunkSectionReferenceAtBlock(x, y, z) ?: return@forEach
            val neighbourRef = BlockModule.getBlockEntity(store, sectionRef, x, y, z) ?: return@forEach
            val neighbour = store.getComponent(neighbourRef, EnergyMember.type) ?: return@forEach
            val neighbourType = CraftoryCore.energyType(neighbour.typeId) ?: return@forEach
            val neighbourPositionId = BlockPos.pack(x, y, z)
            val neighbourConverter = CraftoryCore.converterType(neighbour.typeId)
            val otherNodes =
                if (neighbourConverter == null) {
                    arrayOf(LinkNode(neighbourPositionId, neighbourType.linkFaces))
                } else {
                    arrayOf(
                        LinkNode(neighbourPositionId, neighbourConverter.inputFaces),
                        LinkNode(
                            energy.converterOutputId(neighbourPositionId),
                            neighbourConverter.outputFaces,
                            converterOutput = true,
                        ),
                    )
                }
            ownNodes.forEachIndexed { ownIndex, ownNode ->
                otherNodes.forEach { otherNode ->
                    if (EnergyFaces.connects(ownNode.faces, otherNode.faces, direction.face) &&
                        if (otherNode.converterOutput) {
                            energy.containsConverterOutput(otherNode.id)
                        } else {
                            energy.containsPhysical(otherNode.id)
                        }
                    ) {
                        neighbours[ownIndex].add(otherNode.id)
                    }
                }
            }
        }
        return Array(ownNodes.size) { index -> neighbours[index].toLongArray() }
    }
}
