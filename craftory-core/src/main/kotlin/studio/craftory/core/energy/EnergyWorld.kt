package studio.craftory.core.energy

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.engine.tick.TickScheduler

@Suppress("TooManyFunctions")
internal class EnergyWorld : Resource<ChunkStore> {
    private data class Entry(
        val member: EnergyMember,
        val blockState: BlockModule.BlockStateInfo,
        val capacity: Long,
        var savedAmount: Long,
        var savedRank: Int,
        var savedTick: Long,
    )

    private val runtime = EnergyRuntimeAdapter()
    private val scheduler = TickScheduler()
    private val entries = HashMap<Long, Entry>()
    private val events = EnergyEventBuffer()
    private var world: World? = null
    private var store: Store<ChunkStore>? = null
    private var schedulerRegistered = false
    var lastSolveNanos: Long = 0
        private set
    private var lastSolveTick: Long = 0

    override fun clone(): Resource<ChunkStore> = EnergyWorld()

    fun bind(
        world: World,
        store: Store<ChunkStore>,
    ) {
        world.debugAssertInTickingThread()
        check(this.world == null || this.world === world) { "EnergyWorld cannot be shared between worlds" }
        this.world = world
        this.store = store
        if (!schedulerRegistered) {
            scheduler.register(world.name.hashCode().toLong(), SOLVE_INTERVAL_TICKS, ::solve)
            schedulerRegistered = true
        }
    }

    fun tick(
        world: World,
        store: Store<ChunkStore>,
    ) {
        bind(world, store)
        scheduler.tick(world.tick)
    }

    fun addMember(
        id: Long,
        spec: MemberSpec,
        neighbours: LongArray,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
    ) {
        world?.debugAssertInTickingThread()
        if (entries.containsKey(id)) return
        runtime.add(id, spec, neighbours, member.stored, member.storeRank)
        entries[id] =
            Entry(
                member,
                blockState,
                spec.capacity,
                member.stored,
                member.storeRank,
                world?.tick ?: 0,
            )
    }

    fun removeMember(
        id: Long,
        member: EnergyMember,
        blockState: BlockModule.BlockStateInfo,
        accessor: com.hypixel.hytale.component.ComponentAccessor<ChunkStore>,
    ) {
        world?.debugAssertInTickingThread()
        val saved = runtime.remove(id)
        member.stored = saved.amount
        member.storeRank = saved.rank
        blockState.markNeedsSaving(accessor)
        entries.remove(id)
    }

    fun contains(id: Long): Boolean = runtime.graph.networkOf(id) >= 0

    fun setProduction(
        id: Long,
        amount: Long,
        rank: Int,
    ) = runtime.setProduction(id, amount, rank)

    fun setDemand(
        id: Long,
        amount: Long,
    ) = runtime.setDemand(id, amount)

    fun stored(id: Long): Long = runtime.graph.stored(id)

    fun capacity(id: Long): Long = runtime.capacity(id)

    fun networkSupply(id: Long): Long = runtime.diagnostics(id)?.supply ?: 0L

    fun networkDemand(id: Long): Long = runtime.diagnostics(id)?.demand ?: 0L

    fun delivered(id: Long): Long = runtime.graph.delivered(id)

    fun deliveredRank(id: Long): Int = runtime.graph.deliveredRank(id)

    fun networkOf(id: Long): Int = runtime.graph.networkOf(id)

    fun diagnostics(id: Long): EnergyNetworkDiagnostics? = runtime.diagnostics(id)

    val networkCount: Int get() = runtime.graph.networkCount
    val awakeNetworkCount: Int get() = runtime.graph.awakeNetworkCount

    fun lastSolveTick(): Long = lastSolveTick

    private fun solve(tick: Long) {
        val currentStore = store ?: return
        lastSolveTick = tick
        val started = System.nanoTime()
        runtime.tick(events)
        lastSolveNanos = System.nanoTime() - started
        entries.forEach { (id, entry) ->
            val amount = runtime.graph.stored(id)
            val rank = runtime.graph.storedRank(id)
            entry.member.stored = amount
            entry.member.storeRank = rank
            val threshold = maxOf(1L, entry.capacity / 100)
            if (
                tick - entry.savedTick >= SAVE_INTERVAL_TICKS ||
                kotlin.math.abs(amount - entry.savedAmount) >= threshold ||
                rank != entry.savedRank
            ) {
                entry.blockState.markNeedsSaving(currentStore)
                entry.savedAmount = amount
                entry.savedRank = rank
                entry.savedTick = tick
            }
        }
        events.drain()
    }

    companion object {
        const val SOLVE_INTERVAL_TICKS = 4
        private const val SAVE_INTERVAL_TICKS = 100L

        lateinit var type: ResourceType<ChunkStore, EnergyWorld>
    }
}
