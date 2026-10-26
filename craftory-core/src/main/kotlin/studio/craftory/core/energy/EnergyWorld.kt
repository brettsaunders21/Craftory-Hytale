package studio.craftory.core.energy

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyConverterType
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.EnergyMemberIds
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec
import java.util.logging.Logger
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class EnergyWorld : Resource<ChunkStore> {
    private data class Node(
        val ref: Ref<ChunkStore>,
        val spec: MemberSpec,
        val converterOutput: Boolean = false,
        var outputAmount: Long = 0,
        var outputRank: Int = 0,
        val ownerInputId: Long,
    )

    private val runtime = EnergyRuntimeAdapter()
    private val nodes = HashMap<Long, Node>()
    private val warnedCollisions = HashSet<String>()
    private val events = EnergyEventBuffer()
    private var store: Store<ChunkStore>? = null
    var lastSolveNanos: Long = 0
        private set
    var lastSolveTick: Long = Long.MIN_VALUE
        private set

    override fun clone(): Resource<ChunkStore> = EnergyWorld()

    fun bind(currentStore: Store<ChunkStore>) {
        currentStore.externalData.world.debugAssertInTickingThread()
        store = currentStore
    }

    fun addMember(
        id: Long,
        spec: MemberSpec,
        neighbours: LongArray,
        member: EnergyMember,
        ref: Ref<ChunkStore>,
    ) {
        val existing = nodes[id]
        if (existing != null) {
            if (!EnergyMemberIds.allowPhysicalNode(existing.converterOutput)) {
                warnCollision("member:$id", "real member refused at converter output id $id")
                member.networkId = -1
            }
            return
        }
        addNode(id, spec, neighbours, member, ref, member.stored, member.storeRank)
        member.networkId = runtime.graph.networkOf(id)
    }

    fun addConverter(
        inputId: Long,
        type: EnergyConverterType,
        inputNeighbours: LongArray,
        outputNeighbours: LongArray,
        member: EnergyMember,
        ref: Ref<ChunkStore>,
    ) {
        if (inputId in nodes) {
            member.networkId = -1
            if (nodes[inputId]?.converterOutput == true) {
                warnCollision("converter:$inputId", "converter refused at occupied output id $inputId")
            }
            return
        }
        val outputId = converterOutputId(inputId)
        addNode(inputId, type.input, inputNeighbours, member, ref, 0, -1)
        if (!EnergyMemberIds.allowConverterOutput(outputId in nodes)) {
            warnCollision("output:$outputId", "converter output refused because id $outputId is occupied")
        } else {
            addNode(
                outputId,
                type.output,
                outputNeighbours,
                member,
                ref,
                0,
                -1,
                converterOutput = true,
                ownerInputId = inputId,
            )
        }
        member.networkId = runtime.graph.networkOf(inputId)
    }

    fun converterOutputId(inputId: Long): Long = EnergyMemberIds.converterOutput(inputId)

    fun converterOutputNetwork(inputId: Long): Int = runtime.graph.networkOf(converterOutputId(inputId))

    fun contains(id: Long): Boolean = id in nodes

    fun containsPhysical(id: Long): Boolean = nodes[id]?.converterOutput == false

    fun containsConverterOutput(id: Long): Boolean = nodes[id]?.converterOutput == true

    fun removeMember(
        id: Long,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        reason: RemoveReason,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        if (nodes[id]?.converterOutput == true) return
        val saved = removeNode(id)
        if (saved == null) return
        member.networkId = -1
        if (reason == RemoveReason.UNLOAD) {
            member.stored = saved.amount
            member.storeRank = saved.rank
            member.persist(blockState, accessor, store?.externalData?.world?.tick ?: 0, 0, immediate = true)
        }
    }

    fun removeConverter(
        inputId: Long,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        reason: RemoveReason,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        val outputId = converterOutputId(inputId)
        nodes[outputId]?.let { node ->
            if (EnergyMemberIds.ownsConverterOutput(node.converterOutput, node.ownerInputId, inputId)) {
                removeNode(outputId)
            }
        }
        nodes[inputId]?.let { node ->
            if (!node.converterOutput && node.ownerInputId == inputId) {
                removeMember(inputId, member, blockState, reason, accessor)
            }
        }
    }

    fun member(id: Long): EnergyMember? = nodes[id]?.ref?.let { ref -> store?.getComponent(ref, EnergyMember.type) }

    fun setProduction(
        id: Long,
        amount: Long,
        rank: Int,
    ) {
        EnergyRequestValidation.productionInput(amount, rank, GradeTable.DEFAULT)
        val member = member(id) ?: return
        val spec =
            requireNotNull(nodes[id]?.spec) {
                "Member $id has an unregistered energy type: ${member.typeId}"
            }
        EnergyRequestValidation.producer(id, spec)
        member.production = amount
        member.productionRank = rank
    }

    fun setDemand(
        id: Long,
        amount: Long,
    ) {
        EnergyRequestValidation.demandInput(amount)
        val member = member(id) ?: return
        val spec =
            requireNotNull(nodes[id]?.spec) {
                "Member $id has an unregistered energy type: ${member.typeId}"
            }
        EnergyRequestValidation.consumer(id, spec)
        member.demand = amount
    }

    fun converterInputDelivered(inputId: Long): Long {
        if (nodes[converterOutputId(inputId)]?.converterOutput != true) return 0
        return runtime.graph.delivered(inputId)
    }

    fun setConverterOutput(
        inputId: Long,
        amount: Long,
        rank: Int,
    ) {
        val outputId = converterOutputId(inputId)
        val output = nodes[outputId]?.takeIf { it.converterOutput } ?: return
        val outputSpec = output.spec
        EnergyRequestValidation.production(outputId, outputSpec, amount, rank)
        if (output.outputAmount == amount && output.outputRank == rank) return
        output.outputAmount = amount
        output.outputRank = rank
        runtime.setProduction(outputId, amount, rank)
    }

    fun stored(id: Long): Long = member(id)?.stored ?: 0

    fun networkStored(
        id: Long,
        minRank: Int,
    ): Long = runtime.graph.networkStored(id, minRank)

    fun drawStored(
        id: Long,
        amount: Long,
        minGradeRank: Int,
    ): Boolean {
        if (!runtime.graph.drawStored(id, amount, minGradeRank)) return false
        if (amount == 0L) return true
        val network = runtime.graph.networkOf(id)
        val currentStore = store ?: return true
        nodes.forEach { (memberId, node) ->
            if (node.converterOutput) return@forEach
            if (runtime.graph.networkOf(memberId) != network) return@forEach
            val member = currentStore.getComponent(node.ref, EnergyMember.type) ?: return@forEach
            val changed = member.updateStored(runtime.graph.stored(memberId), runtime.graph.storedRank(memberId))
            if (!changed) return@forEach
            val blockState =
                currentStore.getComponent(node.ref, BlockModule.BlockStateInfo.getComponentType()) ?: return@forEach
            member.persist(blockState, currentStore, currentStore.externalData.world.tick, 0, immediate = true)
        }
        return true
    }

    fun delivered(id: Long): Long = member(id)?.delivered ?: 0

    fun deliveredRank(id: Long): Int = member(id)?.deliveredRank ?: -1

    fun networkOf(id: Long): Int = member(id)?.networkId ?: -1

    fun diagnostics(id: Long): EnergyNetworkDiagnostics? = runtime.diagnostics(id)

    fun capacity(id: Long): Long = nodes[id]?.spec?.capacity ?: 0L

    val networkCount: Int get() = runtime.graph.networkCount
    val awakeNetworkCount: Int get() = runtime.graph.awakeNetworkCount

    @Suppress("CyclomaticComplexMethod")
    fun solve(
        currentStore: Store<ChunkStore>,
        tick: Long,
    ) {
        store = currentStore
        val started = System.nanoTime()
        nodes.forEach { (id, node) ->
            val member = currentStore.getComponent(node.ref, EnergyMember.type) ?: return@forEach
            val spec = node.spec
            val blockState = currentStore.getComponent(node.ref, BlockModule.BlockStateInfo.getComponentType())
            val active = blockState?.let { CraftoryCore.isAreaActive(currentStore, it) } ?: false
            if ((spec.role and MemberSpec.STORAGE) != 0) runtime.setStorageEnabled(id, active)
            if ((spec.role and MemberSpec.PRODUCER) != 0) {
                val production = if (node.converterOutput) node.outputAmount else member.production
                val rank = if (node.converterOutput) node.outputRank else member.productionRank
                runtime.setProduction(id, if (active) production else 0, rank)
            }
            if ((spec.role and MemberSpec.CONSUMER) != 0) runtime.setDemand(id, if (active) member.demand else 0)
        }
        runtime.tick(events)
        lastSolveNanos = System.nanoTime() - started
        nodes.forEach { (id, node) ->
            if (node.converterOutput) return@forEach
            val ref = node.ref
            val member = currentStore.getComponent(ref, EnergyMember.type) ?: return@forEach
            val amount = runtime.graph.stored(id)
            val rank = runtime.graph.storedRank(id)
            member.updateStored(amount, rank)
            val delivered = runtime.graph.delivered(id)
            val deliveredRank = runtime.graph.deliveredRank(id)
            val networkId = runtime.graph.networkOf(id)
            if (member.delivered != delivered) member.delivered = delivered
            if (member.deliveredRank != deliveredRank) member.deliveredRank = deliveredRank
            if (member.networkId != networkId) member.networkId = networkId
            val threshold = maxOf(1L, node.spec.capacity / 100)
            val significant =
                abs(amount - member.lastSavedStored) >= threshold || rank != member.lastSavedStoreRank
            val intervalElapsed = tick - member.lastSavedTick >= SAVE_INTERVAL_TICKS
            if (member.unsaved) {
                val blockState =
                    currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return@forEach
                member.persist(
                    blockState,
                    currentStore,
                    tick,
                    SAVE_INTERVAL_TICKS,
                    immediate = significant || intervalElapsed,
                )
            }
        }
        events.drain(currentStore.externalData.world.worldConfig.uuid)
        lastSolveTick = tick
    }

    private fun addNode(
        id: Long,
        spec: MemberSpec,
        neighbours: LongArray,
        member: EnergyMember,
        ref: Ref<ChunkStore>,
        stored: Long,
        storeRank: Int,
        converterOutput: Boolean = false,
        ownerInputId: Long = id,
    ) {
        if (id in nodes) return
        val connected = neighbours.filter(nodes::containsKey).toLongArray()
        runtime.add(id, spec, connected, stored, storeRank)
        nodes[id] = Node(ref, spec, converterOutput, stored, storeRank, ownerInputId)
        if (!converterOutput) member.networkId = runtime.graph.networkOf(id)
    }

    private fun removeNode(id: Long): studio.craftory.core.energy.StoredEnergy? {
        if (id !in nodes) return null
        val saved = runtime.remove(id)
        nodes.remove(id)
        return saved
    }

    private fun warnCollision(
        key: String,
        message: String,
    ) {
        if (warnedCollisions.add(key)) LOGGER.warning(message)
    }

    companion object {
        private const val SAVE_INTERVAL_TICKS = 100L
        private val LOGGER = Logger.getLogger(EnergyWorld::class.java.name)
        lateinit var type: ResourceType<ChunkStore, EnergyWorld>
    }
}
