package studio.craftory.tech.well

import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

enum class WeatherKind {
    CLEAR,
    RAIN,
    STORM,
    OTHER,
}

object StormHuntRules {
    private val stormRainWeatherIds =
        setOf(
            "zone1_storm",
            "zone2_thunder_storm",
            "zone4_storm",
            "zone4_swamp_storm",
        )
    private val clearWeatherIds =
        setOf(
            "zone1_sunny",
            "zone1_sunny_fireflies",
            "zone2_sunny",
            "skylands_sunny",
        )

    fun weatherKind(weatherId: String): WeatherKind {
        val id = weatherId.lowercase()
        return when {
            id in stormRainWeatherIds -> WeatherKind.STORM
            "rain" in id -> WeatherKind.RAIN
            id in clearWeatherIds || id.isBlank() -> WeatherKind.CLEAR
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

    fun strongWellCountInRegion(
        x: Int,
        z: Int,
        wellPositions: List<Pair<Int, Int>>,
    ): Int {
        val region = regionOf(x, z)
        return wellPositions.count { regionOf(it.first, it.second) == region }
    }

    fun canSpawnStrongWell(
        existingStrongWells: Int,
        budget: Int,
    ): Boolean = existingStrongWells < budget.coerceAtLeast(0)
}

object TetherRules {
    fun requiredStakeCount(strength: WellStrength): Int =
        when (strength) {
            WellStrength.FLICKER, WellStrength.STEADY -> 3
            WellStrength.ROARING -> 4
            WellStrength.PRIMAL -> 6
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

    fun capturePressIntervalValid(tickDelta: Long): Boolean = tickDelta in 4L..40L

    fun captureComplete(pressCount: Int): Boolean = pressCount >= 3
}

internal sealed interface CapturePressResult {
    data class Progress(
        val pressCount: Int,
    ) : CapturePressResult

    data object Complete : CapturePressResult

    data object Miss : CapturePressResult
}

internal fun recordCapturePress(
    state: WellCaptureState,
    playerUuid: UUID,
    wellId: String,
    tick: Long,
): CapturePressResult {
    val current = state.progressByPlayer[playerUuid]
    if (current == null || current.wellId != wellId) {
        state.progressByPlayer[playerUuid] = CaptureProgress(wellId, 1, tick)
        return CapturePressResult.Progress(1)
    }
    if (!TetherRules.capturePressIntervalValid(tick - current.lastPressTick)) {
        state.progressByPlayer.remove(playerUuid)
        return CapturePressResult.Miss
    }
    val pressCount = current.pressCount + 1
    if (TetherRules.captureComplete(pressCount)) {
        state.progressByPlayer.remove(playerUuid)
        return CapturePressResult.Complete
    }
    state.progressByPlayer[playerUuid] = CaptureProgress(wellId, pressCount, tick)
    return CapturePressResult.Progress(pressCount)
}

object JarCarrierRules {
    enum class BurstResult { RETAIN, RETRY_AT_ONE, CONSUME }

    fun slowedSpeed(originalBaseSpeed: Float): Float = originalBaseSpeed * 0.7f

    fun drainForSecond(
        stability: Double,
        temperament: Temperament,
        weatherKind: WeatherKind,
    ): Double =
        stabilityAfter(
            stability,
            1.0,
            0,
            if (StormHuntRules.dislikesWeather(temperament, weatherKind)) 1.0 else 0.0,
        )

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

    fun burstResult(
        stability: Double,
        spawnSucceeded: Boolean,
    ): BurstResult =
        when {
            stability > 0.0 -> BurstResult.RETAIN
            spawnSucceeded -> BurstResult.CONSUME
            else -> BurstResult.RETRY_AT_ONE
        }

    fun ambushRoll(
        chance: Double,
        random: Random,
    ): Boolean = chance > 0.0 && random.nextDouble() < chance.coerceIn(0.0, 1.0)
}
