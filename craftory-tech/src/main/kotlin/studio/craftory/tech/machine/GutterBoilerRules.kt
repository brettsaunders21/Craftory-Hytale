package studio.craftory.tech.machine

data class BoilerStep(
    val pressure: Int,
    val water: Long,
    val production: Long,
    val popped: Boolean,
)

object GutterBoilerRules {
    fun step(
        pressure: Int,
        water: Long,
        fueled: Boolean,
        lavaBelow: Boolean,
        online: Boolean,
    ): BoilerStep {
        if (!online) return BoilerStep(pressure.coerceIn(0, MAX_PRESSURE), water.coerceIn(0, CAPACITY), 0, false)
        val active = water > 0 && (fueled || lavaBelow)
        val nextPressure = if (active) pressure + 1 else pressure - 1
        if (nextPressure >= MAX_PRESSURE) return BoilerStep(0, 0, 0, true)
        val nextWater = if (active) (water - WATER_PER_SECOND).coerceAtLeast(0) else water
        val output =
            if (!active) {
                0
            } else {
                val base = BASE_OUTPUT * nextPressure / MAX_PRESSURE
                if (nextPressure in BOOST_MIN_PRESSURE..BOOST_MAX_PRESSURE) base * BOOST_PERCENT / 100 else base
            }
        return BoilerStep(nextPressure.coerceIn(0, MAX_PRESSURE), nextWater.coerceIn(0, CAPACITY), output, false)
    }

    fun vent(pressure: Int): Int? = if (pressure > VENT_THRESHOLD) (pressure - VENT_DROP).coerceAtLeast(0) else null

    const val CAPACITY = 4_000L
    const val WATER_PER_SECOND = 10L
    const val MAX_PRESSURE = 100
    const val VENT_THRESHOLD = 95
    const val VENT_DROP = 40
    const val BASE_OUTPUT = 60L
    const val BOOST_MIN_PRESSURE = 80
    const val BOOST_MAX_PRESSURE = 95
    const val BOOST_PERCENT = 125
}
