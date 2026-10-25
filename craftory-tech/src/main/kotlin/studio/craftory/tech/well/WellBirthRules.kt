package studio.craftory.tech.well

import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength
import kotlin.random.Random

class WellBirthRules(
    private val random: Random = Random.Default,
) {
    fun stormcall(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun rainFlicker(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun killFlicker(chance: Double): Boolean = random.nextDouble() < chance.coerceIn(0.0, 1.0)

    fun flickerStrength(): WellStrength = if (random.nextDouble() < 0.7) WellStrength.FLICKER else WellStrength.STEADY

    fun stormTemperament(): Temperament = Temperament.entries[random.nextInt(Temperament.entries.size)]

    fun surfaceOffset(): Int = random.nextInt(8, 25)
}
