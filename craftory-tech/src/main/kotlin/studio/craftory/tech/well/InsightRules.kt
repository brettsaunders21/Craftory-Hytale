package studio.craftory.tech.well

import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength

object InsightRules {
    fun well(
        strength: WellStrength,
        firstRead: Boolean,
        perfect: Boolean,
    ): Long {
        val base = 20L * (strength.ordinal + 1)
        if (!firstRead) return repeatDecay(base)
        return if (perfect) base + base / 2 else base
    }

    fun mob(firstScan: Boolean): Long = if (firstScan) 5 else repeatDecay(5)

    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    fun machineInsight(jank: Int?): Long = 8

    fun machine(
        firstScan: Boolean,
        jank: Int?,
    ): Long = if (firstScan) machineInsight(jank) else repeatDecay(machineInsight(jank))

    fun oreCrystal(firstScan: Boolean): Long = if (firstScan) 4 else repeatDecay(4)

    fun repeatDecay(base: Long): Long = maxOf(1L, base / 10)

    fun crystalTemperament(blockId: String): Temperament =
        if (blockId.contains("crystal", ignoreCase = true)) Temperament.SPARK else Temperament.STONE

    fun mobTemperament(role: String): Temperament {
        val value = role.lowercase()
        return when {
            "goblin" in value -> Temperament.CLANK
            "void" in value || "skeleton" in value || "zombie" in value -> Temperament.HOLLOW
            "golem" in value -> Temperament.STONE
            "trork" in value -> Temperament.EMBER
            "fish" in value || "crab" in value -> Temperament.TIDE
            else -> Temperament.GROWTH
        }
    }
}
