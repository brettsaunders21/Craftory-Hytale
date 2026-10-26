package studio.craftory.tech.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CannonChargePolicyTest {
    @Test
    fun `active due charge launches`() {
        assertEquals(
            CannonChargeDecision.LAUNCH,
            CannonChargePolicy.decide(areaActive = true, pending = true, due = true),
        )
    }

    @Test
    fun `inactive pending charge clears without launching`() {
        assertEquals(
            CannonChargeDecision.CLEAR,
            CannonChargePolicy.decide(areaActive = false, pending = true, due = true),
        )
    }
}
