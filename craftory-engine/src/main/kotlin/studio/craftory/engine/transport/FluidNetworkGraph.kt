@file:Suppress("CyclomaticComplexMethod", "LargeClass", "TooManyFunctions")

package studio.craftory.engine.transport

import studio.craftory.engine.energy.LongIntMap
import java.util.Arrays

enum class FluidNodeKind(
    val capacity: Long,
) {
    PIPE(250),
    TANK(8_000),
}

enum class FluidNodeRemovalReason {
    BREAK,
    UNLOAD,
}

data class FluidNodeRecord(
    val fluidKey: Int = NO_FLUID,
    val amount: Long = 0,
) {
    init {
        require(fluidKey >= NO_FLUID)
        require(amount >= 0)
        require(amount == 0L || fluidKey >= 0)
    }

    companion object {
        const val NO_FLUID = -1
    }
}

object FluidNodeCodec {
    private const val VERSION = "1"

    fun encode(record: FluidNodeRecord): String = "$VERSION;${record.fluidKey},${record.amount}"

    fun decode(encoded: String): FluidNodeRecord? {
        if (!encoded.startsWith("$VERSION;")) return null
        val fields = encoded.substringAfter(';').split(',')
        if (fields.size != 2) return null
        val key = fields[0].toIntOrNull() ?: return null
        val amount = fields[1].toLongOrNull() ?: return null
        if (key < FluidNodeRecord.NO_FLUID || amount < 0) return null
        return FluidNodeRecord(key, amount)
    }
}

fun interface FluidDirtyNodeVisitor {
    fun visit(nodePos: Long)
}

private const val NO_FLUSH_TICK = Long.MIN_VALUE
private const val DIRTY_INTERVAL_TICKS = 20L

class FluidNetworkGraph {
    private val nodeMap = LongIntMap()
    private var nodePositions = LongArray(INITIAL_CAPACITY)
    private var nodeKinds = ByteArray(INITIAL_CAPACITY)
    private var nodeCapacities = LongArray(INITIAL_CAPACITY)
    private var nodeAmounts = LongArray(INITIAL_CAPACITY)
    private var nodeFluidKeys = IntArray(INITIAL_CAPACITY) { FluidNodeRecord.NO_FLUID }
    private var nodeNetworks = IntArray(INITIAL_CAPACITY) { -1 }
    private var nodeActive = BooleanArray(INITIAL_CAPACITY)
    private var nodeGenerations = IntArray(INITIAL_CAPACITY)
    private var nodeNext = IntArray(INITIAL_CAPACITY) { -1 }
    private var nodePrevious = IntArray(INITIAL_CAPACITY) { -1 }
    private var networkHeads = IntArray(INITIAL_CAPACITY) { -1 }
    private var networkCounts = IntArray(INITIAL_CAPACITY)
    private var networkCapacities = LongArray(INITIAL_CAPACITY)
    private var networkAmounts = LongArray(INITIAL_CAPACITY)
    private var networkFluidKeys = IntArray(INITIAL_CAPACITY) { FluidNodeRecord.NO_FLUID }
    private var networkLastFlushTicks = LongArray(INITIAL_CAPACITY) { NO_FLUSH_TICK }
    private var networkDrainStamps = IntArray(INITIAL_CAPACITY)
    private var networkDrainDue = BooleanArray(INITIAL_CAPACITY)
    private var networkDrainEpoch = 0
    private var nodeSize = 0
    private var freeHandles = IntArray(INITIAL_CAPACITY)
    private var freeHandleCount = 0
    private var activeNodeCount = 0
    private var activeNetworkCount = 0
    private var queue = IntArray(INITIAL_CAPACITY)
    private var visited = IntArray(INITIAL_CAPACITY)
    private var visitStamp = 0
    private var visitOrder = IntArray(INITIAL_CAPACITY)
    private var componentStarts = IntArray(MAX_NEIGHBOURS)
    private var componentSizes = IntArray(MAX_NEIGHBOURS)
    private var componentRoots = IntArray(MAX_NEIGHBOURS)
    private val neighbourScratch = IntArray(MAX_NEIGHBOURS)
    private var dirtyHandles = IntArray(INITIAL_CAPACITY)
    private var dirtyGenerations = IntArray(INITIAL_CAPACITY)
    private var dirtyFlags = BooleanArray(INITIAL_CAPACITY)
    private var dirtyCount = 0

