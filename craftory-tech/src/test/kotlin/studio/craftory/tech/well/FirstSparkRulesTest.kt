package studio.craftory.tech.well

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

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

    @Test
    fun `known first spark retries only the Vessa spawn until she is met`() {
        assertEquals(FirstSparkRules.Action.GRANT_AND_SPAWN, FirstSparkRules.action(false, false, true))
        assertEquals(FirstSparkRules.Action.RETRY_VESSA, FirstSparkRules.action(true, false, true))
        assertEquals(FirstSparkRules.Action.NONE, FirstSparkRules.action(true, true, true))
        assertEquals(FirstSparkRules.Action.NONE, FirstSparkRules.action(true, false, false))
    }

    @Test
    fun `a pending Vessa spawn is shared by owners in the same crew`() {
        val crewId = UUID.randomUUID()
        val pending = FirstSparkPendingSpawns()

        assertTrue(pending.begin(crewId))
        assertFalse(pending.begin(crewId))
        pending.finish(crewId)
        assertTrue(pending.begin(crewId))
    }
}
