package studio.craftory.tech.notebook

import studio.craftory.core.api.crew.Crew
import studio.craftory.tech.CraftoryIds

data class NotebookGuideStep(
    val id: String,
    val titleKey: String,
    val hintKey: String,
    val completion: Boolean?,
    val isNext: Boolean = false,
)

object NotebookGuide {
    private data class Requirement(
        val id: String,
        val titleKey: String,
        val hintKey: String,
        val knowledgeId: String? = null,
    )

    private val tierOneIntro =
        listOf(
            Requirement("crew", "server.craftory.notebook.guide.crew", "server.craftory.notebook.guide.crew-hint"),
            Requirement(
                "starter-kit",
                "server.craftory.notebook.guide.starter-kit",
                "server.craftory.notebook.guide.starter-kit-hint",
            ),
            Requirement("power", "server.craftory.notebook.guide.power", "server.craftory.notebook.guide.power-hint"),
        )

    private val tierTwoRequirements =
        listOf(
            Requirement(
                "first-spark",
                "server.craftory.notebook.guide.first-spark",
                "server.craftory.notebook.guide.first-spark-hint",
                NotebookTierProgression.FIRST_SPARK_KNOWLEDGE,
            ),
            Requirement(
                "seated-flicker",
                "server.craftory.notebook.guide.seated-flicker",
                "server.craftory.notebook.guide.seated-flicker-hint",
                NotebookTierProgression.SEATED_FLICKER_KNOWLEDGE,
            ),
        )

    fun stepsFor(crew: Crew): List<NotebookGuideStep> {
        val requirements =
            when (crew.tier.coerceIn(1, 3)) {
                1 -> {
                    tierOneIntro +
                        NotebookPages.pages.map { page ->
                            Requirement(
                                "page:${page.id}",
                                page.nameKey,
                                page.sourceKey,
                                page.id,
                            )
                        } +
                        Requirement(
                            "junk-heart",
                            "server.craftory.notebook.guide.junk-heart",
                            "server.craftory.notebook.guide.junk-heart-hint",
                            CraftoryIds.JUNK_HEART_KNOWLEDGE,
                        ) +
                        Requirement(
                            "light-heart",
                            "server.craftory.notebook.guide.light-heart",
                            "server.craftory.notebook.guide.light-heart-hint",
                        )
                }

                2 -> {
                    tierTwoRequirements
                }

                else -> {
                    listOf(
                        Requirement(
                            "tier-complete",
                            "server.craftory.notebook.guide.sparkbound",
                            "server.craftory.notebook.guide.sparkbound-hint",
                        ),
                    )
                }
            }

        val steps =
            requirements.map { requirement ->
                NotebookGuideStep(
                    id = requirement.id,
                    titleKey = requirement.titleKey,
                    hintKey = requirement.hintKey,
                    completion = requirement.knowledgeId?.let(crew.knowledge::contains),
                )
            }
        val nextIndex = steps.indexOfFirst { it.completion == false }
        return steps.mapIndexed { index, step -> step.copy(isNext = index == nextIndex) }
    }

    fun nextStep(crew: Crew): NotebookGuideStep? = stepsFor(crew).firstOrNull(NotebookGuideStep::isNext)

    fun tierTitleKey(tier: Int): String =
        when (tier.coerceIn(1, 3)) {
            1 -> "server.craftory.notebook.tier.scrapjank"
            2 -> "server.craftory.notebook.tier.tinkered"
            else -> "server.craftory.notebook.tier.sparkbound"
        }

    fun tierGuideKey(tier: Int): String =
        when (tier.coerceIn(1, 3)) {
            1 -> "server.craftory.notebook.guide.tier-one"
            2 -> "server.craftory.notebook.guide.tier-two"
            else -> "server.craftory.notebook.guide.tier-three"
        }
}