    val nodeCount: Int get() = activeNodeCount
    val networkCount: Int get() = activeNetworkCount

    fun containsNode(pos: Long): Boolean = nodeMap.getOrDefault(pos, -1) >= 0

    fun addNode(
        pos: Long,
        kind: FluidNodeKind,
        persisted: FluidNodeRecord = FluidNodeRecord(),
    ) {
        require(!containsNode(pos)) { "Node $pos already exists" }
        val handle = allocateHandle()
        nodeGenerations[handle]++
        dirtyFlags[handle] = false
        nodePositions[handle] = pos
        nodeKinds[handle] = kind.ordinal.toByte()
        nodeCapacities[handle] = kind.capacity
        nodeAmounts[handle] = persisted.amount.coerceAtMost(kind.capacity)
        nodeFluidKeys[handle] =
            if (nodeAmounts[handle] == 0L) FluidNodeRecord.NO_FLUID else persisted.fluidKey
        nodeNetworks[handle] = handle
        nodeActive[handle] = true
        nodeNext[handle] = -1
        nodePrevious[handle] = -1
        networkHeads[handle] = handle
        networkCounts[handle] = 1
        networkCapacities[handle] = kind.capacity
        networkAmounts[handle] = nodeAmounts[handle]
        networkFluidKeys[handle] = nodeFluidKeys[handle]
        networkLastFlushTicks[handle] = NO_FLUSH_TICK
        nodeMap.put(pos, handle)
        activeNodeCount++
        activeNetworkCount++

        var merged = false
        for (face in FACES.indices) {
            val adjacent = nodeMap.getOrDefault(neighbour(pos, face), -1)
            if (tryMergeAdjacent(handle, adjacent)) merged = true
        }
        if (merged) redistribute(nodeNetworks[handle])
    }

    private fun tryMergeAdjacent(
        handle: Int,
        adjacent: Int,
    ): Boolean {
        if (adjacent < 0 || !canConnect(handle, adjacent)) return false
        val current = nodeNetworks[handle]
        val other = nodeNetworks[adjacent]
        return current != other && mergeNetworks(current, other)
    }

    fun connect(
        firstPos: Long,
        secondPos: Long,
    ): Boolean {
        val first = nodeMap.getOrDefault(firstPos, -1)
        val second = nodeMap.getOrDefault(secondPos, -1)
        if (first < 0) return false
        if (second < 0) return false
        if (!areAdjacent(firstPos, secondPos)) return false
        if (!canConnect(first, second)) return false
        val firstNetwork = nodeNetworks[first]
        val secondNetwork = nodeNetworks[second]
        if (firstNetwork == secondNetwork) return true
        if (!mergeNetworks(firstNetwork, secondNetwork)) return false
        redistribute(nodeNetworks[first])
        return true
    }

    fun removeNode(
        pos: Long,
        reason: FluidNodeRemovalReason,
    ): FluidNodeRecord? {
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return null
        val oldNetwork = nodeNetworks[handle]
        val persisted =
            FluidNodeRecord(
                nodeFluidKeys[handle],
                nodeAmounts[handle],
            )
        val neighbourCount = collectNeighbours(handle)
        val oldFlush = networkLastFlushTicks[oldNetwork]
        unlink(handle)
        nodeMap.remove(pos)
        nodeActive[handle] = false
        nodeNetworks[handle] = -1
        nodeNext[handle] = -1
        nodePrevious[handle] = -1
        activeNodeCount--
        freeHandle(handle)

        clearNetwork(oldNetwork)
        val splitCount = splitNetwork(oldNetwork, neighbourCount, oldFlush)
        activeNetworkCount += splitCount - 1
        redistributeComponents(splitCount)
        return if (reason == FluidNodeRemovalReason.UNLOAD) persisted else null
    }

    private fun clearNetwork(network: Int) {
        networkHeads[network] = -1
        networkCounts[network] = 0
        networkCapacities[network] = 0
        networkAmounts[network] = 0
        networkFluidKeys[network] = FluidNodeRecord.NO_FLUID
        networkLastFlushTicks[network] = NO_FLUSH_TICK
    }

