package studio.craftory.core.api.energy

object ConverterMath {
    fun skim(
        sourceProduction: Long,
        percent: Int,
    ): Long =
        studio.craftory.engine.energy.ConverterMath
            .skim(sourceProduction, percent)

    fun convert(
        delivered: Long,
        ratePercent: Int,
    ): Long =
        studio.craftory.engine.energy.ConverterMath
            .convert(delivered, ratePercent)

    fun catalyse(
        delivered: Long,
        lossPercent: Int = 15,
    ): Long =
        studio.craftory.engine.energy.ConverterMath
            .catalyse(delivered, lossPercent)
}
