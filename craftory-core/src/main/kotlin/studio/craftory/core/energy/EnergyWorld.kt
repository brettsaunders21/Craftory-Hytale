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
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class EnergyWorld : Resource<ChunkStore> {
    private class ConverterOutput(
        var amount: Long = 0,
        var rank: Int = 0,
    )

    private val runtime = EnergyRuntimeAdapter()
    private val refsById = HashMap<Long, Ref<ChunkStore>>()
    private val specsById = HashMap<Long, MemberSpec>()
    private val neighboursById = HashMap<Long, LongArray>()
    private val outputIdByInput = HashMap<Long, Long>()
    private val inputByOutput = HashMap<Long, Long>()
    private val converterOutputs = HashMap<Long, ConverterOutput>()
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
        if (refsById.containsKey(id)) return
        makePhysicalIdAvailable(id)
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
        if (refsById.containsKey(inputId)) return
        makePhysicalIdAvailable(inputId)
        val outputId = converterOutputId(inputId)
        addNode(inputId, type.input, inputNeighbours, member, ref, 0, -1)
        addNode(outputId, type.output, outputNeighbours, member, ref, 0, -1)
        converterOutputs[inputId] = ConverterOutput()
        member.networkId = runtime.graph.networkOf(inputId)
    }

    fun converterOutputId(inputId: Long): Long =
        outputIdByInput[inputId] ?: allocateOutputId(inputId).also { outputId ->
            outputIdByInput[inputId] = outputId
            inputByOutput[outputId] = inputId
        }

    fun contains(id: Long): Boolean = refsById.containsKey(id)

    fun removeMember(
        id: Long,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        reason: RemoveReason,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
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
        val outputId = outputIdByInput.remove(inputId)
        if (outputId != null) {
            inputByOutput.remove(outputId)
            removeNode(outputId)
        }
        converterOutputs.remove(inputId)
        removeMember(inputId, member, blockState, reason, accessor)
    }

    fun member(id: Long): EnergyMember? = refsById[id]?.let { ref -> store?.getComponent(ref, EnergyMember.type) }

    fun setProduction(
        id: Long,
        amount: Long,
        rank: Int,
    ) {
        EnergyRequestValidation.productionInput(amount, rank, GradeTable.DEFAULT)
        val member = member(id) ?: return
        val spec =
            requireNotNull(specsById[id]) {
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
            requireNotNull(specsById[id]) {
                "Member $id has an unregistered energy type: ${member.typeId}"
            }
        EnergyRequestValidation.consumer(id, spec)
        member.demand = amount
    }

    fun converterInputDelivered(inputId: Long): Long {
        if (inputId !in outputIdByInput) return 0
        return runtime.graph.delivered(inputId)
    }

    fun setConverterOutput(
        inputId: Long,
        amount: Long,
        rank: Int,
    ) {
        val outputId = outputIdByInput[inputId] ?: return
        val outputSpec = specsById[outputId] ?: return
        EnergyRequestValidation.production(outputId, outputSpec, amount, rank)
        val output = converterOutputs.getOrPut(inputId) { ConverterOutput() }
        if (output.amount == amount && output.rank == rank) return
        output.amount = amount
        output.rank = rank
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
        refsById.forEach { (memberId, ref) ->
            if (runtime.graph.networkOf(memberId) != network) return@forEach
            val member = member(memberId) ?: return@forEach
            val changed = member.updateStored(runtime.graph.stored(memberId), runtime.graph.storedRank(memberId))
            if (!changed) return@forEach
            val currentStore = store ?: return@forEach
            val blockState =
                currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return@forEach
            member.persist(blockState, currentStore, currentStore.externalData.world.tick, 0, immediate = true)
        }
        return true
    }

    fun delivered(id: Long): Long = member(id)?.delivered ?: 0

    fun deliveredRank(id: Long): Int = member(id)?.deliveredRank ?: -1

    fun networkOf(id: Long): Int = member(id)?.networkId ?: -1

    fun diagnostics(id: Long): EnergyNetworkDiagnostics? = runtime.diagnostics(id)

    fun capacity(id: Long): Long = specsById[id]?.capacity ?: 0L

    val networkCount: Int get() = runtime.graph.networkCount
    val awakeNetworkCount: Int get() = runtime.graph.awakeNetworkCount

    @Suppress("CyclomaticComplexMethod")
    fun solve(
        currentStore: Store<ChunkStore>,
        tick: Long,
    ) {
        store = currentStore
        val started = System.nanoTime()
        refsById.forEach { (id, ref) ->
            val member = currentStore.getComponent(ref, EnergyMember.type) ?: return@forEach
            val spec = specsById[id] ?: return@forEach
            val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            val active = blockState?.let { CraftoryCore.isAreaActive(currentStore, it) } ?: false
            if ((spec.role and MemberSpec.STORAGE) != 0) runtime.setStorageEnabled(id, active)
            if ((spec.role and MemberSpec.PRODUCER) != 0) {
                val converterInput = inputByOutput[id]
                val output = converterInput?.let(converterOutputs::get)
                val production = output?.amount ?: member.production
                val rank = output?.rank ?: member.productionRank
                runtime.setProduction(id, if (active) production else 0, rank)
            }
            if ((spec.role and MemberSpec.CONSUMER) != 0) runtime.setDemand(id, if (active) member.demand else 0)
        }
        runtime.tick(events)
        lastSolveNanos = System.nanoTime() - started
        refsById.forEach { (id, ref) ->
            if (id in inputByOutput) return@forEach
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
            val spec = specsById[id]
            val threshold = maxOf(1L, (spec?.capacity ?: 0L) / 100)
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
    ) {
        if (refsById.containsKey(id)) return
        val connected = neighbours.filter(refsById::containsKey).toLongArray()
        runtime.add(id, spec, connected, stored, storeRank)
        refsById[id] = ref
        specsById[id] = spec
        neighboursById[id] = connected
        if (id !in inputByOutput) member.networkId = runtime.graph.networkOf(id)
    }

    private fun removeNode(id: Long): studio.craftory.core.energy.StoredEnergy? {
        if (!refsById.containsKey(id)) return null
        val saved = runtime.remove(id)
        refsById.remove(id)
        specsById.remove(id)
        neighboursById.remove(id)
        return saved
    }

    private fun makePhysicalIdAvailable(id: Long) {
        val inputId = inputByOutput[id] ?: return
        val oldOutputId = id
        val ref = refsById[oldOutputId]
        val member = ref?.let { store?.getComponent(it, EnergyMember.type) }
        val spec = specsById[oldOutputId]
        val oldNeighbours = neighboursById[oldOutputId] ?: longArrayOf()
        val output = converterOutputs[inputId]
        removeNode(oldOutputId)
        inputByOutput.remove(oldOutputId)
        val replacement = allocateOutputId(inputId, id)
        outputIdByInput[inputId] = replacement
        inputByOutput[replacement] = inputId
        neighboursById.replaceAll { _, neighbours ->
            LongArray(neighbours.size) { index ->
                if (neighbours[index] == oldOutputId) replacement else neighbours[index]
            }
        }
        if (member != null && spec != null) {
            val neighbours =
                oldNeighbours
                    .map { if (it == oldOutputId) replacement else it }
                    .toLongArray()
            addNode(replacement, spec, neighbours, member, ref, 0, -1)
            if (output != null) runtime.setProduction(replacement, output.amount, output.rank)
        }
    }

    private fun allocateOutputId(
        inputId: Long,
        forbiddenId: Long = Long.MAX_VALUE,
    ): Long {
        var candidate = EnergyMemberIds.converterOutput(inputId)
        while (outputIdUnavailable(candidate, inputId, forbiddenId)) {
            candidate++
        }
        return candidate
    }

    private fun outputIdUnavailable(
        candidate: Long,
        inputId: Long,
        forbiddenId: Long,
    ): Boolean =
        candidate == inputId ||
            candidate == forbiddenId ||
            candidate in refsById ||
            candidate in inputByOutput

    companion object {
        private const val SAVE_INTERVAL_TICKS = 100L
        lateinit var type: ResourceType<ChunkStore, EnergyWorld>
    }
}
