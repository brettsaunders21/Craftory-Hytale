package studio.craftory.core.energy

import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.engine.energy.EnergyEventSink
import studio.craftory.engine.energy.EnergyGraph

internal data class StoredEnergy(
    val amount: Long,
    val rank: Int,
)

internal data class EnergyNetworkDiagnostics(
    val id: Int,
    val members: Int,
    val grade: Int,
    val supply: Long,
    val demand: Long,
    val awake: Boolean,
)

private data class NetworkMetrics(
    var members: Int = 0,
    var grade: Int = 0,
    var supply: Long = 0,
    var demand: Long = 0,
)

internal class EnergyRuntimeAdapter {
    private data class Node(
        val spec: MemberSpec,
        var production: Long = 0,
        var productionRank: Int = 0,
        var demand: Long = 0,
        var stored: Long = 0,
        var storeRank: Int = -1,
        var lastSolveNetworkId: Int = -1,
        var lastSolveProduction: Long = 0,
        var lastSolveProductionRank: Int = 0,
        var lastSolveDemand: Long = 0,
        var lastSolveStored: Long = 0,
        var lastSolveStoreRank: Int = -1,
        var lastSolveTopologyGeneration: Int = -1,
    )

    val graph = EnergyGraph()
    private val nodes = HashMap<Long, Node>()
    private var topologyGeneration = 0

    fun add(
        id: Long,
        spec: MemberSpec,
        neighbours: LongArray,
        stored: Long = 0,
        storeRank: Int = -1,
    ) {
        graph.add(id, spec, neighbours)
        topologyGeneration++
        val restored = if ((spec.role and MemberSpec.STORAGE) != 0) stored.coerceIn(0L, spec.capacity) else 0L
        val rank = if (restored == 0L) -1 else storeRank.coerceAtLeast(0)
        if ((spec.role and MemberSpec.STORAGE) != 0) graph.restoreStored(id, restored, rank)
        nodes[id] = Node(spec, stored = restored, storeRank = rank)
    }

    fun remove(id: Long): StoredEnergy {
        val state = nodes.remove(id) ?: return StoredEnergy(0, -1)
        val saved = StoredEnergy(graph.stored(id), graph.storedRank(id))
        graph.remove(id)
        topologyGeneration++
        state.stored = saved.amount
        state.storeRank = saved.rank
        return saved
    }

    fun setProduction(
        id: Long,
        amount: Long,
        rank: Int,
    ) {
        graph.setProduction(id, amount, rank)
        nodes.getValue(id).also {
            it.production = amount
            it.productionRank = rank
        }
    }

    fun setDemand(
        id: Long,
        amount: Long,
    ) {
        graph.setDemand(id, amount)
        nodes.getValue(id).demand = amount
    }

    fun capacity(id: Long): Long = nodes[id]?.spec?.capacity ?: 0L

    fun tick(sink: EnergyEventSink) {
        nodes.forEach { (id, node) ->
            node.lastSolveNetworkId = graph.networkOf(id)
            node.lastSolveProduction = node.production
            node.lastSolveProductionRank = node.productionRank
            node.lastSolveDemand = minOf(node.demand, node.spec.maxInput)
            node.lastSolveStored = graph.stored(id)
            node.lastSolveStoreRank = graph.storedRank(id)
            node.lastSolveTopologyGeneration = topologyGeneration
        }
        graph.tick(sink)
        nodes.forEach { (id, node) ->
            node.stored = graph.stored(id)
            node.storeRank = graph.storedRank(id)
        }
    }

    fun diagnostics(memberId: Long): EnergyNetworkDiagnostics? {
        val networkId = graph.networkOf(memberId)
        if (networkId < 0) return null
        val member = nodes[memberId] ?: return null
        val useLastSolve =
            member.lastSolveTopologyGeneration == topologyGeneration &&
                member.lastSolveNetworkId == networkId
        val metrics = NetworkMetrics()
        nodes.forEach { (id, node) ->
            if (isInNetwork(id, node, networkId, useLastSolve)) accumulate(node, useLastSolve, metrics)
        }
        return EnergyNetworkDiagnostics(
            networkId,
            metrics.members,
            metrics.grade,
            metrics.supply,
            metrics.demand,
            graph.isAwake(memberId),
        )
    }

    private fun isInNetwork(
        id: Long,
        node: Node,
        networkId: Int,
        useLastSolve: Boolean,
    ): Boolean =
        if (useLastSolve) {
            node.lastSolveTopologyGeneration == topologyGeneration &&
                node.lastSolveNetworkId == networkId
        } else {
            graph.networkOf(id) == networkId
        }

    private fun accumulate(
        node: Node,
        useLastSolve: Boolean,
        metrics: NetworkMetrics,
    ) {
        val production = if (useLastSolve) node.lastSolveProduction else node.production
        val productionRank = if (useLastSolve) node.lastSolveProductionRank else node.productionRank
        val stored = if (useLastSolve) node.lastSolveStored else node.stored
        val storeRank = if (useLastSolve) node.lastSolveStoreRank else node.storeRank
        metrics.members++
        if (production > 0) {
            metrics.grade = maxOf(metrics.grade, productionRank)
            metrics.supply += production
        }
        if (stored > 0) {
            metrics.grade = maxOf(metrics.grade, storeRank)
            metrics.supply += minOf(stored, node.spec.maxOutput)
        }
        metrics.demand +=
            if (useLastSolve) {
                node.lastSolveDemand
            } else {
                minOf(node.demand, node.spec.maxInput)
            }
    }
}
