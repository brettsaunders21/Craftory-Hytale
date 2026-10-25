package studio.craftory.tech.notebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.craftory.core.api.crew.CrewService
import java.nio.file.Path
import java.util.UUID

class NotebookTierProgressionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `merging all notebook pages advances the destination crew`() {
        val service = CrewService(temporaryDirectory.resolve("crews.json"))
        val owner = UUID.randomUUID()
        val joiningPlayer = UUID.randomUUID()
        val knowledgeSubscription =
            service.onKnowledgeGranted { crew, _ ->
                NotebookTierProgression.evaluateTier(crew, service)
            }
        val crewSubscription =
            service.onCrewChanged { crew ->
                NotebookTierProgression.evaluateTier(crew, service)
            }
        try {
            val crew = service.create(owner, "Notebook Crew")
            NotebookPages.pages.take(6).forEach { service.grantKnowledge(owner, it.id) }
            NotebookPages.pages.drop(6).forEach { service.grantKnowledge(joiningPlayer, it.id) }

            assertEquals(1, service.tierOf(owner))
            assertEquals(1, service.tierOf(joiningPlayer))
            assertTrue(service.invite(owner, joiningPlayer))
            assertEquals(crew.id, service.accept(joiningPlayer, crew.id)?.id)
            assertEquals(2, service.tierOf(owner))
        } finally {
            crewSubscription.close()
            knowledgeSubscription.close()
            service.close()
        }
    }
}
