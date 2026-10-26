package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FluidSelfTestLayoutTest {
    @Test
    fun `fluid fixtures including frames avoid Gnasher fillers and other selftests`() {
        val gnasherFootprints =
            listOf(
                FluidSelfTestCases.PRESSURISED_GNASHER,
                FluidSelfTestCases.PLAIN_GNASHER,
            ).associateWith { origin ->
                buildSet {
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            if (dx != 0 || dz != 0) {
                                add(Position(origin.x + dx, origin.y, origin.z + dz))
                            }
                        }
                    }
                }
            }
        val otherFixtures =
            CraftorySelfTest.staticTestPositions().map {
                Position(it.x, it.y, it.z)
            }

        assertDoesNotThrow {
            FluidSelfTestFootprint.assertSafe(
                FluidSelfTestCases.fixturePositions(),
                gnasherFootprints,
                otherFixtures,
            )
        }
        assertTrue(PolishSelfTest.fixturesShareSection())
    }
}
