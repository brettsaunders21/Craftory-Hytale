package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirstSparkRulesTest {
    @Test
    fun `first spark requires a running tier two machine and a nearby source`() {
        assertTrue(FirstSparkRules.conditionMet(true, 2, false, 64))
        assertFalse(FirstSparkRules.conditionMet(false, 2, false, 0))
        assertFalse(FirstSparkRules.conditionMet(true, 1, false, 0))
        assertFalse(FirstSparkRules.conditionMet(true, 2, true, 0))
        assertFalse(FirstSparkRules.conditionMet(true, 2, false, null))
        assertFalse(FirstSparkRules.conditionMet(true, 2, false, 65))
    }
}
