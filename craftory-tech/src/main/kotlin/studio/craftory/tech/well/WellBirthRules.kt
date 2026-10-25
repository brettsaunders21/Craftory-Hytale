package studio.craftory.tech.well

import studio.craftory.core.api.well.WellStrength
import kotlin.random.Random

class WellBirthRules(
    private val random: Random = Random.Default,
) {
    fun stormcall(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun rainFlicker(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun killFlicker(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun flickerStrength(): WellStrength = if (random.nextDouble() < 0.7) WellStrength.FLICKER else WellStrength.STEADY

    fun surfaceOffset(): Int = random.nextInt(8, 25)

    companion object {
        fun cooldownElapsed(
            last: Long?,
            now: Long,
            interval: Long,
        ): Boolean = last?.let { now - it < interval } != true
    }
}
