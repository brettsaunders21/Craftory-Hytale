package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoilerRunningSoundCadenceTest {
    @Test
    fun `running sound cadence starts immediately then repeats every 80 ticks`() {
        assertTrue(BoilerRunningSoundCadence.isDue(100, Long.MIN_VALUE))
        assertFalse(BoilerRunningSoundCadence.isDue(179, 100))
        assertTrue(BoilerRunningSoundCadence.isDue(180, 100))
    }
}
