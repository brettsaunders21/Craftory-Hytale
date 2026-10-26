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

    fun requestDebit(
        id: Long,
        amount: Long,
    ): Long {
        if (amount <= 0) return 0
        val currentStore = store ?: return 0
        val ref = refsById[id] ?: return 0
        val member = currentStore.getComponent(ref, EnergyMember.type) ?: return 0
        val spec = CraftoryCore.energyType(member.typeId)?.spec ?: return 0
        if ((spec.role and MemberSpec.STORAGE) == 0) return 0
        val blockState =
            currentStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return 0
        if (!CraftoryCore.isAreaActive(currentStore, blockState)) return 0

        val removed = runtime.requestDebit(id, amount)
        if (removed > 0) {
            member.stored = runtime.graph.stored(id)
            member.storeRank = runtime.graph.storedRank(id)
            member.unsaved = true
        }
        return removed
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
            member.updateStored(amount, rank)
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
        events.drain(currentStore.externalData.world.worldConfig.uuid)
        lastSolveTick = tick
    }

    companion object {
        private const val SAVE_INTERVAL_TICKS = 100L
        lateinit var type: ResourceType<ChunkStore, EnergyWorld>
    }
}
