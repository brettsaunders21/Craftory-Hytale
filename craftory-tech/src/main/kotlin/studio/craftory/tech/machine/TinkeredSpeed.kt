package studio.craftory.tech.machine

object TinkeredSpeed {
    fun percent(deliveredRank: Int): Int = if (deliveredRank >= PRESSURISED_RANK) 100 else 50

    const val PRESSURISED_RANK = 1
}
