package studio.craftory.engine.tick

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TickSchedulerTest {
    @Test
    fun `interval phases stagger a thousand keys evenly`() {
        val scheduler = TickScheduler()
        val perTick = IntArray(20)
        repeat(1_000) { key ->
            scheduler.register(key.toLong(), 20) { currentTick ->
                perTick[currentTick.toInt()]++
            }
        }

        for (tick in 0L until 20L) {
            scheduler.tick(tick)
        }

        assertTrue(perTick.all { it in 35..65 }, perTick.joinToString())
    }

    @Test
    fun `unregister during a tick skips the target and removes it after the pass`() {
        val scheduler = TickScheduler()
        val calls = ArrayList<String>()
        lateinit var target: Handle
        scheduler.register(1L, 1) {
            calls.add("first")
            scheduler.unregister(target)
        }
        target =
            scheduler.register(2L, 1) {
                calls.add("target")
            }

        scheduler.tick(0L)
        scheduler.tick(1L)

        assertEquals(listOf("first", "first"), calls)
    }
}
