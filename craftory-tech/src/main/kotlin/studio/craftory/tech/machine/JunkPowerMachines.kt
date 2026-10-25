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
