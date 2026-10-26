package studio.craftory.engine.energy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import kotlin.random.Random

class EnergyGraphTest {
    @Test
    fun `graded network draw is atomic spans stores and clears depleted rank`() {
        val graph = EnergyGraph()
        graph.add(1, spec(role = MemberSpec.LINK), longArrayOf())
        for (id in 2L..4L) graph.add(id, store(), longArrayOf(1))
        graph.restoreStored(2, 20, 0)
        graph.restoreStored(3, 30, 1)
        graph.restoreStored(4, 40, 2)
        graph.setStorageEnabled(4, false)
        assertEquals(70L, graph.networkStored(1, 1))
        assertFalse(graph.drawStored(1, 71, 1))
        assertEquals(30L, graph.stored(3))
        assertEquals(40L, graph.stored(4))
        assertTrue(graph.drawStored(1, 50, 1))
        assertEquals(20L, graph.stored(2))
        assertEquals(0L, graph.stored(3))
        assertEquals(-1, graph.storedRank(3))
        assertEquals(20L, graph.stored(4))
        assertEquals(2, graph.storedRank(4))
        assertEquals(20L, graph.networkStored(1, 1))
        assertTrue(graph.drawStored(1, 0, 5))
        assertFalse(graph.drawStored(999, 0, 0))
        assertEquals(0L, graph.networkStored(999, 0))
        assertThrows(IllegalArgumentException::class.java) { graph.drawStored(1, -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { graph.drawStored(1, 1, -1) }
    }

    @Test
    fun `network stored saturates without overflowing and draws stay isolated`() {
        val graph = EnergyGraph()
        val huge = spec(role = MemberSpec.STORAGE, capacity = Long.MAX_VALUE)
        graph.add(1, huge, longArrayOf())
        graph.add(2, huge, longArrayOf(1))
        graph.add(3, huge, longArrayOf())
        for (id in 1L..3L) graph.restoreStored(id, Long.MAX_VALUE, 1)
        assertEquals(Long.MAX_VALUE, graph.networkStored(1, 1))
        assertTrue(graph.drawStored(1, Long.MAX_VALUE, 1))
        assertEquals(setOf(0L, Long.MAX_VALUE), setOf(graph.stored(1), graph.stored(2)))
        assertEquals(Long.MAX_VALUE, graph.networkStored(1, 1))
        assertEquals(Long.MAX_VALUE, graph.stored(3))
    }

    @Test
    fun `grade table is contiguous and supports added definitions`() {
        val table =
            GradeTable(
                listOf(
                    GradeDef("base", 0),
                    GradeDef("addon_grade", 1),
                ),
            )

        assertEquals("base", table.byRank(0)?.id)
        assertEquals(1, table.rankOf("addon_grade"))
        assertEquals(6, GradeTable.DEFAULT.size)
        assertEquals("prismatic_glimmer", GradeTable.DEFAULT.byRank(5)?.id)
    }

    @Test
    fun `adding a bridge merges both existing networks`() {
        val graph = EnergyGraph()
        graph.add(1L, spec(), longArrayOf())
        graph.add(2L, spec(), longArrayOf())
        assertNotEquals(graph.networkOf(1L), graph.networkOf(2L))

        graph.add(3L, spec(), longArrayOf(1L, 2L))

        assertEquals(graph.networkOf(1L), graph.networkOf(2L))
        assertEquals(graph.networkOf(1L), graph.networkOf(3L))
    }

    @Test
    fun `removing a bridge splits its network`() {
        val graph = EnergyGraph()
        graph.add(1L, spec(), longArrayOf())
        graph.add(2L, spec(), longArrayOf(1L))
        graph.add(3L, spec(), longArrayOf(2L))

        graph.remove(2L)

        assertNotEquals(graph.networkOf(1L), graph.networkOf(3L))
        assertEquals(-1, graph.networkOf(2L))
    }

    @Test
    fun `removing a non bridge keeps a connected network`() {
        val graph = EnergyGraph()
        graph.add(1L, spec(), longArrayOf())
        graph.add(2L, spec(), longArrayOf(1L))
        graph.add(3L, spec(), longArrayOf(1L, 2L))

        graph.remove(1L)

        assertEquals(graph.networkOf(2L), graph.networkOf(3L))
    }

    @Test
    fun `disconnect splits only the disconnected component`() {
        val graph = EnergyGraph()
        graph.add(1L, spec(), longArrayOf())
        graph.add(2L, spec(), longArrayOf(1L))
        graph.add(3L, spec(), longArrayOf(2L))

        graph.disconnect(2L, 3L)

        assertNotEquals(graph.networkOf(1L), graph.networkOf(3L))
        assertEquals(graph.networkOf(1L), graph.networkOf(2L))
    }

    @Test
    fun `network grade is the greatest active producer or store grade`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, store(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.setProduction(1L, 10L, 2)
        graph.tick()
        assertEquals(10L, graph.stored(2L))

        graph.setProduction(1L, 1L, 1)
        graph.setDemand(3L, 1L)
        graph.tick()

        assertEquals(2, graph.deliveredRank(3L))
    }

    @Test
    fun `over grade consumers receive nothing and fire overload`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, consumer(maxRank = 1), longArrayOf(1L))
        graph.setProduction(1L, 10L, 2)
        graph.setDemand(2L, 5L)
        val sink = RecordingSink()

        graph.tick(sink)

        assertEquals(0L, graph.delivered(2L))
        assertEquals(listOf(2L to 1), sink.overloads)
    }

