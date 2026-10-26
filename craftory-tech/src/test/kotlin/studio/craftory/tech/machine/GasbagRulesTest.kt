package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GasbagRulesTest {
    @Test
    fun `gasbag loses one percent of capacity per second and patch stops leaking`() {
        assertEquals(4_000, GasbagRules.leakAmount(400_000))
        val state = GasbagState(leaking = true)
        assertTrue(GasbagRules.patch(state))
        assertFalse(state.leaking)
        assertFalse(GasbagRules.patch(state))
    }
}
