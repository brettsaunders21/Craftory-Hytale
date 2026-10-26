@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")

package studio.craftory.engine.transport

import studio.craftory.engine.energy.LongIntMap
import java.util.Arrays
import java.util.SplittableRandom

enum class NodeRemovalReason {
    BREAK,
    UNLOAD,
}

fun interface NodeActivity {
    fun isActive(nodePos: Long): Boolean
}

interface TransitSink {
    fun tryDeliver(
        nodePos: Long,
        itemKey: Int,
        quantity: Int,
        originPos: Long,
    ): Int

    fun dropped(
        itemKey: Int,
        quantity: Int,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
    )
}

fun interface DirtyNodeVisitor {
    fun visit(nodePos: Long)
}

@Suppress("LargeClass", "TooManyFunctions")
class ItemTransitGraph(
    private val speedPerTick: Int = DEFAULT_SPEED_PER_TICK,
) {
    private val nodeMap = LongIntMap()
    private var nodePositions = LongArray(INITIAL_CAPACITY)
    private var nodeColours = ByteArray(INITIAL_CAPACITY)
    private var nodeCursors = IntArray(INITIAL_CAPACITY)
    private var nodeNetworks = IntArray(INITIAL_CAPACITY) { -1 }
    private var nodeActive = BooleanArray(INITIAL_CAPACITY)
    private var nodeSize = 0
    private var activeNodeCount = 0
    private var freeHandles = IntArray(INITIAL_CAPACITY)
    private var freeHandleCount = 0
    private var nodeGenerations = IntArray(INITIAL_CAPACITY)
    private var networkNext = IntArray(INITIAL_CAPACITY) { -1 }
    private var networkPrevious = IntArray(INITIAL_CAPACITY) { -1 }
    private var networkHeads = IntArray(INITIAL_CAPACITY) { -1 }
    private var networkNodeCounts = IntArray(INITIAL_CAPACITY)
    private var nodeItemCounts = IntArray(INITIAL_CAPACITY)
    private var componentCount = 0
    private var networkItemCounts = IntArray(INITIAL_CAPACITY)
    private var awakeNetworkSize = 0
    private var queue = IntArray(INITIAL_CAPACITY)
    private var visitOrder = IntArray(INITIAL_CAPACITY)
    private var visited = IntArray(INITIAL_CAPACITY)
    private var visitStamp = 0
    private val componentStarts = IntArray(MAX_NEIGHBOURS)
    private val componentSizes = IntArray(MAX_NEIGHBOURS)
    private val componentRoots = IntArray(MAX_NEIGHBOURS)
    private var dirtyHandles = IntArray(INITIAL_CAPACITY)
    private var dirtyFlags = BooleanArray(INITIAL_CAPACITY)
    private var dirtyGenerations = IntArray(INITIAL_CAPACITY)
    private var dirtyCount = 0

    private var itemIds = LongArray(INITIAL_CAPACITY)
    private var itemKeys = IntArray(INITIAL_CAPACITY)
    private var quantities = IntArray(INITIAL_CAPACITY)
    private var itemColours = ByteArray(INITIAL_CAPACITY)
    private var fromPositions = LongArray(INITIAL_CAPACITY)
    private var toPositions = LongArray(INITIAL_CAPACITY)
    private var progresses = IntArray(INITIAL_CAPACITY)
    private var originPositions = LongArray(INITIAL_CAPACITY)
    private var waitTicks = IntArray(INITIAL_CAPACITY)
    private var itemSize = 0
    private val itemIdRandom =
        SplittableRandom(System.nanoTime() xor System.identityHashCode(this).toLong())

    init {
        require(speedPerTick in 1..SEGMENT_LENGTH)
    }

    val nodeCount: Int
        get() = activeNodeCount

    internal val nodeHandleCapacity: Int
        get() = nodePositions.size

    val itemCount: Int
        get() = itemSize

    val networkCount: Int
        get() = componentCount

    val awakeNetworkCount: Int
        get() = awakeNetworkSize

    fun containsNode(pos: Long): Boolean = nodeMap.getOrDefault(pos, -1) >= 0

    fun networkOf(pos: Long): Int {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) -1 else nodeNetworks[handle]
    }

    fun colourOf(pos: Long): Int {
        val handle = nodeMap.getOrDefault(pos, -1)
        return if (handle < 0) 0 else nodeColours[handle].toInt()
    }

    fun setColour(
        pos: Long,
        colour: Int,
    ) {
        require(colour in 0..4)
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle >= 0) nodeColours[handle] = colour.toByte()
    }

    fun addNode(
        pos: Long,
        colour: Int = 0,
        persistedItems: List<TransitItemRecord> = emptyList(),
    ) {
        require(colour in 0..4)
        require(!containsNode(pos)) { "Node $pos already exists" }
        val handle = allocateNodeHandle()
        nodeGenerations[handle]++
        nodePositions[handle] = pos
        nodeColours[handle] = colour.toByte()
        nodeCursors[handle] = 0
        nodeActive[handle] = true
        activeNodeCount++
        nodeMap.put(pos, handle)
        nodeNetworks[handle] = handle
        networkNext[handle] = -1
        networkPrevious[handle] = -1
        networkHeads[handle] = handle
        networkNodeCounts[handle] = 1
        nodeItemCounts[handle] = 0
        networkItemCounts[handle] = 0
        componentCount++
        for (face in FACES.indices) {
            val adjacent = nodeMap.getOrDefault(neighbour(pos, face), -1)
            if (adjacent >= 0 && adjacent != handle) mergeNetworks(handle, adjacent)
        }
        var reassignedPersistedId = false
        persistedItems.forEach { record ->
            require(record.fromPos == pos)
            require(record.quantity > 0 && record.colour in 0..4)
            val id = if (record.id < 0 || hasItemId(record.id)) newItemId() else record.id
            appendItem(if (id == record.id) record else record.copy(id = id))
            if (id != record.id) reassignedPersistedId = true
        }
        if (reassignedPersistedId) markDirty(handle)
    }

    fun connect(
        firstPos: Long,
        secondPos: Long,
    ) {
        require(containsNode(firstPos) && containsNode(secondPos))
        require(areAdjacent(firstPos, secondPos))
        mergeNetworks(
            nodeMap.getOrDefault(firstPos, -1),
            nodeMap.getOrDefault(secondPos, -1),
        )
    }

    fun removeNode(
        pos: Long,
        reason: NodeRemovalReason,
        sink: TransitSink,
    ): List<TransitItemRecord> {
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return emptyList()
        val formerNeighbourCount = collectNeighbours(handle)
        val persisted = if (reason == NodeRemovalReason.UNLOAD) ArrayList<TransitItemRecord>() else emptyList()
        var itemIndex = 0
        while (itemIndex < itemSize) {
            val touchesNode = fromPositions[itemIndex] == pos || toPositions[itemIndex] == pos
            if (!touchesNode) {
                itemIndex++
                continue
            }
            if (reason == NodeRemovalReason.UNLOAD && fromPositions[itemIndex] == pos) {
                (persisted as ArrayList<TransitItemRecord>).add(recordAt(itemIndex))
                removeItemAt(itemIndex)
            } else if (reason == NodeRemovalReason.BREAK) {
                val (x, y, z) = interpolatedPosition(itemIndex)
                sink.dropped(itemKeys[itemIndex], quantities[itemIndex], x, y, z)
                removeItemAt(itemIndex)
            } else {
                itemIndex++
            }
        }
        val oldNetwork = nodeNetworks[handle]
        setNetworkItemCount(oldNetwork, networkItemCounts[oldNetwork] - nodeItemCounts[handle])
        networkNodeCounts[oldNetwork]--
        nodeMap.remove(pos)
        nodeActive[handle] = false
        nodeNetworks[handle] = -1
        activeNodeCount--
        unlinkNetworkNode(handle, oldNetwork)
        nodeItemCounts[handle] = 0
        networkNodeCounts[handle] = 0
        setNetworkItemCount(handle, 0)
        networkHeads[handle] = -1
        dirtyFlags[handle] = false
        nodeGenerations[handle]++
        splitNetworkAfterRemoval(oldNetwork, formerNeighbourCount)
        freeHandle(handle)
        return persisted
    }

    fun canInsert(nodePos: Long): Boolean {
        if (nodeMap.getOrDefault(nodePos, -1) < 0) return false
        for (index in 0 until itemSize) {
            if (fromPositions[index] == nodePos && progresses[index] < MIN_SPACING) return false
        }
        return true
    }

    fun insert(
        nodePos: Long,
        itemKey: Int,
        quantity: Int,
        colour: Int,
        originPos: Long,
        sink: TransitSink,
    ): Long {
        val handle = nodeMap.getOrDefault(nodePos, -1)
        if (handle < 0 || itemKey < 0 || quantity <= 0 || colour !in 0..4 || !canInsert(nodePos)) {
            return REJECTED_ITEM_ID
        }
        ensureItemCapacity(itemSize + 1)
        val id = newItemId()
        val itemIndex = itemSize++
        itemIds[itemIndex] = id
        itemKeys[itemIndex] = itemKey
        quantities[itemIndex] = quantity
        itemColours[itemIndex] = colour.toByte()
        fromPositions[itemIndex] = nodePos
        toPositions[itemIndex] = nodePos
        progresses[itemIndex] = SEGMENT_LENGTH
        originPositions[itemIndex] = originPos
        waitTicks[itemIndex] = 0
        incrementOwnerCount(handle)
        markDirty(handle)
        resolveArrival(itemIndex, handle, NO_PREVIOUS_NODE, sink)
        return id
    }

    fun tick(
        activity: NodeActivity,
        sink: TransitSink,
    ) {
        if (itemSize == 0 || awakeNetworkSize == 0) return
        var itemIndex = 0
        while (itemIndex < itemSize) {
            val ownerHandle = nodeMap.getOrDefault(fromPositions[itemIndex], -1)
            if (ownerHandle < 0) {
                itemIndex++
                continue
            }
            val fromPos = fromPositions[itemIndex]
            if (!activity.isActive(fromPos)) {
                itemIndex++
                continue
            }
            if (toPositions[itemIndex] == fromPos) {
                waitTicks[itemIndex]++
                if (waitTicks[itemIndex] < ISOLATED_RETRY_TICKS) {
                    itemIndex++
                    continue
                }
                waitTicks[itemIndex] = 0
                val removed = resolveArrival(itemIndex, ownerHandle, NO_PREVIOUS_NODE, sink)
                if (!removed) itemIndex++
                continue
            }
            val destinationHandle = nodeMap.getOrDefault(toPositions[itemIndex], -1)
            if (destinationHandle < 0) {
                if (progresses[itemIndex] != SEGMENT_LENGTH) {
                    progresses[itemIndex] = SEGMENT_LENGTH
                    markDirty(ownerHandle)
                }
                itemIndex++
                continue
            }
            progresses[itemIndex] += speedPerTick
            if (progresses[itemIndex] >= SEGMENT_LENGTH) {
                progresses[itemIndex] -= SEGMENT_LENGTH
                waitTicks[itemIndex] = 0
                val removed = resolveArrival(itemIndex, destinationHandle, fromPos, sink)
                if (!removed) itemIndex++
                continue
            }
            markDirty(ownerHandle)
            itemIndex++
        }
    }

    fun drainDirty(visitor: DirtyNodeVisitor) {
        for (index in 0 until dirtyCount) {
            val handle = dirtyHandles[index]
            if (!nodeActive[handle] || dirtyGenerations[index] != nodeGenerations[handle]) continue
            dirtyFlags[handle] = false
            visitor.visit(nodePositions[handle])
        }
        dirtyCount = 0
    }

    fun itemsOwnedBy(pos: Long): List<TransitItemRecord> {
        val result = ArrayList<TransitItemRecord>()
        for (index in 0 until itemSize) {
            if (fromPositions[index] == pos) result += recordAt(index)
        }
        return result
    }

    fun itemsOwnedByCount(pos: Long): Int {
        var count = 0
        for (index in 0 until itemSize) {
            if (fromPositions[index] == pos) count++
        }
        return count
    }

    fun progressOf(itemId: Long): Int? {
        for (index in 0 until itemSize) {
            if (itemIds[index] == itemId) return progresses[index]
        }
        return null
    }

    fun itemRecord(index: Int): TransitItemRecord {
        require(index in 0 until itemSize)
        return recordAt(index)
    }

    fun itemId(index: Int): Long {
        require(index in 0 until itemSize)
        return itemIds[index]
    }

    fun itemKey(index: Int): Int {
        require(index in 0 until itemSize)
        return itemKeys[index]
    }

    fun itemFrom(index: Int): Long {
        require(index in 0 until itemSize)
        return fromPositions[index]
    }

    fun itemTo(index: Int): Long {
        require(index in 0 until itemSize)
        return toPositions[index]
    }

    fun itemProgress(index: Int): Int {
        require(index in 0 until itemSize)
        return progresses[index]
    }

    fun itemColour(index: Int): Int {
        require(index in 0 until itemSize)
        return itemColours[index].toInt()
    }

    fun itemWorldX(index: Int): Double {
        require(index in 0 until itemSize)
        val from = fromPositions[index]
        val to = toPositions[index]
        val fraction = progresses[index].toDouble() / SEGMENT_LENGTH
        return PackedPosition.x(from) + 0.5 + (PackedPosition.x(to) - PackedPosition.x(from)) * fraction
    }

    fun itemWorldY(index: Int): Double {
        require(index in 0 until itemSize)
        val from = fromPositions[index]
        val to = toPositions[index]
        val fraction = progresses[index].toDouble() / SEGMENT_LENGTH
        return PackedPosition.y(from) + 0.5 + (PackedPosition.y(to) - PackedPosition.y(from)) * fraction
    }

    fun itemWorldZ(index: Int): Double {
        require(index in 0 until itemSize)
        val from = fromPositions[index]
        val to = toPositions[index]
        val fraction = progresses[index].toDouble() / SEGMENT_LENGTH
        return PackedPosition.z(from) + 0.5 + (PackedPosition.z(to) - PackedPosition.z(from)) * fraction
    }

    fun interpolatedPosition(index: Int): Triple<Double, Double, Double> {
        require(index in 0 until itemSize)
        val from = fromPositions[index]
        val to = toPositions[index]
        val fraction = progresses[index].toDouble() / SEGMENT_LENGTH
        return Triple(
            PackedPosition.x(from) + 0.5 + (PackedPosition.x(to) - PackedPosition.x(from)) * fraction,
            PackedPosition.y(from) + 0.5 + (PackedPosition.y(to) - PackedPosition.y(from)) * fraction,
            PackedPosition.z(from) + 0.5 + (PackedPosition.z(to) - PackedPosition.z(from)) * fraction,
        )
    }

    private fun resolveArrival(
        itemIndex: Int,
        nodeHandle: Int,
        previousPos: Long,
        sink: TransitSink,
    ): Boolean {
        val nodePos = nodePositions[nodeHandle]
        val accepted =
            sink
                .tryDeliver(nodePos, itemKeys[itemIndex], quantities[itemIndex], originPositions[itemIndex])
                .coerceIn(0, quantities[itemIndex])
        if (accepted > 0) quantities[itemIndex] -= accepted
        if (quantities[itemIndex] == 0) {
            removeItemAt(itemIndex)
            return true
        }

        val next = chooseExit(nodeHandle, previousPos, itemColours[itemIndex].toInt())
        val oldOwner = nodeMap.getOrDefault(fromPositions[itemIndex], -1)
        if (oldOwner >= 0) {
            markDirty(oldOwner)
            if (oldOwner != nodeHandle) {
                decrementOwnerCount(oldOwner)
                incrementOwnerCount(nodeHandle)
            }
        }
        fromPositions[itemIndex] = nodePos
        markDirty(nodeHandle)
        if (next < 0) {
            if (previousPos != NO_PREVIOUS_NODE && nodeMap.getOrDefault(previousPos, -1) >= 0) {
                toPositions[itemIndex] = previousPos
                progresses[itemIndex] = 0
            } else {
                toPositions[itemIndex] = nodePos
                progresses[itemIndex] = SEGMENT_LENGTH
                waitTicks[itemIndex] = 0
            }
        } else {
            toPositions[itemIndex] = nodePositions[next]
            progresses[itemIndex] = 0
        }
        return false
    }

    /**
     * Daub a branch's first pipe next to the junction to mark that branch. Stamped items prefer
     * matching exits; otherwise items prefer uncoloured exits before falling back to all exits.
     */
    private fun chooseExit(
        nodeHandle: Int,
        previousPos: Long,
        colour: Int,
    ): Int {
        val candidates = candidateScratch
        var candidateCount = 0
        val nodePos = nodePositions[nodeHandle]
        for (faceIndex in FACES.indices) {
            val candidate = neighbour(nodePos, faceIndex)
            if (candidate == previousPos) continue
            val handle = nodeMap.getOrDefault(candidate, -1)
            if (handle >= 0) candidates[candidateCount++] = handle
        }
        if (candidateCount == 0) return -1
        var matchingCount = 0
        if (colour != 0) {
            for (index in 0 until candidateCount) {
                if (nodeColours[candidates[index]].toInt() == colour) matchingCount++
            }
        }
        var uncolouredCount = 0
        if (matchingCount == 0) {
            for (index in 0 until candidateCount) {
                if (nodeColours[candidates[index]].toInt() == 0) uncolouredCount++
            }
        }
        val preferredCount =
            when {
                matchingCount > 0 -> matchingCount
                uncolouredCount > 0 -> uncolouredCount
                else -> candidateCount
            }
        val cursor = nodeCursors[nodeHandle] % preferredCount
        nodeCursors[nodeHandle] = (cursor + 1) % preferredCount
        var selectedIndex = 0
        for (index in 0 until candidateCount) {
            val matchesPreference =
                when {
                    matchingCount > 0 -> nodeColours[candidates[index]].toInt() == colour
                    uncolouredCount > 0 -> nodeColours[candidates[index]].toInt() == 0
                    else -> true
                }
            if (!matchesPreference) continue
            if (selectedIndex == cursor) return candidates[index]
            selectedIndex++
        }
        return candidates[0]
    }

    private fun mergeNetworks(
        firstHandle: Int,
        secondHandle: Int,
    ) {
        val firstNetwork = nodeNetworks[firstHandle]
        val secondNetwork = nodeNetworks[secondHandle]
        if (firstNetwork == secondNetwork) return

        val largeNetwork: Int
        val smallNetwork: Int
        if (networkNodeCounts[firstNetwork] >= networkNodeCounts[secondNetwork]) {
            largeNetwork = firstNetwork
            smallNetwork = secondNetwork
        } else {
            largeNetwork = secondNetwork
            smallNetwork = firstNetwork
        }

        var handle = networkHeads[smallNetwork]
        while (handle >= 0) {
            nodeNetworks[handle] = largeNetwork
            handle = networkNext[handle]
        }

        val smallHead = networkHeads[smallNetwork]
        val largeHead = networkHeads[largeNetwork]
        var tail = smallHead
        while (networkNext[tail] >= 0) tail = networkNext[tail]
        networkNext[tail] = largeHead
        if (largeHead >= 0) networkPrevious[largeHead] = tail
        networkHeads[largeNetwork] = smallHead
        networkNodeCounts[largeNetwork] += networkNodeCounts[smallNetwork]
        networkNodeCounts[smallNetwork] = 0
        networkHeads[smallNetwork] = -1

        val mergedItemCount = networkItemCounts[largeNetwork] + networkItemCounts[smallNetwork]
        setNetworkItemCount(smallNetwork, 0)
        setNetworkItemCount(largeNetwork, mergedItemCount)
        componentCount--
    }

    private fun collectNeighbours(handle: Int): Int {
        var count = 0
        val position = nodePositions[handle]
        for (face in FACES.indices) {
            val neighbour = nodeMap.getOrDefault(neighbour(position, face), -1)
            if (neighbour >= 0) neighbourScratch[count++] = neighbour
        }
        return count
    }

    @Suppress("LongMethod")
    private fun splitNetworkAfterRemoval(
        oldNetwork: Int,
        formerNeighbourCount: Int,
    ) {
        setNetworkItemCount(oldNetwork, 0)
        networkNodeCounts[oldNetwork] = 0
        networkHeads[oldNetwork] = -1
        if (formerNeighbourCount == 0) {
            componentCount--
            return
        }

        visitStamp++
        if (visitStamp == 0) {
            Arrays.fill(visited, 0)
            visitStamp = 1
        }
        var visitedCount = 0
        var splitCount = 0
        for (neighbourIndex in 0 until formerNeighbourCount) {
            val start = neighbourScratch[neighbourIndex]
            if (!nodeActive[start] || nodeNetworks[start] != oldNetwork || visited[start] == visitStamp) continue
            val componentStart = visitedCount
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = visitStamp
            var containsOldRoot = false
            while (head < tail) {
                val current = queue[head++]
                visitOrder[visitedCount++] = current
                if (current == oldNetwork) containsOldRoot = true
                val currentPos = nodePositions[current]
                for (face in FACES.indices) {
                    val adjacent = nodeMap.getOrDefault(neighbour(currentPos, face), -1)
                    if (
                        adjacent >= 0 &&
                        nodeNetworks[adjacent] == oldNetwork &&
                        visited[adjacent] != visitStamp
                    ) {
                        visited[adjacent] = visitStamp
                        queue[tail++] = adjacent
                    }
                }
            }
            componentStarts[splitCount] = componentStart
            componentSizes[splitCount] = tail
            componentRoots[splitCount] = if (containsOldRoot) oldNetwork else start
            splitCount++
        }

        for (component in 0 until splitCount) {
            val start = componentStarts[component]
            val size = componentSizes[component]
            val root = componentRoots[component]
            setNetworkItemCount(root, 0)
            networkNodeCounts[root] = size
            networkHeads[root] = visitOrder[start]
            var itemCount = 0
            var previous = -1
            for (offset in start until start + size) {
                val handle = visitOrder[offset]
                nodeNetworks[handle] = root
                networkPrevious[handle] = previous
                if (previous >= 0) networkNext[previous] = handle
                networkNext[handle] = -1
                if (previous < 0) networkPrevious[handle] = -1
                previous = handle
                itemCount += nodeItemCounts[handle]
            }
            setNetworkItemCount(root, itemCount)
        }
        componentCount += splitCount - 1
    }

    private fun unlinkNetworkNode(
        handle: Int,
        network: Int,
    ) {
        val previous = networkPrevious[handle]
        val next = networkNext[handle]
        if (previous >= 0) networkNext[previous] = next else networkHeads[network] = next
        if (next >= 0) networkPrevious[next] = previous
        networkNext[handle] = -1
        networkPrevious[handle] = -1
    }

    private fun setNetworkItemCount(
        network: Int,
        count: Int,
    ) {
        val wasAwake = networkItemCounts[network] > 0
        val isAwake = count > 0
        if (wasAwake && !isAwake) awakeNetworkSize--
        if (!wasAwake && isAwake) awakeNetworkSize++
        networkItemCounts[network] = count
    }

    private fun incrementOwnerCount(handle: Int) {
        nodeItemCounts[handle]++
        val network = nodeNetworks[handle]
        setNetworkItemCount(network, networkItemCounts[network] + 1)
    }

    private fun decrementOwnerCount(handle: Int) {
        nodeItemCounts[handle]--
        val network = nodeNetworks[handle]
        setNetworkItemCount(network, networkItemCounts[network] - 1)
    }

    private fun allocateNodeHandle(): Int {
        if (freeHandleCount > 0) return freeHandles[--freeHandleCount]
        ensureNodeCapacity(nodeSize + 1)
        return nodeSize++
    }

    private fun freeHandle(handle: Int) {
        if (freeHandleCount == freeHandles.size) freeHandles = freeHandles.copyOf(freeHandles.size shl 1)
        freeHandles[freeHandleCount++] = handle
    }

    private fun appendItem(record: TransitItemRecord) {
        ensureItemCapacity(itemSize + 1)
        val index = itemSize++
        itemIds[index] = record.id
        itemKeys[index] = record.itemKey
        quantities[index] = record.quantity
        itemColours[index] = record.colour.toByte()
        fromPositions[index] = record.fromPos
        toPositions[index] = record.toPos
        progresses[index] = record.progress
        originPositions[index] = record.originPos
        waitTicks[index] = 0
        val ownerHandle = nodeMap.getOrDefault(record.fromPos, -1)
        if (ownerHandle >= 0) incrementOwnerCount(ownerHandle)
    }

    private fun newItemId(): Long {
        var id: Long
        do {
            id = itemIdRandom.nextLong() and Long.MAX_VALUE
        } while (hasItemId(id))
        return id
    }

    private fun hasItemId(id: Long): Boolean {
        for (index in 0 until itemSize) {
            if (itemIds[index] == id) return true
        }
        return false
    }

    private fun removeItemAt(index: Int) {
        val ownerHandle = nodeMap.getOrDefault(fromPositions[index], -1)
        if (ownerHandle >= 0) {
            markDirty(ownerHandle)
            decrementOwnerCount(ownerHandle)
        }
        val last = --itemSize
        if (index != last) {
            itemIds[index] = itemIds[last]
            itemKeys[index] = itemKeys[last]
            quantities[index] = quantities[last]
            itemColours[index] = itemColours[last]
            fromPositions[index] = fromPositions[last]
            toPositions[index] = toPositions[last]
            progresses[index] = progresses[last]
            originPositions[index] = originPositions[last]
            waitTicks[index] = waitTicks[last]
        }
    }

    private fun recordAt(index: Int) =
        TransitItemRecord(
            itemIds[index],
            itemKeys[index],
            quantities[index],
            itemColours[index].toInt(),
            fromPositions[index],
            toPositions[index],
            progresses[index],
            originPositions[index],
        )

    private fun markDirty(handle: Int) {
        if (handle < 0 || !nodeActive[handle] || dirtyFlags[handle]) return
        ensureDirtyCapacity(dirtyCount + 1)
        dirtyFlags[handle] = true
        dirtyHandles[dirtyCount] = handle
        dirtyGenerations[dirtyCount] = nodeGenerations[handle]
        dirtyCount++
    }

    private fun ensureNodeCapacity(required: Int) {
        if (required <= nodePositions.size) return
        var capacity = nodePositions.size shl 1
        while (capacity < required) capacity = capacity shl 1
        nodePositions = nodePositions.copyOf(capacity)
        nodeColours = nodeColours.copyOf(capacity)
        nodeCursors = nodeCursors.copyOf(capacity)
        nodeNetworks = nodeNetworks.copyOf(capacity).also { Arrays.fill(it, nodeNetworks.size, capacity, -1) }
        nodeActive = nodeActive.copyOf(capacity)
        nodeGenerations = nodeGenerations.copyOf(capacity)
        networkNext = networkNext.copyOf(capacity).also { Arrays.fill(it, networkNext.size, capacity, -1) }
        networkPrevious =
            networkPrevious.copyOf(capacity).also { Arrays.fill(it, networkPrevious.size, capacity, -1) }
        networkHeads = networkHeads.copyOf(capacity).also { Arrays.fill(it, networkHeads.size, capacity, -1) }
        networkNodeCounts = networkNodeCounts.copyOf(capacity)
        nodeItemCounts = nodeItemCounts.copyOf(capacity)
        queue = queue.copyOf(capacity)
        visitOrder = visitOrder.copyOf(capacity)
        visited = visited.copyOf(capacity)
        dirtyFlags = dirtyFlags.copyOf(capacity)
        dirtyGenerations = dirtyGenerations.copyOf(capacity)
        ensureNetworkCapacity(capacity)
    }

    private fun ensureNetworkCapacity(required: Int) {
        if (required <= networkItemCounts.size) return
        var capacity = networkItemCounts.size shl 1
        while (capacity < required) capacity = capacity shl 1
        networkItemCounts = networkItemCounts.copyOf(capacity)
        networkNodeCounts = networkNodeCounts.copyOf(capacity)
        networkHeads = networkHeads.copyOf(capacity).also { Arrays.fill(it, networkHeads.size, capacity, -1) }
    }

    private fun ensureDirtyCapacity(required: Int) {
        if (required <= dirtyHandles.size) return
        dirtyHandles = dirtyHandles.copyOf(dirtyHandles.size shl 1)
        dirtyGenerations = dirtyGenerations.copyOf(dirtyGenerations.size shl 1)
    }

    private fun ensureItemCapacity(required: Int) {
        if (required <= itemIds.size) return
        var capacity = itemIds.size shl 1
        while (capacity < required) capacity = capacity shl 1
        itemIds = itemIds.copyOf(capacity)
        itemKeys = itemKeys.copyOf(capacity)
        quantities = quantities.copyOf(capacity)
        itemColours = itemColours.copyOf(capacity)
        fromPositions = fromPositions.copyOf(capacity)
        toPositions = toPositions.copyOf(capacity)
        progresses = progresses.copyOf(capacity)
        originPositions = originPositions.copyOf(capacity)
        waitTicks = waitTicks.copyOf(capacity)
    }

    private fun neighbour(
        pos: Long,
        face: Int,
    ): Long {
        val x = PackedPosition.x(pos) + FACES[face][0]
        val y = PackedPosition.y(pos) + FACES[face][1]
        val z = PackedPosition.z(pos) + FACES[face][2]
        return PackedPosition.pack(x, y, z)
    }

    private fun areAdjacent(
        first: Long,
        second: Long,
    ): Boolean {
        val dx = PackedPosition.x(first) - PackedPosition.x(second)
        val dy = PackedPosition.y(first) - PackedPosition.y(second)
        val dz = PackedPosition.z(first) - PackedPosition.z(second)
        return kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) == 1
    }

    private val candidateScratch = IntArray(6)
    private val neighbourScratch = IntArray(MAX_NEIGHBOURS)

    companion object {
        const val SEGMENT_LENGTH = 1_000
        const val MIN_SPACING = 250
        const val DEFAULT_SPEED_PER_TICK = 250
        const val ISOLATED_RETRY_TICKS = 20
        const val REJECTED_ITEM_ID = -1L
        private const val NO_PREVIOUS_NODE = Long.MIN_VALUE
        private const val MAX_NEIGHBOURS = 6
        private const val INITIAL_CAPACITY = 16
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
