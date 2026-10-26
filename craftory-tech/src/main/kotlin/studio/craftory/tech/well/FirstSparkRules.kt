package studio.craftory.tech.well

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class FirstSparkPendingSpawns {
    private val crewIds = ConcurrentHashMap.newKeySet<UUID>()

    fun begin(crewId: UUID): Boolean = crewIds.add(crewId)

    fun finish(crewId: UUID) {
        crewIds.remove(crewId)
    }
}

internal object FirstSparkRules {
    enum class Action {
        NONE,
        GRANT_AND_SPAWN,
        RETRY_VESSA,
    }

    fun action(
        alreadyKnown: Boolean,
        vessaMet: Boolean,
        conditionMet: Boolean,
    ): Action =
        when {
            !conditionMet || (alreadyKnown && vessaMet) -> Action.NONE
            alreadyKnown -> Action.RETRY_VESSA
            else -> Action.GRANT_AND_SPAWN
        }

    fun conditionMet(
        machineRunning: Boolean,
        ownerCrewTier: Int,
        alreadyKnown: Boolean,
        nearestSourceDistSq: Long?,
    ): Boolean =
        machineRunning &&
            ownerCrewTier >= 2 &&
            !alreadyKnown &&
            nearestSourceDistSq != null &&
            nearestSourceDistSq <= MAX_DISTANCE_SQUARED

    private const val MAX_DISTANCE_SQUARED = 64L
}
