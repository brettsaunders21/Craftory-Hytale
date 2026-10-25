@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")

package studio.craftory.engine.transport

import java.util.Arrays

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
    private val nodeMap = LongIntIndex()
    private var nodePositions = LongArray(INITIAL_CAPACITY)
    private var nodeColours = ByteArray(INITIAL_CAPACITY)
    private var nodeCursors = IntArray(INITIAL_CAPACITY)
    private var nodeNetworks = IntArray(INITIAL_CAPACITY) { -1 }
    private var nodeActive = BooleanArray(INITIAL_CAPACITY)
    private var nodeSize = 0
    private var componentCount = 0
    private var networkItemCounts = IntArray(INITIAL_CAPACITY)
    private var awakeNetworks = IntArray(INITIAL_CAPACITY)
    private var awakeNetworkSize = 0
    private var queue = IntArray(INITIAL_CAPACITY)
    private var dirtyHandles = IntArray(INITIAL_CAPACITY)
    private var dirtyFlags = BooleanArray(INITIAL_CAPACITY)
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
    private var nextItemId = 1L

    init {
        require(speedPerTick in 1..SEGMENT_LENGTH)
    }

    val nodeCount: Int
        get() = nodeMap.size

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
        ensureNodeCapacity(nodeSize + 1)
        val handle = nodeSize++
        nodePositions[handle] = pos
        nodeColours[handle] = colour.toByte()
        nodeCursors[handle] = 0
        nodeActive[handle] = true
        nodeMap.put(pos, handle)
        rebuildNetworks()
        persistedItems.forEach { record ->
            require(record.fromPos == pos)
            require(record.quantity > 0 && record.colour in 0..4)
            appendItem(record)
            if (record.id >= nextItemId) nextItemId = record.id + 1
        }
        rebuildAwakeNetworks()
    }

    fun connect(
        firstPos: Long,
        secondPos: Long,
    ) {
        require(containsNode(firstPos) && containsNode(secondPos))
        require(areAdjacent(firstPos, secondPos))
        rebuildNetworks()
    }

    fun removeNode(
        pos: Long,
        reason: NodeRemovalReason,
        sink: TransitSink,
    ): List<TransitItemRecord> {
        val handle = nodeMap.getOrDefault(pos, -1)
        if (handle < 0) return emptyList()
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
        nodeMap.remove(pos)
        nodeActive[handle] = false
        nodeNetworks[handle] = -1
        if (dirtyFlags[handle]) dirtyFlags[handle] = false
        rebuildNetworks()
        rebuildAwakeNetworks()
        return persisted
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
        if (handle < 0 || itemKey < 0 || quantity <= 0 || colour !in 0..4) return REJECTED_ITEM_ID
        for (index in 0 until itemSize) {
            if (fromPositions[index] == nodePos && progresses[index] < MIN_SPACING) {
                return REJECTED_ITEM_ID
            }
        }
        ensureItemCapacity(itemSize + 1)
        val id = nextItemId++
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
        markDirty(handle)
        resolveArrival(itemIndex, handle, NO_PREVIOUS_NODE, sink)
        rebuildAwakeNetworks()
        return id
    }

    fun tick(
        activity: NodeActivity,
        sink: TransitSink,
    ) {
        for (awakeIndex in 0 until awakeNetworkCount) {
            val network = awakeNetworks[awakeIndex]
            var itemIndex = 0
            while (itemIndex < itemSize) {
                val ownerHandle = nodeMap.getOrDefault(fromPositions[itemIndex], -1)
                if (ownerHandle < 0 || nodeNetworks[ownerHandle] != network) {
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
                    progresses[itemIndex] = SEGMENT_LENGTH
                    itemIndex++
                    continue
                }
                progresses[itemIndex] += speedPerTick
                if (progresses[itemIndex] >= SEGMENT_LENGTH) {
                    val arrivedAt = toPositions[itemIndex]
                    progresses[itemIndex] -= SEGMENT_LENGTH
                    waitTicks[itemIndex] = 0
                    val removed = resolveArrival(itemIndex, destinationHandle, fromPos, sink)
                    if (!removed) itemIndex++
                    continue
                }
                itemIndex++
            }
        }
        rebuildAwakeNetworks()
    }

    fun drainDirty(visitor: DirtyNodeVisitor) {
        for (index in 0 until dirtyCount) {
            val handle = dirtyHandles[index]
            if (!nodeActive[handle]) continue
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
        if (oldOwner >= 0) markDirty(oldOwner)
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

    private fun rebuildNetworks() {
        componentCount = 0
        Arrays.fill(nodeNetworks, 0, nodeCount, -1)
        ensureNetworkCapacity(nodeCount)
        for (start in 0 until nodeCount) {
            if (!nodeActive[start] || nodeNetworks[start] >= 0) continue
            val root = start
            var head = 0
            var tail = 0
            queue[tail++] = start
            nodeNetworks[start] = root
            while (head < tail) {
                val current = queue[head++]
                val currentPos = nodePositions[current]
                for (face in FACES.indices) {
                    val adjacent = nodeMap.getOrDefault(neighbour(currentPos, face), -1)
                    if (adjacent >= 0 && nodeNetworks[adjacent] < 0) {
                        nodeNetworks[adjacent] = root
                        queue[tail++] = adjacent
                    }
                }
            }
            componentCount++
        }
        rebuildAwakeNetworks()
    }

    private fun rebuildAwakeNetworks() {
        ensureNetworkCapacity(nodeCount)
        Arrays.fill(networkItemCounts, 0, nodeCount, 0)
        for (index in 0 until itemSize) {
            val handle = nodeMap.getOrDefault(fromPositions[index], -1)
            if (handle >= 0) {
                val network = nodeNetworks[handle]
                if (network >= 0) networkItemCounts[network]++
            }
        }
        awakeNetworkSize = 0
        for (handle in 0 until nodeCount) {
            if (nodeActive[handle] && nodeNetworks[handle] == handle && networkItemCounts[handle] > 0) {
                awakeNetworks[awakeNetworkSize++] = handle
            }
        }
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
    }

    private fun removeItemAt(index: Int) {
        val ownerHandle = nodeMap.getOrDefault(fromPositions[index], -1)
        if (ownerHandle >= 0) markDirty(ownerHandle)
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
        dirtyHandles[dirtyCount++] = handle
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
        queue = queue.copyOf(capacity)
        dirtyFlags = dirtyFlags.copyOf(capacity)
        ensureNetworkCapacity(capacity)
    }

    private fun ensureNetworkCapacity(required: Int) {
        if (required <= networkItemCounts.size) return
        var capacity = networkItemCounts.size shl 1
        while (capacity < required) capacity = capacity shl 1
        networkItemCounts = networkItemCounts.copyOf(capacity)
        awakeNetworks = awakeNetworks.copyOf(capacity)
    }

    private fun ensureDirtyCapacity(required: Int) {
        if (required <= dirtyHandles.size) return
        dirtyHandles = dirtyHandles.copyOf(dirtyHandles.size shl 1)
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

    companion object {
        const val SEGMENT_LENGTH = 1_000
        const val MIN_SPACING = 250
        const val DEFAULT_SPEED_PER_TICK = 250
        const val ISOLATED_RETRY_TICKS = 20
        const val REJECTED_ITEM_ID = -1L
        private const val NO_PREVIOUS_NODE = Long.MIN_VALUE
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

private class LongIntIndex(
    initialCapacity: Int = 16,
) {
    private var keys = LongArray(initialCapacity)
    private var values = IntArray(initialCapacity)
    private var states = ByteArray(initialCapacity)
    var size = 0
        private set

    fun getOrDefault(
        key: Long,
        defaultValue: Int,
    ): Int {
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() != EMPTY) {
            if (states[slot].toInt() == OCCUPIED && keys[slot] == key) return values[slot]
            slot = (slot + 1) and (keys.size - 1)
        }
        return defaultValue
    }

    fun put(
        key: Long,
        value: Int,
    ) {
        if ((size + 1) * 10 >= keys.size * 7) rehash(keys.size shl 1)
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() == OCCUPIED) {
            if (keys[slot] == key) {
                values[slot] = value
                return
            }
            slot = (slot + 1) and (keys.size - 1)
        }
        keys[slot] = key
        values[slot] = value
        states[slot] = OCCUPIED.toByte()
        size++
    }

    fun remove(key: Long) {
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() != EMPTY) {
            if (states[slot].toInt() == OCCUPIED && keys[slot] == key) {
                states[slot] = DELETED.toByte()
                size--
                return
            }
            slot = (slot + 1) and (keys.size - 1)
        }
    }

    private fun rehash(capacity: Int) {
        val oldKeys = keys
        val oldValues = values
        val oldStates = states
        keys = LongArray(capacity)
        values = IntArray(capacity)
        states = ByteArray(capacity)
        size = 0
        for (index in oldKeys.indices) {
            if (oldStates[index].toInt() == OCCUPIED) put(oldKeys[index], oldValues[index])
        }
    }

    private fun hash(key: Long): Int {
        var mixed = key
        mixed = (mixed xor (mixed ushr 33)) * -49064778989782843L
        mixed = (mixed xor (mixed ushr 33)) * -4265267296055464877L
        return (mixed xor (mixed ushr 33)).toInt()
    }

    companion object {
        private const val EMPTY = 0
        private const val OCCUPIED = 1
        private const val DELETED = 2
    }
}
