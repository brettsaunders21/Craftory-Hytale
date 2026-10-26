package studio.craftory.tech.well

import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import kotlin.math.abs
import kotlin.random.Random

enum class WeatherKind {
    CLEAR,
    RAIN,
    STORM,
    OTHER,
}

@Suppress("TooManyFunctions")
object WellCaptureRules {
    fun weatherKind(weatherId: String): WeatherKind {
        val id = weatherId.lowercase()
        return when {
            "thunder" in id || "storm" in id -> WeatherKind.STORM
            "rain" in id -> WeatherKind.RAIN
            "clear" in id || id.isBlank() -> WeatherKind.CLEAR
            else -> WeatherKind.OTHER
        }
    }

    fun dislikesWeather(
        temperament: Temperament,
        weatherKind: WeatherKind,
    ): Boolean =
        when (temperament) {
            Temperament.SPARK -> weatherKind == WeatherKind.RAIN
            Temperament.EMBER -> weatherKind == WeatherKind.RAIN || weatherKind == WeatherKind.STORM
            else -> false
        }

    fun stormSteadyChance(vaneCount: Int): Double = (0.15 + vaneCount.coerceAtLeast(0) * 0.1).coerceAtMost(0.55)

    fun rollStormStrength(
        vaneCount: Int,
        random: Random,
    ): WellStrength = stormStrength(vaneCount, random.nextDouble())

    fun stormStrength(
        vaneCount: Int,
        roll: Double,
    ): WellStrength = if (roll < stormSteadyChance(vaneCount)) WellStrength.STEADY else WellStrength.FLICKER

    fun regionOf(
        x: Int,
        z: Int,
    ): Pair<Int, Int> = (x shr 9) to (z shr 9)

    fun canSpawnStrongWell(
        existingStrongWells: Int,
        budget: Int,
    ): Boolean = existingStrongWells < budget.coerceAtLeast(0)

    fun requiredStakeCount(strength: WellStrength): Int =
        when (strength) {
            WellStrength.FLICKER, WellStrength.STEADY -> 3
            else -> 4
        }

    fun stakeSpread(angles: List<Double>): Set<Int> {
        if (angles.size < 2) return angles.indices.toSet()
        val normalized = angles.map { ((it % 360.0) + 360.0) % 360.0 }
        val sorted = normalized.withIndex().sortedBy { it.value }
        val evenGap = 360.0 / angles.size
        val leaning = mutableSetOf<Int>()
        sorted.forEachIndexed { index, indexedAngle ->
            val previous = sorted[(index - 1 + sorted.size) % sorted.size].value
            val next = sorted[(index + 1) % sorted.size].value
            val before = (indexedAngle.value - previous + 360.0) % 360.0
            val after = (next - indexedAngle.value + 360.0) % 360.0
            if (abs(before - evenGap) > 40.0 || abs(after - evenGap) > 40.0) leaning += indexedAngle.index
        }
        return leaning
    }

    fun tetherComplete(
        state: WellState,
        strength: WellStrength,
        stakeCount: Int,
        leaningCount: Int,
    ): Boolean =
        state == WellState.DORMANT &&
            stakeCount >= requiredStakeCount(strength) &&
            leaningCount == 0

    fun stabilityAfter(
        stability: Double,
        elapsedSeconds: Double,
        damageInstances: Int,
        dislikedWeatherSeconds: Double,
    ): Double =
        (
            stability - elapsedSeconds.coerceAtLeast(0.0) * 0.2 - damageInstances.coerceAtLeast(0) * 10.0 -
                (dislikedWeatherSeconds.coerceAtLeast(0.0) / 10.0)
        ).coerceIn(0.0, 100.0)

    fun shouldBurst(stability: Double): Boolean = stability <= 0.0

    fun ambushRoll(
        chance: Double,
        random: Random,
    ): Boolean = chance > 0.0 && random.nextDouble() < chance.coerceIn(0.0, 1.0)
}
