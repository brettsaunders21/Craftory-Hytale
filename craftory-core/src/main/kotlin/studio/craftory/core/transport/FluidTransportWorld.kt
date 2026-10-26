@file:Suppress("TooManyFunctions")

package studio.craftory.core.transport

import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.FluidHandler
import studio.craftory.core.api.transport.FluidPipe
import studio.craftory.core.api.transport.FluidTank
import studio.craftory.core.api.transport.FluidTransport
import studio.craftory.core.api.transport.FluidTransportAccess
import studio.craftory.engine.transport.FluidDirtyNodeVisitor
import studio.craftory.engine.transport.FluidNetworkGraph
import studio.craftory.engine.transport.FluidNodeKind
import studio.craftory.engine.transport.FluidNodeRecord
import studio.craftory.engine.transport.FluidNodeRemovalReason
import studio.craftory.engine.transport.PackedPosition

internal class FluidTransportWorld : Resource<ChunkStore> {
    private val graph = FluidNetworkGraph()
    private val fluidKeys = ArrayList<String>()
    private val fluidIndices = HashMap<String, Int>()
    private val refsByPosition = HashMap<Long, Ref<ChunkStore>>()
    private val pipePositions = HashSet<Long>()
    private var store: Store<ChunkStore>? = null
    private var lastProcessedTick = Long.MIN_VALUE
    private val dirtyVisitor = FluidDirtyVisitor()
    val access = FluidTransportAccess(this)

    override fun clone(): Resource<ChunkStore> = FluidTransportWorld()

    fun bind(currentStore: Store<ChunkStore>) {
        currentStore.externalData.world.debugAssertInTickingThread()
        store = currentStore
    }

    fun addPipe(
        pos: Long,
        ref: Ref<ChunkStore>,
        pipe: FluidPipe,
    ) {
        addNode(pos, ref, FluidNodeKind.PIPE, pipe.fluid, pipe.amount)
        pipePositions.add(pos)
    }

    fun addTank(
        pos: Long,
        ref: Ref<ChunkStore>,
        tank: FluidTank,
    ) {
        addNode(pos, ref, FluidNodeKind.TANK, tank.fluid, tank.amount)
        pipePositions.remove(pos)
    }

    fun removePipe(
        pos: Long,
        pipe: FluidPipe,
        reason: FluidNodeRemovalReason,
        blockState: BlockModule.BlockStateInfo,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        removeNode(pos, reason) { record ->
            pipe.fluid = fluidName(record.fluidKey)
            pipe.amount = record.amount
            blockState.markNeedsSaving(accessor)
        }
        pipePositions.remove(pos)
    }

    fun removeTank(
        pos: Long,
        tank: FluidTank,
        reason: FluidNodeRemovalReason,
        blockState: BlockModule.BlockStateInfo,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        removeNode(pos, reason) { record ->
            tank.fluid = fluidName(record.fluidKey)
            tank.amount = record.amount
            blockState.markNeedsSaving(accessor)
        }
        pipePositions.remove(pos)
    }

    fun tick(
        currentStore: Store<ChunkStore>,
        tick: Long,
    ) {
        bind(currentStore)
        if (tick == lastProcessedTick || tick % TRANSPORT_INTERVAL_TICKS != 0L) return
        lastProcessedTick = tick
        for ((pos, ref) in refsByPosition) {
            processNode(currentStore, pos, ref)
        }
        graph.drainDirty(tick, dirtyVisitor)
    }

