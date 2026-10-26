package studio.craftory.core.transport

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.HurlArc
import studio.craftory.core.api.transport.HurlBend
import studio.craftory.core.api.transport.HurlMath
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.core.api.transport.TubeNode
import studio.craftory.engine.transport.TubeBlock

class CoreTransportTest {
    @Test
    fun `section reconciliation removes stale nodes without affecting other unloaded sections`() {
        val tubes = TubeWorld()
        val start = BlockPos.pack(30, 2, 3)
        val middle = BlockPos.pack(31, 2, 3)
        val end = BlockPos.pack(32, 2, 3)
        tubes.add(start, TubeNode(TubeNode.STATION))
        tubes.add(middle, TubeNode())
        tubes.add(end, TubeNode(TubeNode.STATION))
        tubes.reconcileSection(5, 0, 0) { error("empty sections have no nodes to validate") }
        assertEquals(3, tubes.nodeCount)
        tubes.reconcileSection(0, 0, 0) { it == start }
        assertEquals(2, tubes.nodeCount)
        assertTrue(tubes.isPresent(start))
        assertTrue(tubes.isPresent(end))
        assertFalse(tubes.isPresent(middle))
        assertNull(tubes.route(start, end))
        tubes.reconcileSection(0, 0, 0) { true }
        assertEquals(2, tubes.nodeCount)
        tubes.add(middle, TubeNode())
        assertEquals(3, tubes.nodeCount)
        assertEquals(2.0, requireNotNull(tubes.route(start, end)).lengthBlocks)
    }

    @Test
    fun `tube cache tracks presence splits routes and excludes origin from stations`() {
        val tubes = TubeWorld()
        val start = BlockPos.pack(-1, 2, 3)
        val middle = BlockPos.pack(0, 2, 3)
        val end = BlockPos.pack(0, 3, 3)
        assertEquals(0, tubes.nodeCount)
        assertEquals(-1, tubes.networkOf(start))
        assertFalse(tubes.isPresent(start))
        assertTrue(tubes.stationsReachableFrom(start).isEmpty())
        assertNull(tubes.route(start, end))
        tubes.add(start, TubeNode(TubeNode.STATION))
        tubes.add(end, TubeNode(TubeNode.STATION))
        assertEquals(2, tubes.networkCount)
        tubes.add(middle, TubeNode())
        tubes.add(middle, TubeNode())
        assertEquals(3, tubes.nodeCount)
        assertEquals(1, tubes.networkCount)
        assertEquals(listOf(end), tubes.stationsReachableFrom(start))
        assertTrue(tubes.stationsReachableFrom(middle).isEmpty())
        val route = tubes.route(start, end)!!
        assertEquals(listOf(start, middle, end), route.nodes)
        assertEquals(listOf(HurlBend(1, middle)), route.bends)
        assertEquals(2.0, route.lengthBlocks)
        val out = DoubleArray(3)
        assertSame(out, route.positionAt(0.0, out))
        assertArrayEquals(doubleArrayOf(-0.5, 2.5, 3.5), out, 0.000001)
        route.positionAt(1.5, out)
        assertArrayEquals(doubleArrayOf(0.5, 3.0, 3.5), out, 0.000001)
        assertEquals(middle, route.blockAt(1.0))
        assertEquals(listOf(start, middle, end), route.blocksAhead(0.0, 2.0))
        tubes.remove(middle)
        assertFalse(tubes.isPresent(middle))
        assertEquals(2, tubes.networkCount)
        assertNotEquals(tubes.networkOf(start), tubes.networkOf(end))
        assertNull(tubes.route(start, end))
        assertTrue(tubes.stationsReachableFrom(start).isEmpty())
        tubes.remove(middle)
        tubes.add(middle, TubeNode())
        assertEquals(1, tubes.networkCount)
        assertEquals(0, (tubes.clone() as TubeWorld).nodeCount)
    }

    @Test
    fun `node kind validates and clone is independent`() {
        val node = TubeNode(TubeNode.STATION)
        val copy = node.clone() as TubeNode
        node.kind = TubeNode.TUBE
        assertEquals(TubeNode.STATION, copy.kind)
        assertThrows(IllegalArgumentException::class.java) { TubeNode("other") }
        assertThrows(IllegalArgumentException::class.java) { node.kind = "other" }
    }

    @Test
    fun `core and engine packing are identical`() {
        for (block in listOf(BlockPos(-1, -1, -1), BlockPos(-33554432, -2048, 33554431), BlockPos(12, 31, -76))) {
            val engine = TubeBlock(block.x, block.y, block.z)
            assertEquals(block.pack(), engine.pack())
            assertEquals(engine, TubeBlock.unpack(block.pack()))
        }
    }

    @Test
    fun `public arc and cost wrappers cover landing range and malformed inputs`() {
        val launch = HurlPoint(0.5, 2.5, 0.5)
        val landing = HurlPoint(20.5, 4.5, 0.5)
        val arc = HurlArc.create(launch, landing)!!
        assertEquals(landing, arc.landing)
        assertEquals(20.0, arc.flightTicks)
        val out = DoubleArray(3)
        assertSame(out, arc.positionAt(arc.flightTicks, out))
        assertArrayEquals(doubleArrayOf(20.5, 4.5, 0.5), out, 0.01)
        assertNull(HurlArc.create(launch, launch.copy(x = 100.0)))
        assertThrows(IllegalArgumentException::class.java) { HurlArc.create(launch, landing, gravity = -1.0) }
        assertThrows(IllegalArgumentException::class.java) { HurlArc.create(launch.copy(x = Double.NaN), landing) }
        assertEquals(1, HurlMath.unitsFor(0.0))
        assertEquals(1, HurlMath.unitsFor(16.0))
        assertEquals(2, HurlMath.unitsFor(16.01))
        assertThrows(IllegalArgumentException::class.java) { HurlMath.unitsFor(-1.0) }
        assertEquals(0, CraftoryCore.gradeRank("fizz"))
        assertEquals(1, CraftoryCore.gradeRank("pressurised_fizz"))
        assertNull(CraftoryCore.gradeRank("unknown"))
    }
}