    @Test
    fun `under grade consumers fire under grade`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, consumer(minRank = 1), longArrayOf(1L))
        graph.setProduction(1L, 10L, 0)
        graph.setDemand(2L, 5L)
        val sink = RecordingSink()

        graph.tick(sink)

        assertEquals(0L, graph.delivered(2L))
        assertEquals(listOf(2L), sink.underGrades)
    }

    @Test
    fun `brownout proportionally shares a priority bucket and fires once`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, consumer(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(1L))
        graph.setProduction(1L, 5L, 0)
        graph.setDemand(2L, 4L)
        graph.setDemand(3L, 6L)
        val sink = RecordingSink()

        graph.tick(sink)

        assertEquals(2L, graph.delivered(2L))
        assertEquals(3L, graph.delivered(3L))
        assertEquals(1, sink.brownouts.size)
        assertEquals(0.5, sink.brownouts.single().satisfiedFraction, 0.000001)
    }

    @Test
    fun `higher priority consumers are satisfied first`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, consumer(priority = 1), longArrayOf(1L))
        graph.add(3L, consumer(priority = 0), longArrayOf(1L))
        graph.setProduction(1L, 5L, 0)
        graph.setDemand(2L, 5L)
        graph.setDemand(3L, 5L)

        graph.tick()

        assertEquals(5L, graph.delivered(2L))
        assertEquals(0L, graph.delivered(3L))
    }

    @Test
    fun `surplus production charges stores before later demand discharges them`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, store(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.setProduction(1L, 8L, 0)
        graph.setDemand(3L, 5L)

        graph.tick()
        assertEquals(5L, graph.delivered(3L))
        assertEquals(3L, graph.stored(2L))

        graph.setProduction(1L, 0L, 0)
        graph.setDemand(3L, 5L)
        graph.tick()

        assertEquals(3L, graph.delivered(3L))
        assertEquals(0L, graph.stored(2L))
    }

    @Test
    fun `charged stores neither charge nor discharge at a different network grade`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, store(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.setProduction(1L, 10L, 0)
        graph.tick()
        assertEquals(10L, graph.stored(2L))

        graph.setProduction(1L, 1L, 1)
        graph.setDemand(3L, 5L)
        graph.tick()
        assertEquals(1L, graph.delivered(3L))
        assertEquals(10L, graph.stored(2L))

        graph.setProduction(1L, 10L, 1)
        graph.setDemand(3L, 0L)
        graph.tick()
        assertEquals(10L, graph.stored(2L))
    }

    @Test
    fun `disabled storage preserves charge and resumes without rebuilding`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, store(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.restoreStored(2L, 5L, 0)
        val network = graph.networkOf(2L)

        graph.setStorageEnabled(2L, false)
        graph.setDemand(3L, 5L)
        graph.tick()
        assertEquals(0L, graph.delivered(3L))
        assertEquals(5L, graph.stored(2L))

        graph.setProduction(1L, 10L, 0)
        graph.setDemand(3L, 0L)
        graph.tick()
        assertEquals(5L, graph.stored(2L))

        graph.setStorageEnabled(2L, true)
        assertEquals(network, graph.networkOf(2L))
        graph.setProduction(1L, 0L, 0)
        graph.setDemand(3L, 5L)
        graph.tick()
        assertEquals(5L, graph.delivered(3L))
        assertEquals(0L, graph.stored(2L))

        graph.setProduction(1L, 10L, 0)
        graph.setDemand(3L, 0L)
        graph.tick()
        assertEquals(10L, graph.stored(2L))
    }

    @Test
    fun `link losses reduce network supply`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, spec(role = MemberSpec.LINK, lossPerMillion = 200_000), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.setProduction(1L, 10L, 0)
        graph.setDemand(3L, 10L)

        graph.tick()

        assertEquals(8L, graph.delivered(3L))
    }

    @Test
    fun `idle networks sleep and mutations wake them`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, store(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(2L))
        graph.setProduction(1L, 10L, 0)

        graph.tick()
        assertEquals(10L, graph.stored(2L))
        graph.setProduction(1L, 0L, 0)
        graph.tick()

        assertFalse(graph.isAwake(3L))
        graph.setDemand(3L, 1L)
        assertTrue(graph.isAwake(3L))
        val sink = RecordingSink()
        graph.tick(sink)
        assertEquals(1L, graph.delivered(3L))
        assertTrue(sink.underGrades.isEmpty())
    }

    @Test
    fun `rounding remainder goes to the lowest member slot`() {
        val graph = EnergyGraph()
        graph.add(1L, producer(), longArrayOf())
        graph.add(2L, consumer(), longArrayOf(1L))
        graph.add(3L, consumer(), longArrayOf(1L))
        graph.add(4L, consumer(), longArrayOf(1L))
        graph.setProduction(1L, 1L, 0)
        graph.setDemand(2L, 1L)
        graph.setDemand(3L, 1L)
        graph.setDemand(4L, 1L)

        graph.tick()

        assertEquals(1L, graph.delivered(2L))
        assertEquals(0L, graph.delivered(3L))
        assertEquals(0L, graph.delivered(4L))
    }

    @Test
    fun `randomized adds and removals match naive breadth first search`() {
        val graph = EnergyGraph()
        val random = Random(1974)
        val adjacency = LinkedHashMap<Long, MutableSet<Long>>()
        val ids = ArrayList<Long>(10_000)

        for (id in 0L until 10_000L) {
            val neighbours = LinkedHashSet<Long>()
            if (ids.isNotEmpty()) {
                repeat(random.nextInt(1, 4)) {
                    neighbours.add(ids[random.nextInt(ids.size)])
                }
            }
            graph.add(id, spec(), neighbours.toLongArray())
            adjacency[id] = LinkedHashSet()
            for (neighbour in neighbours) {
                adjacency.getValue(id).add(neighbour)
                adjacency.getValue(neighbour).add(id)
            }
            ids.add(id)
        }

        repeat(250) {
            val removed = ids.removeAt(random.nextInt(ids.size))
            graph.remove(removed)
            val neighbours = adjacency.remove(removed)!!
            for (neighbour in neighbours) {
                adjacency.getValue(neighbour).remove(removed)
            }
        }

        repeat(250) { offset ->
            val id = 10_000L + offset
            val neighbours = LinkedHashSet<Long>()
            repeat(random.nextInt(1, 4)) {
                neighbours.add(ids[random.nextInt(ids.size)])
            }
            graph.add(id, spec(), neighbours.toLongArray())
            adjacency[id] = LinkedHashSet()
            for (neighbour in neighbours) {
                adjacency.getValue(id).add(neighbour)
                adjacency.getValue(neighbour).add(id)
            }
            ids.add(id)
        }

        assertMatchesBreadthFirstSearch(graph, adjacency)
    }

    private fun assertMatchesBreadthFirstSearch(
        graph: EnergyGraph,
        adjacency: Map<Long, Set<Long>>,
    ) {
        val expectedComponents = HashMap<Long, Int>()
        var nextComponent = 0
        for (start in adjacency.keys) {
            if (expectedComponents.containsKey(start)) {
                continue
            }
            val queue = ArrayDeque<Long>()
            queue.addLast(start)
            expectedComponents[start] = nextComponent
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (neighbour in adjacency.getValue(current)) {
                    if (!expectedComponents.containsKey(neighbour)) {
                        expectedComponents[neighbour] = nextComponent
                        queue.addLast(neighbour)
                    }
                }
            }
            nextComponent++
        }

        val expectedToActual = HashMap<Int, Int>()
        val actualToExpected = HashMap<Int, Int>()
        for ((id, expected) in expectedComponents) {
            val actual = graph.networkOf(id)
            assertTrue(actual >= 0)
            val previousActual = expectedToActual.putIfAbsent(expected, actual)
            if (previousActual != null) {
                assertEquals(previousActual, actual)
            }
            val previousExpected = actualToExpected.putIfAbsent(actual, expected)
            if (previousExpected != null) {
                assertEquals(previousExpected, expected)
            }
        }
    }

    private fun producer(): MemberSpec = spec(role = MemberSpec.PRODUCER)

    private fun consumer(
        minRank: Int = 0,
        maxRank: Int = 5,
        priority: Int = 0,
    ): MemberSpec =
        spec(
            role = MemberSpec.CONSUMER,
            maxInput = Long.MAX_VALUE,
            minRank = minRank,
            maxRank = maxRank,
            priority = priority,
        )

    private fun store(): MemberSpec =
        spec(
            role = MemberSpec.STORAGE,
            capacity = 100L,
            maxInput = 100L,
            maxOutput = 100L,
        )

    private fun spec(
        role: Int = 0,
        capacity: Long = 0L,
        maxInput: Long = Long.MAX_VALUE,
        maxOutput: Long = Long.MAX_VALUE,
        minRank: Int = 0,
        maxRank: Int = 5,
        priority: Int = 0,
        lossPerMillion: Int = 0,
    ): MemberSpec =
        MemberSpec(
            role = role,
            capacity = capacity,
            maxInput = maxInput,
            maxOutput = maxOutput,
            minRank = minRank,
            maxRank = maxRank,
            priority = priority,
            lossPerMillion = lossPerMillion,
        )

    private class RecordingSink : EnergyEventSink {
        val overloads = ArrayList<Pair<Long, Int>>()
        val underGrades = ArrayList<Long>()
        val brownouts = ArrayList<Brownout>()

        override fun overload(
            id: Long,
            excessRank: Int,
        ) {
            overloads.add(id to excessRank)
        }

        override fun underGrade(id: Long) {
            underGrades.add(id)
        }

        override fun brownout(
            networkId: Int,
            satisfiedFraction: Double,
        ) {
            brownouts.add(Brownout(networkId, satisfiedFraction))
        }
    }

    private data class Brownout(
        val networkId: Int,
        val satisfiedFraction: Double,
    )
}
