package studio.craftory.tech.machine

import kotlin.math.floor

internal fun rattlewheelOutput(
    waterFlow: Int,
    windMode: Boolean,
    y: Int,
    weatherMultiplier: Double,
    bodgeSpeed: Double,
): Long {
    val rate =
        when {
            waterFlow > 0 -> waterFlow.coerceIn(0, 5) * 2.0
            windMode -> (3 + ((y - 110).coerceAtLeast(0) / 20)).coerceAtMost(8).toDouble()
            else -> 0.0
        }
    return floor(rate * weatherMultiplier.coerceAtLeast(0.0) * bodgeSpeed.coerceAtLeast(0.0)).toLong()
}

internal fun rattlewheelProductionPerSolveTick(
    fizzPerSecond: Long,
    tick: Long,
): Long {
    val solvesPerSecond = 5L
    val base = fizzPerSecond.coerceAtLeast(0) / solvesPerSecond
    val remainder = fizzPerSecond.coerceAtLeast(0) % solvesPerSecond
    return base + if (Math.floorMod(tick / 4, solvesPerSecond) < remainder) 1 else 0
}
