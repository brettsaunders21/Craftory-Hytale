package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OvenPowerMathTest {
    @Test
    fun `stops after six seconds without delivered power`() {
        assertFalse(OvenPowerMath.shouldStopAfterPowerLoss(null, 120))
        assertFalse(OvenPowerMath.shouldStopAfterPowerLoss(100, 219))
        assertTrue(OvenPowerMath.shouldStopAfterPowerLoss(100, 220))
    }

    @Test
    fun `does not stop for a tick before the last delivery`() {
        assertFalse(OvenPowerMath.shouldStopAfterPowerLoss(100, 99))
    }
}
