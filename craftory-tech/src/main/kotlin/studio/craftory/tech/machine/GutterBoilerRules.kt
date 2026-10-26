package studio.craftory.tech.machine

import studio.craftory.tech.CraftoryIds

data class BoilerStep(
    val pressure: Int,
    val water: Long,
    val production: Long,
    val popped: Boolean,
)

data class RadialKnockback(
    val x: Double,
    val y: Double,
    val z: Double,
)

object GutterBoilerRules {
    fun step(
        pressure: Int,
        water: Long,
        fueled: Boolean,
        lavaBelow: Boolean,
        online: Boolean,
    ): BoilerStep {
        if (!online) return BoilerStep(pressure.coerceIn(0, MAX_PRESSURE), water.coerceIn(0, CAPACITY), 0, false)
        val active = water > 0 && (fueled || lavaBelow)
        val nextPressure = if (active) pressure + 1 else pressure - 1
        if (nextPressure >= MAX_PRESSURE) return BoilerStep(0, 0, 0, true)
        val nextWater = if (active) (water - WATER_PER_SECOND).coerceAtLeast(0) else water
        val output =
            if (!active) {
                0
            } else {
                val base = BASE_OUTPUT * nextPressure / MAX_PRESSURE
                if (nextPressure in BOOST_MIN_PRESSURE..BOOST_MAX_PRESSURE) base * BOOST_PERCENT / 100 else base
            }
        return BoilerStep(nextPressure.coerceIn(0, MAX_PRESSURE), nextWater.coerceIn(0, CAPACITY), output, false)
    }

    fun vent(pressure: Int): Int? = if (pressure > VENT_THRESHOLD) (pressure - VENT_DROP).coerceAtLeast(0) else null

    fun isWhistleDue(
        tick: Long,
        lastWhistleTick: Long,
    ): Boolean =
        lastWhistleTick == Long.MIN_VALUE ||
            tick < lastWhistleTick ||
            tick - lastWhistleTick >= WHISTLE_INTERVAL_TICKS

    fun vent(state: BoilerState): Boolean {
        val pressure = vent(state.pressure) ?: return false
        state.pressure = pressure
        return true
    }

    fun rerivet(
        state: BoilerState,
        scraps: Int,
    ): Boolean {
        if (state.online || scraps < RERIVET_SCRAPS) return false
        state.online = true
        state.pressure = 0
        state.fuelTicks = 0
        return true
    }

    fun popKnockback(
        originX: Double,
        originZ: Double,
        targetX: Double,
        targetZ: Double,
    ): RadialKnockback {
        val deltaX = targetX - originX
        val deltaZ = targetZ - originZ
        val magnitude = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ)
        val directionX = if (magnitude == 0.0) 1.0 else deltaX / magnitude
        val directionZ = if (magnitude == 0.0) 0.0 else deltaZ / magnitude
        return RadialKnockback(directionX * KNOCKBACK_FORCE, KNOCKBACK_UPWARD, directionZ * KNOCKBACK_FORCE)
    }

    fun production(
        pressure: Int,
        water: Long,
        fueled: Boolean,
        lavaBelow: Boolean,
        online: Boolean,
    ): Long {
        val hasHeatSource = fueled || lavaBelow
        if (!online || water <= 0 || !hasHeatSource) return 0
        val normalizedPressure = pressure.coerceIn(0, MAX_PRESSURE)
        val base = BASE_OUTPUT * normalizedPressure / MAX_PRESSURE
        return if (normalizedPressure in BOOST_MIN_PRESSURE..BOOST_MAX_PRESSURE) {
            base * BOOST_PERCENT / 100
        } else {
            base
        }
    }

    const val CAPACITY = 4_000L
    const val WATER_PER_SECOND = 10L
    const val MAX_PRESSURE = 100
    const val VENT_THRESHOLD = 95
    const val VENT_DROP = 40
    const val BASE_OUTPUT = 60L
    const val BOOST_MIN_PRESSURE = 80
    const val BOOST_MAX_PRESSURE = 95
    const val BOOST_PERCENT = 125
    const val RERIVET_SCRAPS = 2
    const val KNOCKBACK_FORCE = 0.8
    const val KNOCKBACK_UPWARD = 0.3
    const val WHISTLE_INTERVAL_TICKS = 60L
    const val BOILER_FIZZ_CHARGE_BURN_TICKS = 200L
}

object GutterBoilerActions {
    fun acceptWater(
        state: BoilerState,
        fluidId: String,
        amount: Long,
    ): Long {
        if (fluidId != "Water_Source" && fluidId != "Water") return 0
        val accepted = minOf(amount.coerceAtLeast(0), (GutterBoilerRules.CAPACITY - state.water).coerceAtLeast(0))
        state.water += accepted
        return accepted
    }

    fun fuelBurnTicks(itemId: String): Long =
        if (itemId == CraftoryIds.FIZZ_CHARGE) {
            GutterBoilerRules.BOILER_FIZZ_CHARGE_BURN_TICKS
        } else {
            JunkPowerFuels.definitions.firstOrNull { it.itemId == itemId }?.burnTicks ?: 0L
        }

    fun addFuel(
        state: BoilerState,
        itemId: String,
    ): Long {
        val burnTicks = fuelBurnTicks(itemId)
        if (burnTicks <= 0) return 0
        val added = minOf(burnTicks, Long.MAX_VALUE - state.fuelTicks.coerceAtLeast(0))
        state.fuelTicks = (state.fuelTicks.coerceAtLeast(0) + added)
        return added
    }

    fun advance(
        state: BoilerState,
        lavaBelow: Boolean,
    ): BoilerStep {
        val step =
            GutterBoilerRules.step(
                state.pressure,
                state.water,
                state.fuelTicks > 0,
                lavaBelow,
                state.online,
            )
        state.pressure = step.pressure
        state.water = step.water
        if (step.popped) {
            state.online = false
            state.fuelTicks = 0
        } else if (state.fuelTicks > 0 && !lavaBelow) {
            state.fuelTicks = (state.fuelTicks - 20L).coerceAtLeast(0)
        }
        return step
    }
}
