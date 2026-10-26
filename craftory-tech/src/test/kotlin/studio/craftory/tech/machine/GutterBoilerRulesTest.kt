package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.tech.CraftoryIds

class GutterBoilerRulesTest {
    @Test
    fun `base block id resolves variant to its base`() {
        assertEquals(
            "Craftory_Gutter_Boiler",
            baseBlockId("Craftory_Gutter_Boiler_State_Definitions_Offline", "Craftory_Gutter_Boiler"),
        )
        assertEquals("Variant_Block", baseBlockId("Variant_Block", null))
    }

    @Test
    fun `boiler actions accept water up to capacity`() {
        val boiler = BoilerState(water = 3_500)
        assertEquals(300, GutterBoilerActions.acceptWater(boiler, "Water", 300))
        assertEquals(3_800, boiler.water)
        assertEquals(200, GutterBoilerActions.acceptWater(boiler, "Water_Source", 1_000))
        assertEquals(4_000, boiler.water)
        assertEquals(0, GutterBoilerActions.acceptWater(boiler, "Lava_Source", 100))
        assertEquals(4_000, boiler.water)
    }

    @Test
    fun `boiler actions resolve and add supported fuel`() {
        assertEquals(200, GutterBoilerActions.fuelBurnTicks(CraftoryIds.FIZZ_CHARGE))
        assertEquals(200, GutterBoilerActions.fuelBurnTicks("Plant_Crop_Mushroom_Common_Brown"))
        assertEquals(0, GutterBoilerActions.fuelBurnTicks("Unknown_Fuel"))

        val boiler = BoilerState(fuelTicks = Long.MAX_VALUE - 100)
        assertEquals(100, GutterBoilerActions.addFuel(boiler, CraftoryIds.FIZZ_CHARGE))
        assertEquals(Long.MAX_VALUE, boiler.fuelTicks)
        assertEquals(0, GutterBoilerActions.addFuel(boiler, "Unknown_Fuel"))
    }

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
        assertTrue(GutterBoilerRules.isWhistleDue(100, 100_000))
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
