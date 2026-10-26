package studio.craftory.engine.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FluidNetworkGraphTest {
    @Test
    fun `merge and split preserve only the shares on surviving nodes`() {
        val graph = FluidNetworkGraph()
        val left = pos(0)
        val bridge = pos(1)
        val right = pos(2)
        graph.addNode(left, FluidNodeKind.PIPE)
        graph.addNode(bridge, FluidNodeKind.PIPE)
        graph.addNode(right, FluidNodeKind.PIPE)

        assertEquals(150L, graph.insert(left, 4, 150))
        graph.removeNode(bridge, FluidNodeRemovalReason.BREAK)

        assertNotEquals(graph.networkOf(left), graph.networkOf(right))
        assertEquals(50L, graph.amountAt(left))
        assertEquals(50L, graph.amountAt(right))
        assertEquals(100L, graph.nodeAmountAt(left) + graph.nodeAmountAt(right))
    }

    @Test
    fun `capacity and tank stacking use node capacities`() {
        val graph = FluidNetworkGraph()
        val bottom = pos(0)
        val top = pos(0, 1)
        graph.addNode(bottom, FluidNodeKind.TANK)
        graph.addNode(top, FluidNodeKind.TANK)

        assertEquals(16_000L, graph.capacityAt(bottom))
        assertEquals(16_000L, graph.insert(bottom, 2, 20_000))
        assertEquals(16_000L, graph.nodeAmountAt(bottom) + graph.nodeAmountAt(top))
    }

    @Test
    fun `fluid conflicts refuse a connection and retry after the network empties`() {
        val graph = FluidNetworkGraph()
        val left = pos(0)
        val right = pos(2)
        val bridge = pos(1)
        graph.addNode(left, FluidNodeKind.PIPE)
        graph.addNode(right, FluidNodeKind.PIPE)
        graph.insert(left, 1, 100)
        graph.insert(right, 2, 100)

        graph.addNode(bridge, FluidNodeKind.PIPE)

        assertNotEquals(graph.networkOf(left), graph.networkOf(right))
        assertEquals(0, graph.insert(bridge, 1, 1))
        assertEquals(100L, graph.drain(right, 100))
        assertEquals(graph.networkOf(left), graph.networkOf(right))
        assertEquals(1, graph.fluidKeyAt(right))
    }

    @Test
    fun `draining conflicting tanks retries only valid tank connections`() {
        val graph = FluidNetworkGraph()
        val lower = pos(10)
        val upper = pos(10, 1)
        val side = pos(11)
        graph.addNode(lower, FluidNodeKind.TANK, FluidNodeRecord(1, 100))
        graph.addNode(upper, FluidNodeKind.TANK, FluidNodeRecord(2, 100))
        graph.addNode(side, FluidNodeKind.TANK, FluidNodeRecord(3, 100))

        assertNotEquals(graph.networkOf(lower), graph.networkOf(upper))
        assertNotEquals(graph.networkOf(lower), graph.networkOf(side))
        assertEquals(100L, graph.drain(side, 100))
        assertNotEquals(graph.networkOf(lower), graph.networkOf(side))
        assertEquals(100L, graph.drain(upper, 100))
        assertEquals(graph.networkOf(lower), graph.networkOf(upper))
    }

    @Test
    fun `persisted node record codec round trips`() {
        val record = FluidNodeRecord(7, 249)

        assertEquals(record, FluidNodeCodec.decode(FluidNodeCodec.encode(record)))
        assertEquals(null, FluidNodeCodec.decode("bad"))
    }

    @Test
    fun `dirty nodes flush no more than once per network every 20 ticks`() {
        val graph = FluidNetworkGraph()
        val pipe = pos(0)
        graph.addNode(pipe, FluidNodeKind.PIPE)
        graph.insert(pipe, 3, 10)
        val first = mutableListOf<Long>()
        graph.drainDirty(0, FluidDirtyNodeVisitor { first += it })
        graph.insert(pipe, 3, 10)
        val early = mutableListOf<Long>()
        graph.drainDirty(19, FluidDirtyNodeVisitor { early += it })
        val due = mutableListOf<Long>()
        graph.drainDirty(20, FluidDirtyNodeVisitor { due += it })

        assertEquals(listOf(pipe), first)
        assertTrue(early.isEmpty())
        assertEquals(listOf(pipe), due)
    }

    private fun pos(
        x: Int,
        y: Int = 0,
        z: Int = 0,
    ): Long = PackedPosition.pack(x, y, z)
}
