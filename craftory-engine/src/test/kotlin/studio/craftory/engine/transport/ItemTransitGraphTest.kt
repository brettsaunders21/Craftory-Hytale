package studio.craftory.engine.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemTransitGraphTest {
    @Test
    fun `dirty visits are throttled to once per network every 20 ticks`() {
        val graph = ItemTransitGraph(speedPerTick = 1)
        val start = pos(0)
        val next = pos(1)
        graph.addNode(start)
        graph.addNode(next)
        val sink = RecordingSink(pos(99))
        graph.insert(start, 1, 1, 0, Long.MIN_VALUE, sink)
        graph.drainDirty { }
        val visitsAtTick = ArrayList<Long>()

        repeat(45) { tick ->
            graph.tick(ALWAYS_ACTIVE, sink)
            graph.drainDirty(tick.toLong()) { visitsAtTick += tick.toLong() }
        }

        assertEquals(listOf(0L, 20L, 40L), visitsAtTick)
    }

    @Test
    fun `chunk-scoped dirty drain preserves nodes in other chunks`() {
        val graph = ItemTransitGraph()
        val chunkZeroStart = pos(0)
        val chunkZeroNext = pos(1)
        val chunkOneStart = pos(32)
        val chunkOneNext = pos(33)
        graph.addNode(chunkZeroStart)
        graph.addNode(chunkZeroNext)
        graph.addNode(chunkOneStart)
        graph.addNode(chunkOneNext)
        val sink = RecordingSink(pos(99))
        graph.insert(chunkZeroStart, 1, 1, 0, Long.MIN_VALUE, sink)
        graph.insert(chunkOneStart, 1, 1, 0, Long.MIN_VALUE, sink)

        val chunkZeroVisits = ArrayList<Long>()
        graph.drainDirtyInChunk(tick = 100, chunkX = 0, chunkZ = 0) { chunkZeroVisits += it }
        val laterVisits = ArrayList<Long>()
        graph.drainDirty(tick = 120) { laterVisits += it }

        assertEquals(listOf(chunkZeroStart), chunkZeroVisits)
        assertEquals(listOf(chunkOneStart), laterVisits)
    }

    @Test
    fun `items follow a line and arrive at its only destination`() {
        val graph = ItemTransitGraph()
        val start = pos(0)
        val middle = pos(1)
        val destination = pos(2)
        graph.addNode(start)
        graph.addNode(middle)
        graph.addNode(destination)
        val sink = RecordingSink(destination)

        graph.insert(start, 1, 1, 0, Long.MIN_VALUE, sink)
        repeat(8) { graph.tick(ALWAYS_ACTIVE, sink) }

        assertEquals(1, sink.deliveredAt(destination))
        assertEquals(0, graph.itemCount)
    }

    @Test
    fun `uncoloured branches alternate round robin`() {
        val graph = ItemTransitGraph()
        val source = pos(-1)
        val junction = pos(0)
        val firstBranch = pos(0, 1, 0)
        val secondBranch = pos(0, 0, 1)
        graph.addNode(source)
        graph.addNode(junction)
        graph.addNode(firstBranch)
        graph.addNode(secondBranch)
        val sink = RecordingSink(firstBranch, secondBranch)

        repeat(3) { index ->
            graph.insert(source, 5, 1, 0, Long.MIN_VALUE, sink)
            graph.tick(ALWAYS_ACTIVE, sink)
            if (index < 2) graph.tick(ALWAYS_ACTIVE, sink)
        }
        repeat(8) { graph.tick(ALWAYS_ACTIVE, sink) }

        assertEquals(listOf(firstBranch, secondBranch, firstBranch), sink.deliveries.map { it.first })
    }

    @Test
    fun `stamped items prefer matching branch and unstamped items avoid coloured branch`() {
        val graph = ItemTransitGraph()
        val source = pos(-1)
        val junction = pos(0)
        val redBranch = pos(0, 1, 0)
        val plainBranch = pos(1)
        graph.addNode(source)
        graph.addNode(junction)
        graph.addNode(redBranch, 1)
        graph.addNode(plainBranch)
        val sink = RecordingSink(redBranch, plainBranch)

        graph.insert(source, 7, 1, 1, Long.MIN_VALUE, sink)
        graph.tick(ALWAYS_ACTIVE, sink)
        graph.tick(ALWAYS_ACTIVE, sink)
        graph.insert(source, 7, 1, 0, Long.MIN_VALUE, sink)
        repeat(8) { graph.tick(ALWAYS_ACTIVE, sink) }

        assertEquals(listOf(redBranch, plainBranch), sink.deliveries.map { it.first })
    }

    @Test
    fun `breaking a middle node splits networks and drops items on broken segments`() {
        val graph = ItemTransitGraph()
        val left = pos(0)
        val broken = pos(1)
        val right = pos(2)
        val otherStart = pos(10)
        val otherEnd = pos(11)
        graph.addNode(left)
        graph.addNode(broken)
        graph.addNode(right)
        graph.addNode(otherStart)
        graph.addNode(otherEnd)
        val sink = RecordingSink(pos(99))
        graph.insert(left, 3, 2, 0, Long.MIN_VALUE, sink)
        graph.insert(otherStart, 4, 1, 0, Long.MIN_VALUE, sink)

        graph.removeNode(broken, NodeRemovalReason.BREAK, sink)

        assertEquals(2, sink.dropped.sumOf { it.quantity })
        assertEquals(1, graph.itemCount)
        assertNotEquals(graph.networkOf(left), graph.networkOf(right))
        assertEquals(graph.networkOf(otherStart), graph.networkOf(otherEnd))
    }

    @Test
    fun `unload persists owned items without drops and reload completes delivery`() {
        val graph = ItemTransitGraph()
        val start = pos(0)
        val destination = pos(1)
        graph.addNode(start)
        graph.addNode(destination)
        val sink = RecordingSink(destination)
        graph.insert(start, 9, 3, 2, pos(-3), sink)
        graph.tick(ALWAYS_ACTIVE, sink)
        val beforeUnload = graph.itemRecord(0)

        val persisted = graph.removeNode(start, NodeRemovalReason.UNLOAD, sink)

        assertEquals(1, persisted.size)
        assertEquals(beforeUnload, persisted.single())
        assertTrue(sink.dropped.isEmpty())
        graph.addNode(start, persistedItems = persisted)
        repeat(3) { graph.tick(ALWAYS_ACTIVE, sink) }

        assertEquals(3, sink.deliveredAt(destination))
        assertEquals(0, graph.itemCount)
    }

    @Test
    fun `partially reloaded pipes with overlapping persisted ids remain unique`() {
        val graph = ItemTransitGraph()
        val loadedPipe = pos(40)
        val unloadedPipe = pos(80)
        val sink = RecordingSink(pos(99))
        val overlappingId = 57L
        val loadedRecord = transitRecord(overlappingId, loadedPipe)
        val unloadedRecord = transitRecord(overlappingId, unloadedPipe)

        assertEquals(loadedRecord.id, unloadedRecord.id)
        graph.addNode(loadedPipe, persistedItems = listOf(loadedRecord))
        graph.drainDirty { }
        val insertedId = graph.insert(loadedPipe, 3, 1, 0, Long.MIN_VALUE, sink)
        graph.drainDirty { }

        assertTrue(insertedId >= 0)
        assertFalse(graph.containsNode(unloadedPipe))
        graph.addNode(unloadedPipe, persistedItems = listOf(unloadedRecord))
        val dirtyNodes = ArrayList<Long>()
        graph.drainDirty { dirtyNodes.add(it) }

        val itemIds = (0 until graph.itemCount).map { graph.itemId(it) }
        assertEquals(3, itemIds.size)
        assertEquals(itemIds.size, itemIds.toSet().size)
        assertTrue(itemIds.all { it >= 0 })
        assertEquals(listOf(unloadedPipe), dirtyNodes)
    }

    @Test
    fun `inactive source node freezes item progress`() {
        val graph = ItemTransitGraph()
        val start = pos(0)
        val destination = pos(1)
        graph.addNode(start)
        graph.addNode(destination)
        val sink = RecordingSink(destination)
        graph.insert(start, 2, 1, 0, Long.MIN_VALUE, sink)
        val progress = graph.itemProgress(0)

        repeat(12) { graph.tick(NodeActivity { false }, sink) }

        assertEquals(progress, graph.itemProgress(0))
    }

    @Test
    fun `moving item marks its owner dirty for persistence`() {
        val graph = ItemTransitGraph()
        val start = pos(0)
        val destination = pos(1)
        graph.addNode(start)
        graph.addNode(destination)
        val sink = RecordingSink(destination)
        graph.insert(start, 2, 1, 0, Long.MIN_VALUE, sink)
        graph.drainDirty { }

        graph.tick(ALWAYS_ACTIVE, sink)

        var dirtyNode: Long? = null
        graph.drainDirty { dirtyNode = it }

        assertEquals(start, dirtyNode)
    }

    @Test
    fun `delivered items leave no awake networks`() {
        val graph = ItemTransitGraph(speedPerTick = ItemTransitGraph.SEGMENT_LENGTH)
        val start = pos(0)
        val destination = pos(1)
        graph.addNode(start)
        graph.addNode(destination)
        val sink = RecordingSink(destination)
        graph.insert(start, 11, 1, 0, Long.MIN_VALUE, sink)
        graph.tick(ALWAYS_ACTIVE, sink)
        val countAfterDelivery = graph.itemCount

        repeat(20) { graph.tick(ALWAYS_ACTIVE, sink) }

        assertEquals(0, countAfterDelivery)
        assertEquals(0, graph.awakeNetworkCount)
    }

    @Test
    fun `adding and removing a bridge merges then splits item networks incrementally`() {
        val graph = ItemTransitGraph()
        val left = pos(20)
        val bridge = pos(21)
        val right = pos(22)
        val sink = RecordingSink(pos(99))
        graph.addNode(left)
        graph.addNode(right)
        graph.insert(left, 1, 1, 0, Long.MIN_VALUE, sink)
        graph.insert(right, 2, 1, 0, Long.MIN_VALUE, sink)

        assertEquals(2, graph.awakeNetworkCount)
        graph.addNode(bridge)

        assertEquals(graph.networkOf(left), graph.networkOf(bridge))
        assertEquals(graph.networkOf(right), graph.networkOf(bridge))
        assertEquals(1, graph.awakeNetworkCount)
        assertEquals(2, graph.itemCount)

        graph.removeNode(bridge, NodeRemovalReason.BREAK, sink)

        assertNotEquals(graph.networkOf(left), graph.networkOf(right))
        assertEquals(2, graph.awakeNetworkCount)
        assertEquals(1, graph.itemsOwnedByCount(left))
        assertEquals(1, graph.itemsOwnedByCount(right))
        assertEquals(2, graph.itemCount)
    }

    @Test
    fun `node handles are reused through repeated add and remove cycles`() {
        val graph = ItemTransitGraph()
        val sink = RecordingSink()

        repeat(2_000) { index ->
            val node = pos(index)
            graph.addNode(node)
            graph.removeNode(node, NodeRemovalReason.BREAK, sink)
        }

        assertEquals(0, graph.nodeCount)
        assertEquals(0, graph.networkCount)
        assertEquals(16, graph.nodeHandleCapacity)
    }

    @Test
    fun `can insert follows topology and congestion rules`() {
        val graph = ItemTransitGraph()
        val start = pos(30)
        val next = pos(31)
        val sink = RecordingSink(pos(99))

        assertFalse(graph.canInsert(start))
        graph.addNode(start)
        graph.addNode(next)
        assertTrue(graph.canInsert(start))
        graph.insert(start, 1, 1, 0, Long.MIN_VALUE, sink)
        assertFalse(graph.canInsert(start))
    }

    @Test
    fun `transit codec round trips records and ignores malformed fields`() {
        val records =
            listOf(
                TransitItemRecord(0, 3, 8, 4, pos(-4, 5, -6), pos(1, 2, 3), 750, pos(8, 9, 10), "Ore_Copper"),
                TransitItemRecord(13, 9, 1, 0, pos(0), pos(1), 250, Long.MIN_VALUE),
            )

        val encoded = TransitItemCodec.encode(records)
        val legacyRecords = records.map { it.copy(itemId = null) }
        val legacyEncoded =
            "1;" +
                encoded
                    .substringAfter(';')
                    .split(';')
                    .joinToString(";") { it.substringBeforeLast(',') }
        val malformedItemId = encoded.substringAfter(';').substringBefore(';').substringBeforeLast(',') + ",!"

        assertEquals(records, TransitItemCodec.decode(encoded))
        assertEquals(records, TransitItemCodec.decode("$encoded;bad;1,2;1,2,3,4,5,6,7,8"))
        assertEquals(legacyRecords, TransitItemCodec.decode(legacyEncoded))
        assertTrue(TransitItemCodec.decode("2;$malformedItemId").isEmpty())
        assertTrue(TransitItemCodec.decode("3;$encoded").isEmpty())
        assertFalse(TransitItemCodec.decode("1;").isNotEmpty())
    }

    private fun pos(
        x: Int,
        y: Int = 0,
        z: Int = 0,
    ): Long = PackedPosition.pack(x, y, z)

    private fun transitRecord(
        id: Long,
        owner: Long,
    ) = TransitItemRecord(id, 1, 1, 0, owner, owner, 500, Long.MIN_VALUE)

    private companion object {
        val ALWAYS_ACTIVE = NodeActivity { true }
    }

    private class RecordingSink(
        private vararg val destinations: Long,
    ) : TransitSink {
        val deliveries = ArrayList<Pair<Long, Int>>()
        val dropped = ArrayList<Drop>()

        override fun tryDeliver(
            nodePos: Long,
            itemKey: Int,
            quantity: Int,
            originPos: Long,
        ): Int {
            if (destinations.isNotEmpty() && nodePos !in destinations) return 0
            deliveries += nodePos to quantity
            return quantity
        }

        override fun dropped(
            itemKey: Int,
            quantity: Int,
            worldX: Double,
            worldY: Double,
            worldZ: Double,
        ) {
            dropped += Drop(itemKey, quantity, worldX, worldY, worldZ)
        }

        fun deliveredAt(position: Long): Int = deliveries.filter { it.first == position }.sumOf { it.second }
    }

    private data class Drop(
        val itemKey: Int,
        val quantity: Int,
        val worldX: Double,
        val worldY: Double,
        val worldZ: Double,
    )
}
