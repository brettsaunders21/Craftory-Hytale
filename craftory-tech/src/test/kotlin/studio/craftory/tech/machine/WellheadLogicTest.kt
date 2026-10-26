package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.ConverterMath
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.well.matchesBlockOrState

class WellheadLogicTest {
    @Test
    fun `wellhead matcher accepts base and state variants`() {
        val variants = setOf("Craftory_Wellhead_Pumping")
        val isWellhead =
            { blockId: String ->
                matchesBlockOrState(blockId, "Craftory_Wellhead") { it in variants }
            }

        assertTrue(isWellhead("Craftory_Wellhead"))
        assertTrue(isWellhead("Craftory_Wellhead_Pumping"))
        assertFalse(isWellhead("Other_Block"))
    }

    @Test
    fun `wellhead catalyses delivered energy into dim glimmer output`() {
        val outputRank = GradeTable.DEFAULT.rankOf("dim_glimmer") ?: error("dim_glimmer grade is missing")
        assertEquals(2, outputRank)
        assertTrue(0 < outputRank)
        assertTrue(1 < outputRank)
        assertEquals(
            WellOutput.base(WellStrength.FLICKER) + ConverterMath.catalyse(40),
            WellOutput.total(WellStrength.FLICKER, 1, 40, false),
        )
    }
}