    private fun processNode(
        currentStore: Store<ChunkStore>,
        pos: Long,
        ref: Ref<ChunkStore>,
    ) {
        val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(currentStore, blockState)) return
        if (pos in pipePositions && graph.connectedNeighbourCount(pos) <= 1) pullSource(currentStore, pos)
        pushToHandlers(currentStore, pos)
    }

    fun flushDirtyChunk(
        currentStore: Store<ChunkStore>,
        chunkX: Int,
        chunkZ: Int,
        tick: Long,
    ) {
        bind(currentStore)
        graph.drainDirtyInChunk(tick, chunkX, chunkZ, dirtyVisitor)
    }

    fun amount(pos: Long): Long = graph.amountAt(pos)

    fun capacity(pos: Long): Long = graph.capacityAt(pos)

    fun fluid(pos: Long): String? = fluidName(graph.fluidKeyAt(pos)).ifEmpty { null }

    fun isNode(pos: Long): Boolean = graph.containsNode(pos)

    fun insert(
        pos: Long,
        fluid: String,
        amount: Long,
    ): Long {
        val key = intern(fluid)
        return graph.insert(pos, key, amount)
    }

    fun drain(
        pos: Long,
        amount: Long,
    ): Long = graph.drain(pos, amount)

    fun insert(
        currentStore: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        fluid: String,
        amount: Long,
    ): Long {
        val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return 0
        val position = Vector3i()
        if (!blockState.fillWorldPos(currentStore, position)) return 0
        val accepted = insert(BlockPos.pack(position.x, position.y, position.z), fluid, amount)
        if (accepted > 0) blockState.markNeedsSaving(currentStore)
        return accepted
    }

    private fun addNode(
        pos: Long,
        ref: Ref<ChunkStore>,
        kind: FluidNodeKind,
        fluid: String,
        amount: Long,
    ) {
        if (graph.containsNode(pos)) {
            refsByPosition[pos] = ref
            return
        }
        refsByPosition[pos] = ref
        val key = if (fluid.isBlank() || amount <= 0) FluidNodeRecord.NO_FLUID else intern(fluid)
        val storedAmount = if (key < 0) 0 else amount.coerceAtLeast(0)
        graph.addNode(pos, kind, FluidNodeRecord(key, storedAmount))
    }

    private fun removeNode(
        pos: Long,
        reason: FluidNodeRemovalReason,
        persist: (FluidNodeRecord) -> Unit,
    ) {
        if (!graph.containsNode(pos)) return
        val record = graph.removeNode(pos, reason)
        refsByPosition.remove(pos)
        if (reason == FluidNodeRemovalReason.UNLOAD && record != null) persist(record)
    }

    private fun pullSource(
        currentStore: Store<ChunkStore>,
        pos: Long,
    ) {
        val x = PackedPosition.x(pos)
        val y = PackedPosition.y(pos)
        val z = PackedPosition.z(pos)
        for (face in FACES) {
            if (pullSourceFromFace(currentStore, pos, x, y, z, face)) return
        }
    }

    private fun pullSourceFromFace(
        currentStore: Store<ChunkStore>,
        pos: Long,
        x: Int,
        y: Int,
        z: Int,
        face: IntArray,
    ): Boolean {
        val sourceX = x + face[0]
        val sourceY = y + face[1]
        val sourceZ = z + face[2]
        val sectionRef =
            currentStore.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(sourceX, sourceY, sourceZ)
                ?: return false
        val section = currentStore.getComponent(sectionRef, FluidSection.getComponentType()) ?: return false
        if (section.getFluidId(sourceX, sourceY, sourceZ) != WATER_SOURCE_INDEX) return false
        graph.insert(pos, intern(WATER_SOURCE), SOURCE_PULL_AMOUNT)
        return true
    }

    private fun pushToHandlers(
        currentStore: Store<ChunkStore>,
        pos: Long,
    ) {
        val key = graph.fluidKeyAt(pos)
        val fluid = fluidName(key)
        if (fluid.isEmpty()) return
        val x = PackedPosition.x(pos)
        val y = PackedPosition.y(pos)
        val z = PackedPosition.z(pos)
        for (face in FACES) {
            if (!pushToHandlerFace(currentStore, pos, fluid, x, y, z, face)) return
        }
    }

    private fun pushToHandlerFace(
        currentStore: Store<ChunkStore>,
        pos: Long,
        fluid: String,
        x: Int,
        y: Int,
        z: Int,
        face: IntArray,
    ): Boolean {
        val targetX = x + face[0]
        val targetY = y + face[1]
        val targetZ = z + face[2]
        val sectionRef =
            currentStore.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(targetX, targetY, targetZ)
                ?: return true
        val targetRef = BlockModule.getBlockEntity(currentStore, sectionRef, targetX, targetY, targetZ) ?: return true
        for ((componentType, handler) in FluidTransport.handlers()) {
            @Suppress("UNCHECKED_CAST")
            val typedComponent =
                componentType as com.hypixel.hytale.component.ComponentType<ChunkStore, Component<ChunkStore>>
            val component = currentStore.getComponent(targetRef, typedComponent) ?: continue
            val available = graph.amountAt(pos)
            if (available <= 0) return false
            val requested = minOf(available, HANDLER_TRANSFER_AMOUNT)

            @Suppress("UNCHECKED_CAST")
            val typedHandler = handler as FluidHandler<Component<ChunkStore>>
            val accepted = typedHandler.fill(component, fluid, requested).coerceIn(0, requested)
            if (accepted > 0) {
                graph.drain(pos, accepted)
                currentStore
                    .getComponent(targetRef, BlockModule.BlockStateInfo.getComponentType())
                    ?.markNeedsSaving(currentStore)
            }
        }
        return true
    }

    private fun flushNode(pos: Long) {
        val currentStore = store ?: return
        val ref = refsByPosition[pos] ?: return
        val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val record = graph.nodeRecordAt(pos)
        if (pos in pipePositions) {
            val pipe = currentStore.getComponent(ref, FluidPipe.type) ?: return
            pipe.fluid = fluidName(record.fluidKey)
            pipe.amount = record.amount
        } else {
            val tank = currentStore.getComponent(ref, FluidTank.type) ?: return
            tank.fluid = fluidName(record.fluidKey)
            tank.amount = record.amount
            setTankState(currentStore, blockState, pos, record.amount, graph.capacityAt(pos))
        }
        blockState.markNeedsSaving(currentStore)
    }

    private fun setTankState(
        currentStore: Store<ChunkStore>,
        blockState: BlockModule.BlockStateInfo,
        pos: Long,
        amount: Long,
        capacity: Long,
    ) {
        val sectionRef = blockState.sectionRef
        val blockSection = currentStore.getComponent(sectionRef, BlockSection.getComponentType()) ?: return
        val x = PackedPosition.x(pos)
        val y = PackedPosition.y(pos)
        val z = PackedPosition.z(pos)
        val blockType = BlockType.getAssetMap().getAsset(blockSection.get(x, y, z)) ?: return
        if (blockType.getState() == null) return
        val state =
            when {
                amount <= 0 -> "Empty"
                amount * 4 <= capacity -> "Quarter"
                amount * 2 <= capacity -> "Half"
                amount * 4 <= capacity * 3 -> "ThreeQuarter"
                else -> "Full"
            }
        if (blockType.getStateForBlock(blockType) == state || blockType.getBlockForState(state) == null) return
        BlockOperations.setBlockInteractionState(
            currentStore.externalData.world.chunkStore,
            sectionRef,
            x,
            y,
            z,
            blockType,
            state,
            false,
        )
    }

    private fun intern(fluid: String): Int {
        val existing = fluidIndices[fluid]
        if (existing != null) return existing
        val key = fluidKeys.size
        fluidKeys.add(fluid)
        fluidIndices[fluid] = key
        return key
    }

    private fun fluidName(key: Int): String = if (key < 0 || key >= fluidKeys.size) "" else fluidKeys[key]

    private inner class FluidDirtyVisitor : FluidDirtyNodeVisitor {
        override fun visit(nodePos: Long) = flushNode(nodePos)
    }

    companion object {
        private const val TRANSPORT_INTERVAL_TICKS = 20L
        private const val SOURCE_PULL_AMOUNT = 50L
        private const val HANDLER_TRANSFER_AMOUNT = 50L
        private const val WATER_SOURCE = "Water_Source"
        private val WATER_SOURCE_INDEX: Int = Fluid.getAssetMap().getIndex(WATER_SOURCE)
        private val FACES =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
            )
        lateinit var type: ResourceType<ChunkStore, FluidTransportWorld>
    }
}
