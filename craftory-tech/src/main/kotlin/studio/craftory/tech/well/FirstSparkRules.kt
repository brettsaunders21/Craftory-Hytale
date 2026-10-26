package studio.craftory.tech.well

internal object FirstSparkRules {
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
