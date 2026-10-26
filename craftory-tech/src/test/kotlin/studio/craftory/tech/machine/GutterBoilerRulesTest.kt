package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GutterBoilerRulesTest {
    @Test
    fun `pressure rises consumes water and produces pressurised fizz`() {
        val step = GutterBoilerRules.step(79, 100, fueled = true, lavaBelow = false, online = true)
        assertEquals(80, step.pressure)
        assertEquals(90, step.water)
        assertEquals(60, step.production)
        assertTrue(!step.popped)
    }

    @Test
    fun `vent and pop rules release pressure and stop the boiler`() {
        assertEquals(56, GutterBoilerRules.vent(96))
        val pop = GutterBoilerRules.step(99, 10, fueled = true, lavaBelow = false, online = true)
        assertEquals(0, pop.pressure)
        assertEquals(0, pop.water)
        assertTrue(pop.popped)
    }

    @Test
    fun `lava permits fuel free operation and empty water decays pressure`() {
        assertEquals(1, GutterBoilerRules.step(0, 10, false, true, true).pressure)
        assertEquals(19, GutterBoilerRules.step(20, 0, false, false, true).pressure)
    }
}
