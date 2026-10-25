package studio.craftory.tech.machine

data class FuelDefinition(
    val itemId: String,
    val burnTicks: Long,
) {
    init {
        require(itemId.isNotBlank())
        require(burnTicks > 0)
    }
}

internal object MachineSaveThrottle {
    fun shouldPersist(
        unsaved: Boolean,
        currentTick: Long,
        lastSavedTick: Long,
        intervalTicks: Long,
        immediate: Boolean,
    ): Boolean = immediate || (unsaved && currentTick - lastSavedTick >= intervalTicks)
}

internal class CrankPulseLimiter(
    private val pulseIntervalTicks: Long,
) {
    private var lastPulseTick: Long? = null

    init {
        require(pulseIntervalTicks > 0)
    }

    fun pulse(currentTick: Long): Boolean {
        val previous = lastPulseTick
        if (previous != null && (currentTick < previous || currentTick - previous < pulseIntervalTicks)) {
            return false
        }
        lastPulseTick = currentTick
        return true
    }
}

class CrankMachine(
    initialSpinTicks: Long = 0,
    private val pulseIntervalTicks: Long = PULSE_INTERVAL_TICKS,
    private val spinPerPulseTicks: Long = SPIN_PER_PULSE_TICKS,
) {
    var spinTicks: Long = initialSpinTicks.coerceAtLeast(0)
        private set

    private val pulseLimiter = CrankPulseLimiter(pulseIntervalTicks)
    private var lastAdvancedTick: Long? = null

    init {
        require(spinPerPulseTicks > 0)
    }

    fun pulse(currentTick: Long): Boolean {
        if (!pulseLimiter.pulse(currentTick)) return false
        val wasIdle = spinTicks == 0L
        spinTicks =
            if (Long.MAX_VALUE - spinTicks < spinPerPulseTicks) {
                Long.MAX_VALUE
            } else {
                spinTicks + spinPerPulseTicks
            }
        if (wasIdle) lastAdvancedTick = currentTick
        return true
    }

    fun advanceTo(currentTick: Long): Boolean {
        val previous = lastAdvancedTick
        lastAdvancedTick = currentTick
        if (previous == null || currentTick <= previous || spinTicks == 0L) return false
        val remaining = (spinTicks - (currentTick - previous)).coerceAtLeast(0)
        val changed = remaining != spinTicks
        spinTicks = remaining
        return changed
    }

    fun productionPerSolveTick(): Long = if (spinTicks > 0) FIZZ_PER_SOLVE_TICK else 0

    companion object {
        const val PULSE_INTERVAL_TICKS = 10L
        const val SPIN_PER_PULSE_TICKS = 40L
        const val FIZZ_PER_SOLVE_TICK = 20L
    }
}

class BurnerMachine(
    initialBurnTicks: Long = 0,
    initiallyBurning: Boolean = false,
    fuelDefinitions: Collection<FuelDefinition>,
) {
    private val fuels = fuelDefinitions.associate { it.itemId to it.burnTicks }
    var burnTicks: Long = initialBurnTicks.coerceAtLeast(0)
        private set
    var isBurning: Boolean = initiallyBurning && burnTicks > 0
        private set

    private var lastAdvancedTick: Long? = null

    fun addFuel(itemId: String): Long {
        val addedTicks = fuels[itemId] ?: return 0
        burnTicks =
            if (Long.MAX_VALUE - burnTicks < addedTicks) {
                Long.MAX_VALUE
            } else {
                burnTicks + addedTicks
            }
        return addedTicks
    }

    fun kick(currentTick: Long): Boolean {
        if (isBurning || burnTicks == 0L) return false
        isBurning = true
        lastAdvancedTick = currentTick
        return true
    }

    fun advanceTo(currentTick: Long): Boolean {
        val previous = lastAdvancedTick
        lastAdvancedTick = currentTick
        if (previous == null || currentTick <= previous || !isBurning) return false
        val remaining = (burnTicks - (currentTick - previous)).coerceAtLeast(0)
        val changed = remaining != burnTicks
        burnTicks = remaining
        if (burnTicks == 0L) isBurning = false
        return changed
    }

    fun productionPerSolveTick(): Long = if (isBurning) FIZZ_PER_SOLVE_TICK else 0

    companion object {
        const val FIZZ_PER_SOLVE_TICK = 40L
    }
}

data class GritDefinition(
    val metal: String,
    val gritItemId: String,
    val oreItemIds: List<String>,
) {
    init {
        require(metal.isNotBlank())
        require(gritItemId.isNotBlank())
        require(oreItemIds.isNotEmpty())
    }
}

