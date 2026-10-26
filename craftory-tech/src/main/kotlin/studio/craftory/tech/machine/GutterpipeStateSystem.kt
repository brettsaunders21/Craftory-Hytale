package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.FluidPipe
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.core.api.transport.ItemPipe
import studio.craftory.core.api.transport.ItemTransport
import studio.craftory.tech.CraftoryIds

internal data class GutterpipeFace(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val mask: Int,
)

internal object GutterpipeMask {
    // Bits 0-5 are +X, -X, +Y, -Y, +Z, -Z.
    val FACES =
        arrayOf(
            GutterpipeFace(1, 0, 0, 1 shl 0),
            GutterpipeFace(-1, 0, 0, 1 shl 1),
            GutterpipeFace(0, 1, 0, 1 shl 2),
            GutterpipeFace(0, -1, 0, 1 shl 3),
            GutterpipeFace(0, 0, 1, 1 shl 4),
            GutterpipeFace(0, 0, -1, 1 shl 5),
        )

    fun stateName(mask: Int): String = "C${mask.coerceIn(0, 63).toString().padStart(2, '0')}"
}

internal class GutterpipeStateSystem : EntityTickingSystem<ChunkStore>() {
    private val position = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            ItemPipe.type,
            FluidPipe.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        super.tick(dt, chunk, store, commandBuffer)
    }

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState) || !blockState.fillWorldPos(commandBuffer, position)) return

        val world = store.externalData.world
        if ((world.tick + position.x + position.z) % STATE_UPDATE_INTERVAL_TICKS != 0L) return
        if (blockTypeAt(store, position.x, position.y, position.z)?.let(::baseBlockId) != CraftoryIds.PIPE) return

        val itemTransport = ItemTransport.of(world)
        val fluidTransport = FluidTransport.of(world)
        val energy = CraftoryCore.energyAccess(world)
        var mask = 0
        for (face in GutterpipeMask.FACES) {
            val x = position.x + face.dx
            val y = position.y + face.dy
            val z = position.z + face.dz
            val neighborType = blockTypeAt(store, x, y, z)
            val neighborId = neighborType?.let(::baseBlockId)
            val connected =
                neighborId == CraftoryIds.PIPE ||
                    neighborId == CraftoryIds.SNATCHER_CLAW ||
                    itemTransport.hasReceiverAt(store, x, y, z) ||
                    fluidTransport.isNode(x, y, z) ||
                    energy.networkOf(BlockPos.pack(x, y, z)) >= 0
            if (connected) mask = mask or face.mask
        }

        if (setBlockState(world, blockState, commandBuffer, GutterpipeMask.stateName(mask))) {
            blockState.markNeedsSaving(commandBuffer)
        }
    }
}

private fun blockTypeAt(
    store: Store<ChunkStore>,
    x: Int,
    y: Int,
    z: Int,
): BlockType? {
    val sectionRef =
        store.externalData.world.chunkStore
            .getChunkSectionReferenceAtBlock(x, y, z) ?: return null
    val section = store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return null
    return BlockType.getAssetMap().getAsset(section.get(x, y, z))
}

private const val STATE_UPDATE_INTERVAL_TICKS = 10L
