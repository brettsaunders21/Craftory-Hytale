package studio.craftory.tech.notebook

import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.crew.CrewService
import studio.craftory.tech.CraftoryIds.JUNK_HEART_KNOWLEDGE

internal object NotebookTierProgression {
    private const val TINKERED_TIER = 2

    fun evaluateTier(
        crew: Crew,
        crews: CrewService,
    ): Crew? {
        if (crew.tier >= TINKERED_TIER || !meetsTinkeredRequirements(crew.knowledge)) return null
        if (!crews.setTier(crew.id, TINKERED_TIER)) return null
        return crew.copy(tier = TINKERED_TIER)
    }

    fun meetsTinkeredRequirements(knowledge: Set<String>): Boolean =
        knowledge.containsAll(NotebookPages.ids) && JUNK_HEART_KNOWLEDGE in knowledge
}
