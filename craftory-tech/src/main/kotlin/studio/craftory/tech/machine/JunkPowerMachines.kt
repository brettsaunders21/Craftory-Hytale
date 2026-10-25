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

class CrankMachine(
    initialSpinTicks: Long = 0,
    private val pulseIntervalTicks: Long = PULSE_INTERVAL_TICKS,
    private val spinPerPulseTicks: Long = SPIN_PER_PULSE_TICKS,
) {
    var spinTicks: Long = initialSpinTicks.coerceAtLeast(0)
        private set

    private var lastPulseTick: Long? = null
    private var lastAdvancedTick: Long? = null

    init {
        require(pulseIntervalTicks > 0)
        require(spinPerPulseTicks > 0)
    }

    fun pulse(currentTick: Long): Boolean {
        val previous = lastPulseTick
        if (previous != null && (currentTick < previous || currentTick - previous < pulseIntervalTicks)) {
            return false
        }
        lastPulseTick = currentTick
        spinTicks =
            if (Long.MAX_VALUE - spinTicks < spinPerPulseTicks) {
                Long.MAX_VALUE
            } else {
                spinTicks + spinPerPulseTicks
            }
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

    fun kick(): Boolean {
        if (isBurning || burnTicks == 0L) return false
        isBurning = true
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
