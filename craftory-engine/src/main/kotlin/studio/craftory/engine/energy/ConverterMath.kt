package studio.craftory.engine.energy

object ConverterMath {
    fun skim(
        sourceProduction: Long,
        percent: Int,
    ): Long {
        require(percent in 0..100) { "Skim percent must be between 0 and 100" }
        if (sourceProduction <= 0L || percent == 0) return 0L
        val quotient = sourceProduction / 100
        val remainder = sourceProduction % 100
        return quotient * percent + (remainder * percent + 99) / 100
    }

    fun convert(
        delivered: Long,
        ratePercent: Int,
    ): Long {
        require(ratePercent in MIN_RATE_PERCENT..MAX_RATE_PERCENT) {
            "Conversion rate must be between $MIN_RATE_PERCENT and $MAX_RATE_PERCENT"
        }
        require(delivered >= 0L) { "Delivered energy must be non-negative" }
        val quotient = delivered / 100
        val remainder = delivered % 100
        return quotient * ratePercent + remainder * ratePercent / 100
    }

    fun catalyse(
        delivered: Long,
        lossPercent: Int = 15,
    ): Long {
        require(delivered >= 0L) { "Delivered energy must be non-negative" }
        require(lossPercent in 0..100) { "Catalysis loss must be between 0 and 100" }
        val quotient = delivered / 100
        val remainder = delivered % 100
        val loss = quotient * lossPercent + (remainder * lossPercent + 99) / 100
        return delivered - loss
    }

    private const val MIN_RATE_PERCENT = 25
    private const val MAX_RATE_PERCENT = 40
}
