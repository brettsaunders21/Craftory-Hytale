package studio.craftory.tech.machine.jank

import java.util.random.RandomGenerator

data class JankState(
    val value: Double = 0.0,
    val stallTicks: Int = 0,
    val stolen: Boolean = false,
) {
    val isStalled: Boolean
        get() = stallTicks > 0 || stolen
}

data class JankStepResult(
    val state: JankState,
    val fizzled: Boolean = false,
    val recovered: Boolean = false,
)

object JankTuning {
    const val MIN_VALUE = 0.0
    const val MAX_VALUE = 100.0
    const val CYCLE_TICKS = 20
    const val RUNNING_GAIN = 0.25
    const val IDLE_DECAY = 0.1
    const val OVER_GRADED_MULTIPLIER = 2.0
    const val OVERLOADED_MULTIPLIER = 2.0
    const val RATTLE_THRESHOLD = 40.0
    const val GREY_SMOKE_FIZZLE_THRESHOLD = 70.0
    const val FIZZLE_BASE_CHANCE = 0.02
    const val FIZZLE_CHANCE_PER_POINT = 0.002
    const val FIZZLE_STALL_TICKS = 200
    const val FIZZLE_RECOVERY_VALUE = 50.0
    const val SCRAP_REPAIR_AMOUNT = 20.0
}

fun rollFizzle(
    value: Double,
    rng: RandomGenerator,
): Boolean {
    val chance = fizzleChance(value)
    return chance > 0.0 && rng.nextDouble() < chance
}

fun fizzleChance(value: Double): Double {
    if (value < JankTuning.GREY_SMOKE_FIZZLE_THRESHOLD) return 0.0
    return (
        JankTuning.FIZZLE_BASE_CHANCE +
            (value - JankTuning.GREY_SMOKE_FIZZLE_THRESHOLD) * JankTuning.FIZZLE_CHANCE_PER_POINT
    ).coerceIn(0.0, 1.0)
}

fun repairWithScraps(value: Double): Double =
    (value - JankTuning.SCRAP_REPAIR_AMOUNT).coerceAtLeast(JankTuning.MIN_VALUE)

fun step(
    state: JankState,
    running: Boolean,
    overGraded: Boolean,
    overloaded: Boolean,
    mods: BodgeModifiers,
    rng: RandomGenerator,
): JankStepResult {
    if (state.stolen) return JankStepResult(state)
    if (state.stallTicks > 0) {
        val remaining = (state.stallTicks - JankTuning.CYCLE_TICKS).coerceAtLeast(0)
        return if (remaining == 0) {
            JankStepResult(state.copy(value = JankTuning.FIZZLE_RECOVERY_VALUE, stallTicks = 0), recovered = true)
        } else {
            JankStepResult(state.copy(stallTicks = remaining))
        }
    }
    if (!running) {
        return JankStepResult(
            state.copy(value = (state.value - JankTuning.IDLE_DECAY).coerceAtLeast(JankTuning.MIN_VALUE)),
        )
    }

    val gain =
        JankTuning.RUNNING_GAIN *
            mods.jankRate *
            (if (overGraded) JankTuning.OVER_GRADED_MULTIPLIER else 1.0) *
            (if (overloaded) JankTuning.OVERLOADED_MULTIPLIER else 1.0)
    val value = (state.value + gain).coerceIn(JankTuning.MIN_VALUE, JankTuning.MAX_VALUE)
    val advanced = state.copy(value = value)
    return if (rollFizzle(value, rng)) {
        JankStepResult(advanced.copy(stallTicks = JankTuning.FIZZLE_STALL_TICKS), fizzled = true)
    } else {
        JankStepResult(advanced)
    }
}
