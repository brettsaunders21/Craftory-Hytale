package studio.craftory.tech.notebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.craftory.core.api.crew.CrewService
import java.nio.file.Path
import java.util.UUID

class SparkboundTierProgressionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `both spark discoveries advance a tinkered crew to sparkbound`() {
        val service = CrewService(temporaryDirectory.resolve("crews.json"))
        try {
            val player = UUID.randomUUID()
            val crew = service.crewOf(player)
            service.setTier(crew.id, 2)
            val requirements =
                setOf(
                    NotebookTierProgression.FIRST_SPARK_KNOWLEDGE,
                    NotebookTierProgression.SEATED_FLICKER_KNOWLEDGE,
                )
            assertNull(NotebookTierProgression.evaluateTier(crew.copy(tier = 2), service))
            assertTrue(NotebookTierProgression.meetsSparkboundRequirements(requirements))
            val upgraded = NotebookTierProgression.evaluateTier(crew.copy(tier = 2, knowledge = requirements), service)
            assertEquals(3, upgraded?.tier)
            assertEquals(3, service.tierOf(player))
        } finally {
            service.close()
        }
    }
}
