package studio.craftory.tech.notebook

import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.crew.CrewService

internal object NotebookTierProgression {
    private const val TINKERED_TIER = 2

    fun evaluateTier(
        crew: Crew,
        crews: CrewService,
    ): Crew? {
        if (crew.tier >= TINKERED_TIER || !crew.knowledge.containsAll(NotebookPages.ids)) return null
        if (!crews.setTier(crew.id, TINKERED_TIER)) return null
        return crew.copy(tier = TINKERED_TIER)
    }
}