object GritTable {
    val definitions =
        listOf(
            GritDefinition(
                "Adamantite",
                "Craftory_Grit_Adamantite",
                listOf("Ore_Adamantite", "Ore_Adamantite_Magma", "Ore_Adamantite_Magma_Cracked"),
            ),
            GritDefinition(
                "Cobalt",
                "Craftory_Grit_Cobalt",
                listOf("Ore_Cobalt", "Ore_Cobalt_Shale", "Ore_Cobalt_Slate", "Ore_Cobalt_Slate_Cracked"),
            ),
            GritDefinition(
                "Copper",
                "Craftory_Grit_Copper",
                listOf("Ore_Copper", "Ore_Copper_Sandstone", "Ore_Copper_Shale", "Ore_Copper_Stone"),
            ),
            GritDefinition(
                "Gold",
                "Craftory_Grit_Gold",
                listOf(
                    "Ore_Gold",
                    "Ore_Gold_Basalt",
                    "Ore_Gold_Calcite",
                    "Ore_Gold_Sandstone",
                    "Ore_Gold_Shale",
                    "Ore_Gold_Stone",
                    "Ore_Gold_Volcanic",
                ),
            ),
            GritDefinition(
                "Iron",
                "Craftory_Grit_Iron",
                listOf(
                    "Ore_Iron",
                    "Ore_Iron_Basalt",
                    "Ore_Iron_Basalt_Cracked",
                    "Ore_Iron_Sandstone",
                    "Ore_Iron_Shale",
                    "Ore_Iron_Slate",
                    "Ore_Iron_Stone",
                    "Ore_Iron_Volcanic",
                ),
            ),
            GritDefinition("Mithril", "Craftory_Grit_Mithril", listOf("Ore_Mithril", "Ore_Mithril_Stone")),
            GritDefinition("Onyxium", "Craftory_Grit_Onyxium", listOf("Ore_Onyxium")),
            GritDefinition("Prisma", "Craftory_Grit_Prisma", listOf("Ore_Prisma")),
            GritDefinition(
                "Silver",
                "Craftory_Grit_Silver",
                listOf(
                    "Ore_Silver",
                    "Ore_Silver_Basalt",
                    "Ore_Silver_Sandstone",
                    "Ore_Silver_Shale",
                    "Ore_Silver_Slate",
                    "Ore_Silver_Stone",
                    "Ore_Silver_Volcanic",
                ),
            ),
            GritDefinition(
                "Thorium",
                "Craftory_Grit_Thorium",
                listOf("Ore_Thorium", "Ore_Thorium_Mud", "Ore_Thorium_Mud_Cracked", "Ore_Thorium_Sandstone"),
            ),
        )
    private val byOre =
        definitions
            .flatMap { definition -> definition.oreItemIds.map { it to definition.gritItemId } }
            .toMap()

    fun gritForOre(oreItemId: String): String? = byOre[oreItemId]

    fun metalForGrit(gritItemId: String): String? = definitions.firstOrNull { it.gritItemId == gritItemId }?.metal

    fun encodeQueue(oreCounts: Map<String, Int>): String =
        oreCounts.entries
            .filter { it.value > 0 }
            .joinToString(";") { (gritId, count) -> "$gritId=$count" }

    fun decodeQueue(encoded: String): Map<String, Int> =
        encoded
            .split(';')
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0) return@mapNotNull null
                val count = entry.substring(separator + 1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val gritId = entry.substring(0, separator)
                if (metalForGrit(gritId) == null) null else gritId to count
            }.toMap()
}

class GnasherPitMachine(
    initialOreCounts: Map<String, Int> = emptyMap(),
    initialProgressPulses: Int = 0,
    initiallyJammed: Boolean = false,
    private val pulseIntervalTicks: Long = CrankMachine.PULSE_INTERVAL_TICKS,
) {
    private val pulseLimiter = CrankPulseLimiter(pulseIntervalTicks)
    private val oreCounts = LinkedHashMap<String, Int>()
    var progressPulses: Int = initialProgressPulses.coerceIn(0, PULSES_PER_ORE - 1)
        private set
    var isJammed: Boolean = initiallyJammed
        private set
    var queuedOreCount: Int = 0
        private set
    private var lastAutomaticPulseTick: Long? = null

    init {
        initialOreCounts.forEach { (gritId, count) ->
            if (GritTable.metalForGrit(gritId) != null && count > 0) {
                oreCounts[gritId] = count
                queuedOreCount += count
            }
        }
        require(pulseIntervalTicks > 0)
    }

    fun deposit(oreItemId: String): Boolean {
        val gritId = GritTable.gritForOre(oreItemId) ?: return false
        if (isJammed) return false
        oreCounts[gritId] = (oreCounts[gritId] ?: 0) + 1
        queuedOreCount++
        if (queuedOreCount > HOPPER_CAPACITY) isJammed = true
        return true
    }

    fun crank(currentTick: Long): String? {
        if (isJammed || queuedOreCount == 0 || !pulseLimiter.pulse(currentTick)) return null
        return progressOnePulse()
    }

    fun autoCrank(
        currentTick: Long,
        deliveredFizz: Long,
    ): String? {
        if (deliveredFizz < FIZZ_PER_SOLVE_TICK || isJammed || queuedOreCount == 0) return null
        val previous = lastAutomaticPulseTick
        if (previous != null && currentTick - previous < AUTO_PULSE_INTERVAL_TICKS) return null
        lastAutomaticPulseTick = currentTick
        return crank(currentTick)
    }

    fun kick(): Boolean {
        if (!isJammed) return false
        isJammed = false
        return true
    }

    fun snapshot(): Map<String, Int> = oreCounts.toMap()

    private fun progressOnePulse(): String? {
        progressPulses++
        if (progressPulses < PULSES_PER_ORE) return null
        progressPulses = 0
        val first = oreCounts.entries.firstOrNull() ?: return null
        val gritId = first.key
        if (first.value == 1) oreCounts.remove(gritId) else oreCounts[gritId] = first.value - 1
        queuedOreCount--
        return gritId
    }

    companion object {
        const val HOPPER_CAPACITY = 8
        const val PULSES_PER_ORE = 6
        const val FIZZ_PER_SOLVE_TICK = 15L
        const val AUTO_PULSE_INTERVAL_TICKS = 20L
        const val GRIT_OUTPUT_QUANTITY = 2
    }
}
