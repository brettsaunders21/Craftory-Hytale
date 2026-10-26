package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `missing delivery timestamp starts the power loss timer`() {
        val started = OvenPowerMath.updatePowerLoss(null, 100, hasDelivery = false)
        assertEquals(100, started.lastDeliveredTick)
        assertFalse(started.shouldStop)

        val expired = OvenPowerMath.updatePowerLoss(started.lastDeliveredTick, 220, hasDelivery = false)
        assertEquals(100, expired.lastDeliveredTick)
        assertTrue(expired.shouldStop)
    }

    @Test
    fun `reloaded oven power loss timer starts at load tick`() {
        val oven = OvenPower()
        MachineClock.initialize(oven, 500)

        assertFalse(OvenPowerMath.updatePowerLoss(oven.lastDeliveredTick, 619, hasDelivery = false).shouldStop)
        assertTrue(OvenPowerMath.updatePowerLoss(oven.lastDeliveredTick, 620, hasDelivery = false).shouldStop)
    }
}
