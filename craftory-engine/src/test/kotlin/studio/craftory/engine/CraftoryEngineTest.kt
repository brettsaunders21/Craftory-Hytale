package studio.craftory.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CraftoryEngineTest {
    @Test
    fun `exposes its module name`() {
        assertEquals("craftory-engine", CraftoryEngine.NAME)
    }
}
