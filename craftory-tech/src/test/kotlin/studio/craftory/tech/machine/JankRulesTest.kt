package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.tech.machine.jank.BodgePart
import studio.craftory.tech.machine.jank.JankState
import studio.craftory.tech.machine.jank.JankTuning
import studio.craftory.tech.machine.jank.bodgeModifiers
import studio.craftory.tech.machine.jank.fizzleChance
import studio.craftory.tech.machine.jank.isRunning
import studio.craftory.tech.machine.jank.repairWithScraps
import studio.craftory.tech.machine.jank.rollFizzle
import studio.craftory.tech.machine.jank.step
import java.util.Random

class JankRulesTest {
    @Test
    fun `blocked demand is running when energy condition flags are active`() {
        assertTrue(isRunning(1, 0, 0, false, false))
        assertTrue(isRunning(0, 15, 15, false, false))
        assertTrue(isRunning(0, 15, 0, true, false))
        assertTrue(isRunning(0, 15, 0, false, true))
        assertFalse(isRunning(0, 15, 0, false, false))
    }

    @Test
    fun `running accrues base jank and modifiers`() {
        assertEquals(0.25, step(JankState(), true, false, false, bodgeModifiers(emptyList()), Random(1)).state.value)
        assertEquals(
            1.0,
            step(JankState(), true, true, true, bodgeModifiers(emptyList()), Random(1)).state.value,
        )
        assertEquals(
            0.375,
            step(
                JankState(),
                true,
                false,
                false,
                bodgeModifiers(listOf(BodgePart.BOOM_VALVE)),
                Random(1),
            ).state.value,
        )
        assertEquals(
            0.125,
            step(
                JankState(),
                true,
                false,
                false,
                bodgeModifiers(listOf(BodgePart.SCRAP_PLATING)),
                Random(1),
            ).state.value,
        )
    }

    @Test
    fun `idle decay clamps to zero and running gain clamps to maximum`() {
        assertEquals(
            0.0,
            step(JankState(0.05), false, false, false, bodgeModifiers(emptyList()), Random(1)).state.value,
        )
        assertEquals(
            100.0,
            step(JankState(100.0), true, true, true, bodgeModifiers(emptyList()), Random(1)).state.value,
        )
    }

    @Test
    fun `every bodge part and combinations produce modifiers`() {
        assertEquals(1.25, bodgeModifiers(listOf(BodgePart.BOOM_VALVE)).speed)
        assertEquals(1.5, bodgeModifiers(listOf(BodgePart.BOOM_VALVE)).jankRate)
        assertEquals(0.8, bodgeModifiers(listOf(BodgePart.GUTTER_GEAR)).energyUse)
        assertEquals(0.5, bodgeModifiers(listOf(BodgePart.SCRAP_PLATING)).jankRate)
        assertTrue(bodgeModifiers(listOf(BodgePart.SQUAWK_BOX)).squawk)
        assertTrue(bodgeModifiers(listOf(BodgePart.MUFFLER)).muffled)
        assertEquals(
            0.75,
            bodgeModifiers(listOf(BodgePart.BOOM_VALVE, BodgePart.SCRAP_PLATING)).jankRate,
        )
    }

    @Test
    fun `fizzle chance is zero below threshold and seeded rolls are deterministic`() {
        assertEquals(0.0, fizzleChance(69.99))
        assertEquals(0.02, fizzleChance(70.0))
        val firstRandom = Random(741)
        val first = List(1_000) { rollFizzle(85.0, firstRandom) }
        val secondRandom = Random(741)
        val second = List(1_000) { rollFizzle(85.0, secondRandom) }
        assertEquals(first, second)
        assertEquals(51, first.count { it })
        assertFalse(rollFizzle(69.0, Random(741)))
    }

    @Test
    fun `fizzle stall recovers at fifty after ten seconds`() {
        val fizzled =
            step(
                JankState(value = 100.0),
                true,
                false,
                false,
                bodgeModifiers(emptyList()),
                AlwaysFizzleRandom,
            )
        assertTrue(fizzled.fizzled)
        assertEquals(200, fizzled.state.stallTicks)

        var state = fizzled.state
        repeat(9) {
            state = step(state, false, false, false, bodgeModifiers(emptyList()), Random(1)).state
        }
        assertEquals(20, state.stallTicks)
        val recovered = step(state, false, false, false, bodgeModifiers(emptyList()), Random(1))
        assertTrue(recovered.recovered)
        assertEquals(50.0, recovered.state.value)
        assertEquals(0, recovered.state.stallTicks)
        assertEquals(JankTuning.FIZZLE_RECOVERY_VALUE, recovered.state.value)
    }

    @Test
    fun `scraps reduce jank by twenty without going below zero`() {
        assertEquals(25.0, repairWithScraps(45.0))
        assertEquals(0.0, repairWithScraps(10.0))
    }

    private object AlwaysFizzleRandom : java.util.random.RandomGenerator {
        override fun nextLong(): Long = 0L

        override fun nextDouble(): Double = 0.0
    }
}
