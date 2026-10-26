package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class GrizzlecogVisitLogicTest {
    @Test
    fun `visit chance scales with elapsed game time and caps at certainty`() {
        val interval = WorldTimeResource.NANOS_PER_DAY * 2

        assertEquals(0.0, GrizzlecogVisitLogic.chanceFromElapsed(0, 2.0))
        assertEquals(0.5, GrizzlecogVisitLogic.chanceFromElapsed(interval / 2, 2.0))
        assertEquals(1.0, GrizzlecogVisitLogic.chanceFromElapsed(interval * 2, 2.0))
        assertEquals(0.0, GrizzlecogVisitLogic.chanceFromElapsed(-1, 2.0))
    }

    @Test
    fun `nearby grizzlecogs exclude players while active clear players remain eligible`() {
        val nearby = GrizzlecogVisitCandidate(UUID.randomUUID(), 0.0, 0.0, 0.0)
        val inactive = GrizzlecogVisitCandidate(UUID.randomUUID(), 100.0, 0.0, 0.0, areaActive = false)
        val eligible = GrizzlecogVisitCandidate(UUID.randomUUID(), 200.0, 0.0, 0.0)
        val player =
            GrizzlecogVisitLogic.eligiblePlayer(
                listOf(nearby, inactive, eligible),
                listOf(GrizzlecogVisitPosition(8.0, 0.0, 0.0)),
                nearbyRadius = 64,
            )

        assertSame(eligible, player)
    }

    @Test
    fun `expired visit records retry or drop according to loaded state and age`() {
        val day = WorldTimeResource.NANOS_PER_DAY
        val record = GrizzlecogVisitRecord("npc", 0L)

        assertEquals(GrizzlecogVisitExpiry.KEEP, GrizzlecogVisitLogic.expiryDecision(day / 2, record, true))
        assertEquals(GrizzlecogVisitExpiry.DESPAWN_AND_DROP, GrizzlecogVisitLogic.expiryDecision(day * 2, record, true))
        assertEquals(GrizzlecogVisitExpiry.KEEP, GrizzlecogVisitLogic.expiryDecision(day * 2, record, false))
        assertEquals(GrizzlecogVisitExpiry.DROP, GrizzlecogVisitLogic.expiryDecision(day * 8, record, false))
    }

    @Test
    fun `young visits do not require an NPC lookup`() {
        val day = WorldTimeResource.NANOS_PER_DAY
        val record = GrizzlecogVisitRecord("npc", 0L)

        assertFalse(GrizzlecogVisitLogic.needsNpcLookup(day - 1, record))
        assertTrue(GrizzlecogVisitLogic.needsNpcLookup(day, record))
    }

    @Test
    fun `expiry scan is throttled to one game-time second`() {
        val second = 1_000_000_000L

        assertTrue(GrizzlecogVisitLogic.shouldCheckExpiry(0, Long.MIN_VALUE))
        assertFalse(GrizzlecogVisitLogic.shouldCheckExpiry(second - 1, 0))
        assertTrue(GrizzlecogVisitLogic.shouldCheckExpiry(second, 0))
    }
}