    private fun splitNetwork(
        oldNetwork: Int,
        neighbourCount: Int,
        oldFlush: Long,
    ): Int {
        advanceVisitStamp()
        var visitedCount = 0
        var splitCount = 0
        for (index in 0 until neighbourCount) {
            val start = neighbourScratch[index]
            if (!isUnvisitedComponent(start, oldNetwork)) continue
            val componentStart = visitedCount
            val size = collectComponent(start, oldNetwork, componentStart, splitCount)
            visitedCount += size
            networkLastFlushTicks[componentRoots[splitCount]] = oldFlush
            splitCount++
        }
        return splitCount
    }

    private fun advanceVisitStamp() {
        visitStamp++
        if (visitStamp == 0) {
            Arrays.fill(visited, 0)
            visitStamp = 1
        }
    }

    private fun isUnvisitedComponent(
        node: Int,
        network: Int,
    ): Boolean = nodeActive[node] && nodeNetworks[node] == network && visited[node] != visitStamp

    private fun collectComponent(
        start: Int,
        oldNetwork: Int,
        componentStart: Int,
        component: Int,
    ): Int {
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = visitStamp
        var totalCapacity = 0L
        var totalAmount = 0L
        var fluidKey = FluidNodeRecord.NO_FLUID
        var containsOldRoot = false
        while (head < tail) {
            val current = queue[head++]
            visitOrder[componentStart + head - 1] = current
            if (current == oldNetwork) containsOldRoot = true
            totalCapacity += nodeCapacities[current]
            totalAmount += nodeAmounts[current]
            if (fluidKey == FluidNodeRecord.NO_FLUID) fluidKey = nodeFluidKeys[current]
            val currentPos = nodePositions[current]
            for (face in FACES.indices) {
                val adjacent = nodeMap.getOrDefault(neighbour(currentPos, face), -1)
                if (shouldVisit(current, adjacent, oldNetwork)) {
                    visited[adjacent] = visitStamp
                    queue[tail++] = adjacent
                }
            }
        }
        val root = if (containsOldRoot) oldNetwork else start
        componentStarts[component] = componentStart
        componentSizes[component] = tail
        componentRoots[component] = root
        networkCounts[root] = tail
        networkHeads[root] = visitOrder[componentStart]
        networkCapacities[root] = totalCapacity
        networkAmounts[root] = totalAmount
        networkFluidKeys[root] = if (totalAmount == 0L) FluidNodeRecord.NO_FLUID else fluidKey
        return tail
    }

    private fun shouldVisit(
        current: Int,
        adjacent: Int,
        network: Int,
    ): Boolean {
        if (adjacent < 0) return false
        if (nodeNetworks[adjacent] != network) return false
        if (visited[adjacent] == visitStamp) return false
        return canConnect(current, adjacent)
    }

    private fun redistributeComponents(splitCount: Int) {
        for (component in 0 until splitCount) {
            val start = componentStarts[component]
            val size = componentSizes[component]
            val root = componentRoots[component]
            var previous = -1
            for (offset in start until start + size) {
                val current = visitOrder[offset]
                nodeNetworks[current] = root
                nodePrevious[current] = previous
                if (previous >= 0) nodeNext[previous] = current
                nodeNext[current] = -1
                previous = current
            }
            redistribute(root)
        }
    }

    fun insert(
        pos: Long,
        fluidKey: Int,
        amount: Long,
    ): Long {
        if (fluidKey < 0 || amount <= 0) return 0
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return 0
        val network = nodeNetworks[handle]
        val currentKey = networkFluidKeys[network]
        if (currentKey >= 0 && currentKey != fluidKey) return 0
        val accepted = amount.coerceAtMost(networkCapacities[network] - networkAmounts[network])
        if (accepted <= 0) return 0
        networkFluidKeys[network] = fluidKey
        networkAmounts[network] += accepted
        redistribute(network)
        return accepted
    }

    fun drain(
        pos: Long,
        amount: Long,
    ): Long {
        if (amount <= 0) return 0
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return 0
        val network = nodeNetworks[handle]
        val drained = amount.coerceAtMost(networkAmounts[network])
        if (drained == 0L) return 0
        networkAmounts[network] -= drained
        if (networkAmounts[network] == 0L) networkFluidKeys[network] = FluidNodeRecord.NO_FLUID
        redistribute(network)
        if (networkAmounts[network] == 0L) retryConnections(network)
        return drained
    }

