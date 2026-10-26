package studio.craftory.tech.notebook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.craftory.core.api.crew.Crew
import java.util.UUID

class NotebookGuideTest {
    @Test
    fun `tier one highlights the first missing page after informational steps`() {
        val crew = crew(tier = 1)

        assertEquals("page:${NotebookPages.pages.first().id}", NotebookGuide.nextStep(crew)?.id)
        assertTrue(NotebookGuide.stepsFor(crew).take(3).all { it.completion == null })
    }

    @Test
    fun `tier two highlights the first spark gate`() {
        val crew = crew(tier = 2)

        assertEquals("first-spark", NotebookGuide.nextStep(crew)?.id)
        assertEquals(
            "seated-flicker",
            NotebookGuide
                .nextStep(
                    crew.copy(knowledge = setOf(NotebookTierProgression.FIRST_SPARK_KNOWLEDGE)),
                )?.id,
        )
    }

    @Test
    fun `tier three has no next tier gate`() {
        val crew =
            crew(
                tier = 3,
                knowledge =
                    setOf(
                        NotebookTierProgression.FIRST_SPARK_KNOWLEDGE,
                        NotebookTierProgression.SEATED_FLICKER_KNOWLEDGE,
                    ),
            )

        assertNull(NotebookGuide.nextStep(crew))
        assertTrue(NotebookGuide.stepsFor(crew).all { it.completion == null })
    }

    private fun crew(
        tier: Int,
        knowledge: Set<String> = emptySet(),
    ): Crew {
        val owner = UUID.randomUUID()
        return Crew(UUID.randomUUID(), "Test", owner, setOf(owner), emptySet(), knowledge, tier, true)
    }
}
