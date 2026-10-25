package studio.craftory.tech.well

data class ScanScore(
    val perfect: Boolean,
    val errors: List<Long?>,
)

object ScanScoring {
    private val periodScales = listOf(1.0, 1.25, 1.5)

    fun ringPeriod(
        index: Int,
        base: Long,
    ): Long {
        require(base > 0) { "Scan period must be positive" }
        val scale = periodScales.getOrNull(index) ?: error("Unknown resonance ring: $index")
        return (base * scale).toLong().coerceAtLeast(1L)
    }

    fun phaseError(
        ringStartTick: Long,
        tick: Long,
        index: Int,
        base: Long,
    ): Long {
        val period = ringPeriod(index, base)
        val phase = Math.floorMod(tick - ringStartTick, period)
        return minOf(phase, period - phase)
    }

    fun score(
        ringStartTick: Long,
        lockTicks: List<Long>,
        periodTicks: Long,
        windowTicks: Long,
    ): ScanScore {
        require(periodTicks > 0) { "Scan period must be positive" }
        require(windowTicks >= 0) { "Scan window must not be negative" }
        val errors =
            periodScales.indices.map { index ->
                val tick = lockTicks.getOrNull(index) ?: return@map null
                phaseError(ringStartTick, tick, index, periodTicks)
            }
        return ScanScore(
            perfect = errors.size == 3 && errors.all { it != null && it <= windowTicks },
            errors = errors,
        )
    }
}
