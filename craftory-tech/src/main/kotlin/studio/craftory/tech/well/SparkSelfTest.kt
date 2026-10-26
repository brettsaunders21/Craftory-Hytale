package studio.craftory.tech.well

import studio.craftory.core.api.energy.ConverterMath
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookTierProgression

internal object SparkSelfTest {
    private var finished = false

    fun run(report: (String) -> Unit) {
        if (finished) return
        finished = true
        check(HarmonyRules.harmony(0) == 1)
        check(HarmonyRules.harmony(2) == 2)
        check(HarmonyRules.harmony(3) == 3)
        check(HarmonyRules.harmony(6) == 4)
        check(HarmonyRules.harmony(10) == 5)
        check(FirstSparkRules.conditionMet(true, 2, false, 64L))
        check(!FirstSparkRules.conditionMet(true, 1, false, 0L))
        check(!FirstSparkRules.conditionMet(true, 2, true, 0L))
        check(!FirstSparkRules.conditionMet(true, 2, false, 65L))
        check(ConverterMath.skim(101, 5) == 6L)
        check(ConverterMath.convert(40, 30) == 12L)
        check(CraftoryIds.FIRST_SPARK_KNOWLEDGE == NotebookTierProgression.FIRST_SPARK_KNOWLEDGE)
        report("spark selftest: harmony, converter rounding, and First Spark gates passed")
    }
}
