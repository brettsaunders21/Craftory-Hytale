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

internal object CrankMachine {
    fun pulse(
        state: CrankState,
        currentTick: Long,
        pulseIntervalTicks: Long = PULSE_INTERVAL_TICKS,
        spinPerPulseTicks: Long = SPIN_PER_PULSE_TICKS,
    ): Boolean {
        val previous = state.lastPulseTick
        if (previous != null && (currentTick < previous || currentTick - previous < pulseIntervalTicks)) return false
        val wasIdle = state.spinTicks == 0L
        state.lastPulseTick = currentTick
        state.spinTicks =
            if (Long.MAX_VALUE - state.spinTicks < spinPerPulseTicks) {
                Long.MAX_VALUE
            } else {
                state.spinTicks + spinPerPulseTicks
            }
        if (wasIdle) state.lastAdvancedTick = currentTick
        return true
    }

    fun advanceTo(
        state: CrankState,
        currentTick: Long,
    ): Boolean {
        if (state.spinTicks == 0L) return false
        val previous = state.lastAdvancedTick
        if (currentTick <= previous) return false
        state.lastAdvancedTick = currentTick
        val remaining = (state.spinTicks - (currentTick - previous)).coerceAtLeast(0)
        val changed = remaining != state.spinTicks
        state.spinTicks = remaining
        return changed
    }

    fun productionPerSolveTick(state: CrankState): Long = if (state.spinTicks > 0) FIZZ_PER_SOLVE_TICK else 0

    const val PULSE_INTERVAL_TICKS = 10L
    const val SPIN_PER_PULSE_TICKS = 40L
    const val FIZZ_PER_SOLVE_TICK = 20L
}

internal object BurnerMachine {
    private val fuels = JunkPowerFuels.definitions.associate { it.itemId to it.burnTicks }

    fun acceptsFuel(itemId: String): Boolean = itemId in fuels

    fun addFuel(
        state: BurnerState,
        itemId: String,
    ): Long {
        val addedTicks = fuels[itemId] ?: return 0
        state.burnTicks =
            if (Long.MAX_VALUE - state.burnTicks < addedTicks) {
                Long.MAX_VALUE
            } else {
                state.burnTicks + addedTicks
            }
        return addedTicks
    }

    fun kick(
        state: BurnerState,
        currentTick: Long,
    ): Boolean {
        if (state.burning || state.burnTicks == 0L) return false
        state.burning = true
        state.lastAdvancedTick = currentTick
        return true
    }

    fun advanceTo(
        state: BurnerState,
        currentTick: Long,
    ): Boolean {
        if (!state.burning) return false
        val previous = state.lastAdvancedTick
        if (currentTick <= previous) return false
        state.lastAdvancedTick = currentTick
        val remaining = (state.burnTicks - (currentTick - previous)).coerceAtLeast(0)
        val changed = remaining != state.burnTicks
        state.burnTicks = remaining
        if (state.burnTicks == 0L) state.burning = false
        return changed
    }

    fun productionPerSolveTick(state: BurnerState): Long = if (state.burning) FIZZ_PER_SOLVE_TICK else 0

    const val FIZZ_PER_SOLVE_TICK = 40L
}

internal object JunkPowerFuels {
    val definitions =
        listOf(
            FuelDefinition("Plant_Crop_Mushroom_Common_Brown", 200),
            FuelDefinition("Plant_Crop_Mushroom_Common_Blue", 200),
            FuelDefinition("Plant_Crop_Mushroom_Common_Lime", 200),
            FuelDefinition("Plant_Crop_Mushroom_Flatcap_Blue", 200),
            FuelDefinition("Plant_Crop_Mushroom_Flatcap_Green", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Blue", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Green", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Orange", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Purple", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Red", 200),
            FuelDefinition("Plant_Crop_Mushroom_Glowing_Violet", 200),
            FuelDefinition("Ingredient_Scraps", 40),
        )
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
    private val gritIds = definitions.map { it.gritItemId }.toSet()

    fun gritForOre(oreItemId: String): String? = byOre[oreItemId]

    fun metalForGrit(gritItemId: String): String? = definitions.firstOrNull { it.gritItemId == gritItemId }?.metal

    fun encodeOreQueue(oreItems: List<String>): String =
        oreItems
            .filter { it in byOre }
            .joinToString(",")

    fun decodeOreQueue(encoded: String): List<String> =
        encoded
            .split(',')
            .filter { it in byOre }

    fun queuedOreCount(encoded: String): Int = decodeOreQueue(encoded).size

    fun containsGrit(gritItemId: String): Boolean = gritItemId in gritIds
}

data class GnasherPulseResult(
    val accepted: Boolean,
    val outputItemId: String? = null,
    val consumedOreItemId: String? = null,
)

internal object GnasherMachine {
    fun canDeposit(
        state: GnasherState,
        oreItemId: String,
    ): Boolean = !state.jammed && GritTable.gritForOre(oreItemId) != null

