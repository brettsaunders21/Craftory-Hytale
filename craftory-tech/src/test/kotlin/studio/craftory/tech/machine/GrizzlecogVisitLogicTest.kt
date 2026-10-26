package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
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
}
