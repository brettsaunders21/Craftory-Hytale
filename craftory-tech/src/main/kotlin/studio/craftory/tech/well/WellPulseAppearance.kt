package studio.craftory.tech.well

import com.hypixel.hytale.protocol.Color
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength
import kotlin.math.abs
import kotlin.math.sin

internal data class WellPulseParticle(
    val height: Double,
    val horizontalOffset: Double,
    val scale: Float,
)

internal object WellPulseAppearance {
    const val PARTICLE_ID = "Magic_Sparks_GS"

    fun intervalTicks(strength: WellStrength): Long =
        if (strength == WellStrength.FLICKER) FLICKER_INTERVAL_TICKS else STEADY_INTERVAL_TICKS

    fun particles(
        strength: WellStrength,
        tick: Long,
    ): List<WellPulseParticle> =
        if (strength == WellStrength.FLICKER) {
            listOf(
                WellPulseParticle(
                    height = 1.05 + sin(tick / 5.0) * 0.08,
                    horizontalOffset = sin(tick / 7.0) * 0.09,
                    scale = 0.34f + abs(sin(tick / 4.0)).toFloat() * 0.12f,
                ),
            )
        } else {
            listOf(
                WellPulseParticle(0.75, 0.0, 1.15f),
                WellPulseParticle(1.25, sin(tick / 12.0) * 0.04, 1.15f),
                WellPulseParticle(1.75, 0.0, 1.15f),
            )
        }

    fun tint(temperament: Temperament): Color {
        val hex = temperament.tint.removePrefix("#")
        return Color(
            hex.substring(0, 2).toInt(16).toByte(),
            hex.substring(2, 4).toInt(16).toByte(),
            hex.substring(4, 6).toInt(16).toByte(),
        )
    }

    private const val FLICKER_INTERVAL_TICKS = 40L
    private const val STEADY_INTERVAL_TICKS = 20L
}