    fun deposit(
        state: GnasherState,
        oreItemId: String,
    ): Boolean {
        if (!canDeposit(state, oreItemId)) return false
        val oreItems = GritTable.decodeOreQueue(state.oreQueue).toMutableList()
        oreItems.add(oreItemId)
        state.oreQueue = GritTable.encodeOreQueue(oreItems)
        if (GritTable.queuedOreCount(state.oreQueue) > HOPPER_CAPACITY) state.jammed = true
        return true
    }

    fun pulse(
        state: GnasherState,
        currentTick: Long,
    ): GnasherPulseResult {
        val previous = state.lastPulseTick
        if (previous != null && (currentTick < previous || currentTick - previous < MANUAL_PULSE_INTERVAL_TICKS)) {
            return GnasherPulseResult(false)
        }
        if (state.jammed || GritTable.queuedOreCount(state.oreQueue) == 0) return GnasherPulseResult(false)
        state.lastPulseTick = currentTick
        return advanceGrinding(state)
    }

    fun autoCrank(
        state: GnasherState,
        currentTick: Long,
        deliveredFizz: Long,
        speed: Double = 1.0,
        requiredDelivery: Long = FIZZ_PER_SOLVE_TICK,
    ): GnasherPulseResult {
        if (state.jammed || GritTable.queuedOreCount(state.oreQueue) == 0 || deliveredFizz < requiredDelivery) {
            return GnasherPulseResult(false)
        }
        val previous = state.lastAutoCrankTick
        val pulseInterval = (AUTO_PULSE_INTERVAL_TICKS / speed).toLong().coerceAtLeast(1L)
        if (
            previous != Long.MIN_VALUE &&
            (currentTick < previous || currentTick - previous < pulseInterval)
        ) {
            return GnasherPulseResult(false)
        }
        state.lastAutoCrankTick = currentTick
        return advanceGrinding(state)
    }

    fun kick(state: GnasherState): Boolean {
        if (!state.jammed) return false
        state.jammed = false
        return true
    }

    fun restoreFailedDrop(
        state: GnasherState,
        result: GnasherPulseResult,
    ) {
        val consumedOre = result.consumedOreItemId ?: return
        val oreItems = GritTable.decodeOreQueue(state.oreQueue).toMutableList()
        oreItems.add(0, consumedOre)
        state.oreQueue = GritTable.encodeOreQueue(oreItems)
        state.progressPulses = PULSES_PER_ORE - 1
    }

    private fun advanceGrinding(state: GnasherState): GnasherPulseResult {
        val oreItems = GritTable.decodeOreQueue(state.oreQueue).toMutableList()
        val oreItemId = oreItems.firstOrNull() ?: return GnasherPulseResult(false)
        state.progressPulses++
        if (state.progressPulses < PULSES_PER_ORE) return GnasherPulseResult(true)
        state.progressPulses = 0
        oreItems.removeAt(0)
        state.oreQueue = GritTable.encodeOreQueue(oreItems)
        return GnasherPulseResult(true, GritTable.gritForOre(oreItemId), oreItemId)
    }

    const val HOPPER_CAPACITY = 8
    const val PULSES_PER_ORE = 6
    const val GRIT_OUTPUT_QUANTITY = 2
    const val FIZZ_PER_SOLVE_TICK = 15L
    const val MANUAL_PULSE_INTERVAL_TICKS = 10L
    const val AUTO_PULSE_INTERVAL_TICKS = 20L
}
