package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolishSelfTestFixtureTest {
    @Test
    fun `polish fixtures stay in the anchor section`() {
        assertTrue(PolishSelfTest.fixturesShareSection())
    }
}
