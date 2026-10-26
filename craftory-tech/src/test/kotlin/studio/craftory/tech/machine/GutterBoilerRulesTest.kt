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

    @Test
    fun `boiler production depends on current pressure and active state`() {
        assertTrue(GutterBoilerRules.production(85, 4_000, true, false, true) > 0)
        assertEquals(0, GutterBoilerRules.production(85, 4_000, true, false, false))
        assertEquals(0, GutterBoilerRules.production(85, 0, true, false, true))
    }

    @Test
    fun `high pressure whistle is throttled to once every three seconds`() {
        assertTrue(GutterBoilerRules.isWhistleDue(0, Long.MIN_VALUE))
        assertTrue(!GutterBoilerRules.isWhistleDue(40, 0))
        assertTrue(GutterBoilerRules.isWhistleDue(60, 0))
        assertTrue(GutterBoilerRules.isWhistleDue(120, 60))
    }

    @Test
    fun `pop knockback points radially away with upward force`() {
        val away = GutterBoilerRules.popKnockback(1.0, 2.0, 4.0, 6.0)
        assertTrue(away.x > 0.0)
        assertTrue(away.z > 0.0)
        assertEquals(GutterBoilerRules.KNOCKBACK_UPWARD, away.y)
        assertEquals(
            GutterBoilerRules.KNOCKBACK_FORCE,
            kotlin.math.sqrt(away.x * away.x + away.z * away.z),
            0.0001,
        )
    }

    @Test
    fun `vent and pop actions share state transitions with re-riveting`() {
        val boiler = BoilerState(pressure = 97, water = 1_000, fuelTicks = 100)
        assertTrue(GutterBoilerRules.vent(boiler))
        assertEquals(57, boiler.pressure)

        boiler.pressure = 99
        val pop = GutterBoilerActions.advance(boiler, lavaBelow = false)
        assertTrue(pop.popped)
        assertEquals(0, boiler.pressure)
        assertEquals(0L, boiler.water)
        assertEquals(0L, boiler.fuelTicks)
        assertTrue(!boiler.online)
        assertTrue(GutterBoilerRules.rerivet(boiler, 2))
        assertTrue(boiler.online)
    }
}
