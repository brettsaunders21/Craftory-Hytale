package studio.craftory.tech.machine

internal object FillBand {
    fun of(
        amount: Long,
        capacity: Long,
    ): Int {
        if (amount <= 0L || capacity <= 0L) return 0
        if (amount >= capacity) return 4
        return (1 + amount * 3 / capacity).toInt().coerceIn(1, 3)
    }

    fun stateName(band: Int): String =
        when (band) {
            1 -> "Quarter"
            2 -> "Half"
            3 -> "ThreeQuarter"
            4 -> "Full"
            else -> "Empty"
        }
}
