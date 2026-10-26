package studio.craftory.tech.machine

import kotlin.math.roundToInt

private const val DARK_LIGHT_THRESHOLD = 4

internal fun resolveDarkness(
    skyLight: Int?,
    blockLight: Int?,
    isNight: Boolean,
    isCovered: Boolean,
): Boolean =
    if (skyLight != null && blockLight != null) {
        skyLight < DARK_LIGHT_THRESHOLD && blockLight < DARK_LIGHT_THRESHOLD
    } else {
        isNight || isCovered
    }

internal fun shroomGrowth(
    dark: Boolean,
    density: Int,
): Double = if (dark) 10.0 * (1.0 + 0.15 * density.coerceIn(0, 8)) else 0.0

internal fun twitchDelta(
    lightOrDay: Boolean,
    playerWithin2Blocks: Boolean,
    density: Int,
): Int {
    if (!lightOrDay && !playerWithin2Blocks) return -3
    val stimulus = (if (lightOrDay) 5 else 0) + (if (playerWithin2Blocks) 10 else 0)
    return (stimulus * (1.0 + 0.25 * density.coerceIn(0, 8))).roundToInt()
}

internal fun shouldPop(
    twitch: Int,
    roll: Double,
): Boolean {
    val value = twitch.coerceIn(0, 100)
    return value >= 80 && roll < (value - 80) / 100.0
}
