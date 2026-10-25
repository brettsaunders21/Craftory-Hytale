package studio.craftory.tech.well

data class ScanScore(
    val perfect: Boolean,
    val errors: List<Long?>,
)

object ScanScoring {
    private val periodScales = listOf(1.0, 1.25, 1.5)

    fun score(
        ringStartTick: Long,
        lockTicks: List<Long>,
        periodTicks: Long,
        windowTicks: Long,
    ): ScanScore {
        require(periodTicks > 0) { "Scan period must be positive" }
        require(windowTicks >= 0) { "Scan window must not be negative" }
        val errors =
            periodScales.mapIndexed { index, scale ->
                val tick = lockTicks.getOrNull(index) ?: return@mapIndexed null
                val ringPeriod = (periodTicks * scale).toLong().coerceAtLeast(1L)
                val phase = Math.floorMod(tick - ringStartTick, ringPeriod)
                minOf(phase, ringPeriod - phase)
            }
        return ScanScore(
            perfect = errors.size == 3 && errors.all { it != null && it <= windowTicks },
            errors = errors,
        )
    }
}
