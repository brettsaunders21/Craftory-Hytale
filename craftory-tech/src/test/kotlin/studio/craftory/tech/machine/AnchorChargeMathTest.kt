package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnchorChargeMathTest {
    @Test
    fun `charge additions stop at the configured maximum`() {
        assertEquals(1_200L, AnchorChargeMath.addCharge(900, 900, 1_200))
    }

    @Test
    fun `lowering the maximum clamps existing charge`() {
        assertEquals(7_200L, AnchorChargeMath.clamp(18_000, 7_200))
    }

    @Test
    fun `charge drains only while covering a non-ticking area`() {
        assertEquals(
            AnchorDrainResult(120, 0f),
            AnchorChargeMath.drain(120, 0f, 1f, coveringNonTickingArea = false),
        )
        assertEquals(
            AnchorDrainResult(119, 0f),
            AnchorChargeMath.drain(120, 0f, 1f, coveringNonTickingArea = true),
        )
    }

    @Test
    fun `zero maximum rejects charging`() {
        assertNull(AnchorChargeMath.tryAddCharge(0, 0))
    }
}
