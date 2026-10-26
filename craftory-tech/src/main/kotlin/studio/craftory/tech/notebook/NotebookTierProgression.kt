package studio.craftory.tech.notebook

import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.crew.CrewService
import studio.craftory.tech.CraftoryIds.JUNK_HEART_KNOWLEDGE

internal object NotebookTierProgression {
    private const val TINKERED_TIER = 2
    private const val SPARKBOUND_TIER = 3
    const val FIRST_SPARK_KNOWLEDGE = "craftory:experiment/first_spark"
    const val SEATED_FLICKER_KNOWLEDGE = "craftory:well/seated_flicker"

    fun evaluateTier(
        crew: Crew,
        crews: CrewService,
    ): Crew? {
        if (crew.tier < TINKERED_TIER && meetsTinkeredRequirements(crew.knowledge)) {
            if (!crews.setTier(crew.id, TINKERED_TIER)) return null
            return crew.copy(tier = TINKERED_TIER)
        }
        if (crew.tier == TINKERED_TIER && meetsSparkboundRequirements(crew.knowledge)) {
            if (!crews.setTier(crew.id, SPARKBOUND_TIER)) return null
            return crew.copy(tier = SPARKBOUND_TIER)
        }
        return null
    }

    fun meetsTinkeredRequirements(knowledge: Set<String>): Boolean =
        knowledge.containsAll(NotebookPages.ids) && JUNK_HEART_KNOWLEDGE in knowledge

    fun meetsSparkboundRequirements(knowledge: Set<String>): Boolean =
        FIRST_SPARK_KNOWLEDGE in knowledge && SEATED_FLICKER_KNOWLEDGE in knowledge
}