    fun amountAt(pos: Long): Long {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) 0 else networkAmounts[nodeNetworks[handle]]
    }

    fun nodeAmountAt(pos: Long): Long {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) 0 else nodeAmounts[handle]
    }

    fun nodeRecordAt(pos: Long): FluidNodeRecord {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) FluidNodeRecord() else FluidNodeRecord(nodeFluidKeys[handle], nodeAmounts[handle])
    }

    fun kindAt(pos: Long): FluidNodeKind? {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) null else FluidNodeKind.values()[nodeKinds[handle].toInt()]
    }

    fun connectedNeighbourCount(pos: Long): Int {
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return 0
        var count = 0
        for (face in FACES.indices) {
            val adjacent = nodeMap.getOrDefault(neighbour(pos, face), -1)
            if (adjacent >= 0 && canConnect(handle, adjacent)) count++
        }
        return count
    }

    fun fluidKeyAt(pos: Long): Int {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) FluidNodeRecord.NO_FLUID else networkFluidKeys[nodeNetworks[handle]]
    }

    fun capacityAt(pos: Long): Long {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) 0 else networkCapacities[nodeNetworks[handle]]
    }

    fun networkOf(pos: Long): Int {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) -1 else nodeNetworks[handle]
    }

    fun drainDirty(visitor: FluidDirtyNodeVisitor) {
        drainDirty(null, null, 0L, visitor)
    }

    fun drainDirty(
        tick: Long,
        visitor: FluidDirtyNodeVisitor,
    ) {
        drainDirty(null, null, tick, visitor)
    }

    fun drainDirtyInChunk(
        tick: Long,
        chunkX: Int,
        chunkZ: Int,
        visitor: FluidDirtyNodeVisitor,
    ) {
        drainDirty(chunkX, chunkZ, tick, visitor)
    }

    private fun drainDirty(
        chunkX: Int?,
        chunkZ: Int?,
        tick: Long,
        visitor: FluidDirtyNodeVisitor,
    ) {
        if (networkDrainEpoch == Int.MAX_VALUE) {
            Arrays.fill(networkDrainStamps, 0)
            networkDrainEpoch = 1
        } else {
            networkDrainEpoch++
        }
        val epoch = networkDrainEpoch
        var retained = 0
        for (index in 0 until dirtyCount) {
            val handle = dirtyHandles[index]
            if (!nodeActive[handle] || dirtyGenerations[index] != nodeGenerations[handle]) continue
            val pos = nodePositions[handle]
            val inChunk =
                chunkX == null ||
                    (PackedPosition.x(pos) shr 5 == chunkX && PackedPosition.z(pos) shr 5 == chunkZ)
            val network = nodeNetworks[handle]
            var due = inChunk && chunkX != null
            if (inChunk && chunkX == null) {
                due =
                    if (networkDrainStamps[network] == epoch) {
                        networkDrainDue[network]
                    } else {
                        val last = networkLastFlushTicks[network]
                        val flush = last == NO_FLUSH_TICK || tick - last >= DIRTY_INTERVAL_TICKS
                        networkDrainStamps[network] = epoch
                        networkDrainDue[network] = flush
                        if (flush) networkLastFlushTicks[network] = tick
                        flush
                    }
            } else if (due) {
                networkLastFlushTicks[network] = tick
            }
            if (due) {
                dirtyFlags[handle] = false
                visitor.visit(pos)
            } else {
                dirtyHandles[retained] = handle
                dirtyGenerations[retained] = dirtyGenerations[index]
                retained++
            }
        }
        dirtyCount = retained
    }

    private fun retryConnections(initialNetwork: Int) {
        var handle = networkHeads[initialNetwork]
        while (handle >= 0) {
            val pos = nodePositions[handle]
            for (face in FACES.indices) {
                val adjacent = nodeMap.getOrDefault(neighbour(pos, face), -1)
                if (adjacent < 0) continue
                val firstNetwork = nodeNetworks[handle]
                val secondNetwork = nodeNetworks[adjacent]
                if (firstNetwork != secondNetwork && canMerge(firstNetwork, secondNetwork)) {
                    mergeNetworks(firstNetwork, secondNetwork)
                    redistribute(nodeNetworks[handle])
                }
            }
            handle = nodeNext[handle]
        }
    }

    private fun mergeNetworks(
        first: Int,
        second: Int,
    ): Boolean {
        if (first == second) return true
        if (!canMerge(first, second)) return false
        val large: Int
        val small: Int
        if (networkCounts[first] >= networkCounts[second]) {
            large = first
            small = second
        } else {
            large = second
            small = first
        }
        val oldFlush = maxFlush(networkLastFlushTicks[large], networkLastFlushTicks[small])
        val smallHead = networkHeads[small]
        var tail = smallHead
        while (nodeNext[tail] >= 0) tail = nodeNext[tail]
        nodeNext[tail] = networkHeads[large]
        if (networkHeads[large] >= 0) nodePrevious[networkHeads[large]] = tail
        networkHeads[large] = smallHead
        var node = smallHead
        while (node >= 0) {
            nodeNetworks[node] = large
            node = nodeNext[node]
        }
        networkCounts[large] += networkCounts[small]
        networkCapacities[large] += networkCapacities[small]
        networkAmounts[large] += networkAmounts[small]
        if (networkFluidKeys[large] == FluidNodeRecord.NO_FLUID) {
            networkFluidKeys[large] = networkFluidKeys[small]
        }
        networkCounts[small] = 0
        networkCapacities[small] = 0
        networkAmounts[small] = 0
        networkFluidKeys[small] = FluidNodeRecord.NO_FLUID
        networkHeads[small] = -1
        networkLastFlushTicks[large] = oldFlush
        networkLastFlushTicks[small] = NO_FLUSH_TICK
        activeNetworkCount--
        return true
    }

    private fun canMerge(
        first: Int,
        second: Int,
    ): Boolean {
        val firstKey = networkFluidKeys[first]
        val secondKey = networkFluidKeys[second]
        return firstKey == FluidNodeRecord.NO_FLUID ||
            secondKey == FluidNodeRecord.NO_FLUID ||
            firstKey == secondKey
    }

    private fun canConnect(
        first: Int,
        second: Int,
    ): Boolean {
        val firstKind = FluidNodeKind.values()[nodeKinds[first].toInt()]
        val secondKind = FluidNodeKind.values()[nodeKinds[second].toInt()]
        if (firstKind == FluidNodeKind.TANK && secondKind == FluidNodeKind.TANK) {
            return PackedPosition.x(nodePositions[first]) == PackedPosition.x(nodePositions[second]) &&
                PackedPosition.z(nodePositions[first]) == PackedPosition.z(nodePositions[second]) &&
                kotlin.math.abs(PackedPosition.y(nodePositions[first]) - PackedPosition.y(nodePositions[second])) == 1
        }
        return true
    }

    private fun redistribute(network: Int) {
        val amount = networkAmounts[network]
        val capacity = networkCapacities[network]
        val key = if (amount == 0L) FluidNodeRecord.NO_FLUID else networkFluidKeys[network]
        var assigned = 0L
        var handle = networkHeads[network]
        while (handle >= 0) {
            val share = if (capacity == 0L) 0L else amount * nodeCapacities[handle] / capacity
            assigned += share
            updateNode(handle, key, share)
            handle = nodeNext[handle]
        }
        var remainder = amount - assigned
        handle = networkHeads[network]
        while (remainder > 0 && handle >= 0) {
            updateNode(handle, key, nodeAmounts[handle] + 1)
            remainder--
            handle = nodeNext[handle]
        }
    }

    private fun updateNode(
        handle: Int,
        key: Int,
        amount: Long,
    ) {
        if (nodeFluidKeys[handle] == key && nodeAmounts[handle] == amount) return
        nodeFluidKeys[handle] = key
        nodeAmounts[handle] = amount
        markDirty(handle)
    }

    private fun splitRoot(
        oldRoot: Int,
        start: Int,
        size: Int,
    ): Int = if (oldRoot in start until start + size) oldRoot else visitOrder[start]

    private fun collectNeighbours(handle: Int): Int {
        var count = 0
        val pos = nodePositions[handle]
        for (face in FACES.indices) {
            val adjacent = nodeMap.getOrDefault(neighbour(pos, face), -1)
            if (adjacent >= 0 && nodeNetworks[adjacent] == nodeNetworks[handle]) neighbourScratch[count++] = adjacent
        }
        return count
    }

    private fun unlink(handle: Int) {
        val previous = nodePrevious[handle]
        val next = nodeNext[handle]
        if (previous >= 0) nodeNext[previous] = next else networkHeads[nodeNetworks[handle]] = next
        if (next >= 0) nodePrevious[next] = previous
    }

    private fun markDirty(handle: Int) {
        if (dirtyFlags[handle]) return
        if (dirtyCount == dirtyHandles.size) {
            dirtyHandles = dirtyHandles.copyOf(dirtyHandles.size shl 1)
            dirtyGenerations = dirtyGenerations.copyOf(dirtyGenerations.size shl 1)
        }
        dirtyFlags[handle] = true
        dirtyHandles[dirtyCount] = handle
        dirtyGenerations[dirtyCount++] = nodeGenerations[handle]
    }

    private fun allocateHandle(): Int {
        if (freeHandleCount > 0) return freeHandles[--freeHandleCount]
        ensureCapacity(nodeSize + 1)
        return nodeSize++
    }

    private fun freeHandle(handle: Int) {
        if (freeHandleCount == freeHandles.size) freeHandles = freeHandles.copyOf(freeHandles.size shl 1)
        freeHandles[freeHandleCount++] = handle
    }

    private fun ensureCapacity(required: Int) {
        if (required <= nodePositions.size) return
        var size = nodePositions.size shl 1
        while (size < required) size = size shl 1
        nodePositions = nodePositions.copyOf(size)
        nodeKinds = nodeKinds.copyOf(size)
        nodeCapacities = nodeCapacities.copyOf(size)
        nodeAmounts = nodeAmounts.copyOf(size)
        nodeFluidKeys = nodeFluidKeys.copyOf(size).also { Arrays.fill(it, nodeFluidKeys.size, size, -1) }
        nodeNetworks = nodeNetworks.copyOf(size).also { Arrays.fill(it, nodeNetworks.size, size, -1) }
        nodeActive = nodeActive.copyOf(size)
        nodeGenerations = nodeGenerations.copyOf(size)
        nodeNext = nodeNext.copyOf(size).also { Arrays.fill(it, nodeNext.size, size, -1) }
        nodePrevious = nodePrevious.copyOf(size).also { Arrays.fill(it, nodePrevious.size, size, -1) }
        networkHeads = networkHeads.copyOf(size).also { Arrays.fill(it, networkHeads.size, size, -1) }
        networkCounts = networkCounts.copyOf(size)
        networkCapacities = networkCapacities.copyOf(size)
        networkAmounts = networkAmounts.copyOf(size)
        networkFluidKeys = networkFluidKeys.copyOf(size).also { Arrays.fill(it, networkFluidKeys.size, size, -1) }
        networkLastFlushTicks =
            networkLastFlushTicks.copyOf(size).also { Arrays.fill(it, networkLastFlushTicks.size, size, NO_FLUSH_TICK) }
        networkDrainStamps = networkDrainStamps.copyOf(size)
        networkDrainDue = networkDrainDue.copyOf(size)
        queue = queue.copyOf(size)
        visited = visited.copyOf(size)
        visitOrder = visitOrder.copyOf(size)
        freeHandles = freeHandles.copyOf(size)
        dirtyHandles = dirtyHandles.copyOf(size)
        dirtyGenerations = dirtyGenerations.copyOf(size)
        dirtyFlags = dirtyFlags.copyOf(size)
    }

    private fun neighbour(
        pos: Long,
        face: Int,
    ): Long =
        PackedPosition.pack(
            PackedPosition.x(pos) + FACES[face][0],
            PackedPosition.y(pos) + FACES[face][1],
            PackedPosition.z(pos) + FACES[face][2],
        )

    private fun areAdjacent(
        first: Long,
        second: Long,
    ): Boolean =
        kotlin.math.abs(PackedPosition.x(first) - PackedPosition.x(second)) +
            kotlin.math.abs(PackedPosition.y(first) - PackedPosition.y(second)) +
            kotlin.math.abs(PackedPosition.z(first) - PackedPosition.z(second)) == 1

    private fun maxFlush(
        first: Long,
        second: Long,
    ): Long =
        when {
            first == NO_FLUSH_TICK -> second
            second == NO_FLUSH_TICK -> first
            else -> maxOf(first, second)
        }

    companion object {
        private const val INITIAL_CAPACITY = 16
        private const val MAX_NEIGHBOURS = 6
        private val FACES =
            arrayOf(
                intArrayOf(1, 0, 0),
                intArrayOf(-1, 0, 0),
                intArrayOf(0, 1, 0),
                intArrayOf(0, -1, 0),
                intArrayOf(0, 0, 1),
                intArrayOf(0, 0, -1),
            )
    }
}
