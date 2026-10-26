package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength

class WellPulseAppearanceTest {
    @Test
    fun `temperament tint is applied from core api`() {
        val colors =
            Temperament.entries.map { temperament ->
                val color = WellPulseAppearance.tint(temperament)
                Triple(color.red.toInt() and 0xff, color.green.toInt() and 0xff, color.blue.toInt() and 0xff)
            }

        assertEquals(Temperament.entries.size, colors.distinct().size)
        Temperament.entries.zip(colors).forEach { (temperament, color) ->
            val hex = temperament.tint.removePrefix("#")
            assertEquals(
                Triple(
                    hex.substring(0, 2).toInt(16),
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16),
                ),
                color,
            )
        }
    }

    @Test
    fun `flicker is sparse while stronger wells share the steady column`() {
        val flicker = WellPulseAppearance.particles(WellStrength.FLICKER, 80)
        val steady = WellPulseAppearance.particles(WellStrength.STEADY, 80)

        assertEquals(40L, WellPulseAppearance.intervalTicks(WellStrength.FLICKER))
        assertEquals(20L, WellPulseAppearance.intervalTicks(WellStrength.STEADY))
        assertEquals(1, flicker.size)
        assertTrue(flicker.single().scale < steady.first().scale)
        assertEquals(3, steady.size)
        assertEquals(steady, WellPulseAppearance.particles(WellStrength.ROARING, 80))
        assertEquals(steady, WellPulseAppearance.particles(WellStrength.PRIMAL, 80))
        assertNotEquals(flicker, steady)
    }
}
