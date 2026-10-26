package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockAnimationTimingTest {
    @Test
    fun `animation holds through its duration and then returns to idle`() {
        val untilTick = BlockAnimationTiming.holdUntil(100, TimedBlockAnimation.HOLD_TICKS)

        assertEquals(110L, untilTick)
        assertEquals("Grab", BlockAnimationTiming.stateAt(100, untilTick, "Off", "Grab"))
        assertEquals("Grab", BlockAnimationTiming.stateAt(109, untilTick, "Off", "Grab"))
        assertEquals("Off", BlockAnimationTiming.stateAt(110, untilTick, "Off", "Grab"))
    }

    @Test
    fun `cannon charge window can end before the standard animation hold`() {
        val untilTick = BlockAnimationTiming.holdUntil(100, 5)

        assertEquals("Charge", BlockAnimationTiming.stateAt(104, untilTick, "Off", "Charge"))
        assertEquals("Off", BlockAnimationTiming.stateAt(105, untilTick, "Off", "Charge"))
    }
}
