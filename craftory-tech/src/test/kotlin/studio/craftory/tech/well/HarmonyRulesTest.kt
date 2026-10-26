package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HarmonyRulesTest {
    @Test
    fun `harmony scales at the specified matching block thresholds`() {
        assertEquals(1, HarmonyRules.harmony(0))
        assertEquals(2, HarmonyRules.harmony(1))
        assertEquals(2, HarmonyRules.harmony(2))
        assertEquals(3, HarmonyRules.harmony(3))
        assertEquals(3, HarmonyRules.harmony(5))
        assertEquals(4, HarmonyRules.harmony(6))
        assertEquals(4, HarmonyRules.harmony(9))
        assertEquals(5, HarmonyRules.harmony(10))
    }

    @Test
    fun `taste blocks include the scrap plating mapping`() {
        assertTrue(TasteBlocks.matches("Ingredient_Scraps", "Craftory_Scrap_Plating"))
    }
}
