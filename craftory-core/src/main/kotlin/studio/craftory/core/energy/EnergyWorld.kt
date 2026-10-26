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
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class EnergyWorld : Resource<ChunkStore> {
    private val runtime = EnergyRuntimeAdapter()
    private val refsById = HashMap<Long, Ref<ChunkStore>>()
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
        runtime.add(id, spec, neighbours, member.stored, member.storeRank)
        refsById[id] = ref
        member.networkId = runtime.graph.networkOf(id)
    }

    fun contains(id: Long): Boolean = refsById.containsKey(id)

    fun removeMember(
        id: Long,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        reason: RemoveReason,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        if (!refsById.containsKey(id)) return
        val saved = runtime.remove(id)
        refsById.remove(id)
        member.networkId = -1
        if (reason == RemoveReason.UNLOAD) {
            member.stored = saved.amount
            member.storeRank = saved.rank
            member.persist(blockState, accessor, store?.externalData?.world?.tick ?: 0, 0, immediate = true)
        }
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
            requireNotNull(CraftoryCore.energyType(member.typeId)?.spec) {
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
            requireNotNull(CraftoryCore.energyType(member.typeId)?.spec) {
                "Member $id has an unregistered energy type: ${member.typeId}"
            }
        EnergyRequestValidation.consumer(id, spec)
        member.demand = amount
    }

    fun stored(id: Long): Long = member(id)?.stored ?: 0

    fun drainStored(
        id: Long,
        amount: Long,
    ): Long {
        if (amount <= 0) return 0
        val member = member(id) ?: return 0
        val stored = runtime.graph.stored(id)
        val drained = minOf(stored, amount)
        if (drained == 0L) return 0
        val remaining = stored - drained
        val rank = if (remaining == 0L) -1 else runtime.graph.storedRank(id)
        runtime.graph.restoreStored(id, remaining, rank)
        member.stored = remaining
        member.storeRank = rank
        member.unsaved = true
        return drained
    }

    fun delivered(id: Long): Long = member(id)?.delivered ?: 0

    fun deliveredRank(id: Long): Int = member(id)?.deliveredRank ?: -1

    fun networkOf(id: Long): Int = member(id)?.networkId ?: -1

    fun diagnostics(id: Long): EnergyNetworkDiagnostics? = runtime.diagnostics(id)

    fun capacity(id: Long): Long = member(id)?.let { CraftoryCore.energyType(it.typeId)?.spec?.capacity } ?: 0L

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
            val spec = CraftoryCore.energyType(member.typeId)?.spec ?: return@forEach
            val blockState = currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
            val active = blockState?.let { CraftoryCore.isAreaActive(currentStore, it) } ?: false
            if ((spec.role and MemberSpec.STORAGE) != 0) runtime.setStorageEnabled(id, active)
            if ((spec.role and MemberSpec.PRODUCER) != 0) {
                runtime.setProduction(id, if (active) member.production else 0, member.productionRank)
            }
            if ((spec.role and MemberSpec.CONSUMER) != 0) runtime.setDemand(id, if (active) member.demand else 0)
        }
        runtime.tick(events)
        lastSolveNanos = System.nanoTime() - started
        refsById.forEach { (id, ref) ->
            val member = currentStore.getComponent(ref, EnergyMember.type) ?: return@forEach
            val amount = runtime.graph.stored(id)
            val rank = runtime.graph.storedRank(id)
            if (member.stored != amount || member.storeRank != rank) {
                member.stored = amount
                member.storeRank = rank
                member.unsaved = true
            }
            val delivered = runtime.graph.delivered(id)
            val deliveredRank = runtime.graph.deliveredRank(id)
            val networkId = runtime.graph.networkOf(id)
            if (member.delivered != delivered) member.delivered = delivered
            if (member.deliveredRank != deliveredRank) member.deliveredRank = deliveredRank
            if (member.networkId != networkId) member.networkId = networkId
            val spec = CraftoryCore.energyType(member.typeId)?.spec
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
        events.drain()
        lastSolveTick = tick
    }

    companion object {
        private const val SAVE_INTERVAL_TICKS = 100L
        lateinit var type: ResourceType<ChunkStore, EnergyWorld>
    }
}
