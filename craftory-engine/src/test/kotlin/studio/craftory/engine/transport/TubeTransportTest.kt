package studio.craftory.engine.transport

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TubeTransportTest {
    @Test
    fun `packing matches core layout with signed coordinates and no boundary wrapping`() {
        val block = TubeBlock(-123, -47, 456)
        assertEquals(block, TubeBlock.unpack(block.pack()))
        assertEquals((1L shl 38) or (2L shl 26) or 3L, TubeBlock(1, 2, 3).pack())
        val min = TubeBlock(-(1 shl 25), -2048, -(1 shl 25))
        val max = TubeBlock((1 shl 25) - 1, 2047, (1 shl 25) - 1)
        assertEquals(min, TubeBlock.unpack(min.pack()))
        assertEquals(max, TubeBlock.unpack(max.pack()))
        val graph = TubeGraph()
        graph.add(TubeBlock(0, -2048, 0).pack())
        graph.add(TubeBlock(0, 2047, 0).pack())
        assertEquals(2, graph.networkCount)
        assertThrows(IllegalArgumentException::class.java) { TubeBlock(0, 2048, 0).pack() }
    }

    @Test
    fun `add merges remove splits only affected network and isolated removal empties graph`() {
        val graph = TubeGraph()
        assertEquals(0, graph.nodeCount)
        assertEquals(0, graph.networkCount)
        assertEquals(-1, graph.networkOf(pos(0)))
        assertTrue(graph.stationsInNetwork(pos(0)).isEmpty())
        graph.remove(pos(0))
        graph.add(pos(0), TubeNodeKind.STATION)
        graph.add(pos(2), TubeNodeKind.STATION)
        graph.add(pos(20))
        val unrelated = graph.networkOf(pos(20))
        assertEquals(3, graph.networkCount)
        graph.add(pos(1))
        assertEquals(4, graph.nodeCount)
        assertEquals(2, graph.networkCount)
        assertEquals(graph.networkOf(pos(0)), graph.networkOf(pos(2)))
        assertEquals(setOf(pos(0), pos(2)), graph.stationsInNetwork(pos(1)).toSet())
        assertThrows(IllegalArgumentException::class.java) { graph.add(pos(1)) }
        graph.remove(pos(1))
        assertEquals(3, graph.networkCount)
        assertNotEquals(graph.networkOf(pos(0)), graph.networkOf(pos(2)))
        assertEquals(unrelated, graph.networkOf(pos(20)))
        graph.remove(pos(0))
        graph.remove(pos(2))
        graph.remove(pos(20))
        assertEquals(0, graph.networkCount)
        assertEquals(0, graph.nodeCount)
    }

    @Test
    fun `routing chooses shortest path and passes through intermediate stations`() {
        val graph = TubeGraph()
        graph.add(pos(0), TubeNodeKind.STATION)
        graph.add(pos(2), TubeNodeKind.STATION)
        graph.add(pos(1), TubeNodeKind.STATION)
        for (x in 0..2) graph.add(pos(x, 1))
        val route = graph.route(pos(0), pos(2))!!
        assertEquals(listOf(pos(0), pos(1), pos(2)), route.nodes)
        assertEquals(2.0, route.lengthBlocks)
        assertTrue(route.bends.isEmpty())
        assertNull(graph.route(pos(0), pos(0, 1)))
        assertNull(graph.route(pos(0), pos(30)))
        graph.add(pos(30), TubeNodeKind.STATION)
        assertNull(graph.route(pos(0), pos(30)))
        graph.remove(pos(1))
        assertEquals(4.0, graph.route(pos(0), pos(2))!!.lengthBlocks)
        graph.remove(pos(1, 1))
        assertNull(graph.route(pos(0), pos(2)))
    }

    @Test
    fun `L and U routes bake bends and follow arc length at fixed speed`() {
        val lPath = listOf(pos(0), pos(1), pos(2), pos(2, 1), pos(2, 2))
        val lRoute = route(lPath)
        assertEquals(listOf(TubeBend(2, pos(2))), lRoute.bends)
        val path = lPath + listOf(pos(1, 2), pos(0, 2))
        val route = route(path)
        assertEquals(listOf(TubeBend(2, pos(2)), TubeBend(4, pos(2, 2))), route.bends)
        val spline = route.spline
        assertEquals(4, spline.sampleCount)
        assertEquals(6.0, spline.length)
        val out = DoubleArray(3)
        assertSame(out, spline.positionAt(0.0, out))
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 0.5), out, 0.000001)
        assertArrayEquals(doubleArrayOf(2.5, 0.5, 1.5), spline.positionAt(3.0), 0.000001)
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 2.5), spline.positionAt(6.0), 0.000001)
        assertArrayEquals(spline.positionAt(0.0), spline.positionAt(-10.0), 0.000001)
        assertArrayEquals(spline.positionAt(6.0), spline.positionAt(10.0), 0.000001)
        var distance = 0.0
        repeat(10) { distance += 0.25 }
        assertArrayEquals(doubleArrayOf(2.5, 0.5, 1.0), spline.positionAt(distance), 0.000001)
        path.forEachIndexed { index, block ->
            assertEquals(index, spline.nodeIndexAt(index.toDouble()))
            assertEquals(block, spline.blockAt(index.toDouble()))
        }
        assertEquals(path[1], spline.blockAt(0.5))
        assertEquals(path.subList(1, 4), spline.blocksAhead(1.0, 2.0))
        assertEquals(listOf(path.last()), spline.blocksAhead(100.0, 5.0))
        assertThrows(IllegalArgumentException::class.java) { spline.positionAt(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { spline.blocksAhead(0.0, -1.0) }
    }

    @Test
    fun `singleton and vertical routes retain block identity`() {
        val point = route(listOf(pos(0))).spline
        assertEquals(0.0, point.length)
        assertEquals(1, point.sampleCount)
        assertEquals(pos(0), point.blockAt(100.0))
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 0.5), point.positionAt(10.0), 0.000001)
        val vertical = listOf(TubeBlock(-1, -2, -3).pack(), TubeBlock(-1, -1, -3).pack())
        val spline = route(vertical).spline
        assertArrayEquals(doubleArrayOf(-0.5, -1.0, -2.5), spline.positionAt(0.5), 0.000001)
        assertEquals(vertical[1], spline.blockAt(0.6))
    }

    @Test
    fun `ballistic trajectory lands on target with sensible apex and rejects invalid range`() {
        val start = TransportPoint(0.5, 4.5, -1.5)
        val target = TransportPoint(20.5, 6.5, -1.5)
        val arc = BallisticArc(start, target)
        assertEquals(target, arc.landing)
        assertEquals(20.0, arc.flightTicks)
        assertArrayEquals(doubleArrayOf(start.x, start.y, start.z), arc.positionAt(0.0), 0.01)
        assertArrayEquals(doubleArrayOf(target.x, target.y, target.z), arc.positionAt(arc.flightTicks), 0.01)
        val apex = arc.positionAt(arc.flightTicks / 2)[1]
        assertTrue(apex > target.y && apex < target.y + 10.0)
        assertThrows(IllegalArgumentException::class.java) {
            BallisticArc(start, TransportPoint(65.5, 4.5, -1.5))
        }
        assertThrows(IllegalArgumentException::class.java) { BallisticArc(start, target, horizontalSpeed = 0.0) }
        val vertical = BallisticArc(start, start.copy(y = 14.5))
        assertEquals(14.5, vertical.positionAt(vertical.flightTicks)[1], 0.01)
        val same = BallisticArc(start, start)
        assertTrue(same.flightTicks > 0)
        assertEquals(start.y, same.positionAt(same.flightTicks)[1], 0.01)
    }

    @Test
    fun `cost rounds up with a minimum of one unit`() {
        for ((length, units) in listOf(0.0 to 1, 1.0 to 1, 16.0 to 1, 16.01 to 2, 32.0 to 2)) {
            assertEquals(units, HurlCost.unitsFor(length))
        }
        assertEquals(3, HurlCost.unitsFor(9.0, 4.0))
        assertThrows(IllegalArgumentException::class.java) { HurlCost.unitsFor(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { HurlCost.unitsFor(1.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { HurlCost.unitsFor(Double.NaN) }
    }

    private fun pos(
        x: Int,
        z: Int = 0,
    ): Long = TubeBlock(x, 0, z).pack()

    private fun route(path: List<Long>): TubeRoute {
        val graph = TubeGraph()
        path.forEachIndexed { index, block ->
            graph.add(block, if (index == 0 || index == path.lastIndex) TubeNodeKind.STATION else TubeNodeKind.TUBE)
        }
        return graph.route(path.first(), path.last())!!
    }
}
