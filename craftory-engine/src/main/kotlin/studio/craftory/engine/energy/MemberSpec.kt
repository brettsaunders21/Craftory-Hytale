package studio.craftory.engine.energy

class MemberSpec(
    val role: Int,
    val capacity: Long,
    val maxInput: Long,
    val maxOutput: Long,
    val minRank: Int,
    val maxRank: Int,
    val priority: Int = 0,
    val lossPerMillion: Int = 0,
) {
    init {
        require(capacity >= 0)
        require(maxInput >= 0)
        require(maxOutput >= 0)
        require(minRank >= 0)
        require(maxRank >= minRank)
        require(lossPerMillion >= 0)
    }

    companion object {
        const val PRODUCER = 1
        const val CONSUMER = 2
        const val STORAGE = 4
        const val LINK = 8
    }
}

interface EnergyEventSink {
    fun overload(
        id: Long,
        excessRank: Int,
    )

    fun underGrade(id: Long)

    fun brownout(
        networkId: Int,
        satisfiedFraction: Double,
    )

    companion object {
        val NONE =
            object : EnergyEventSink {
                override fun overload(
                    id: Long,
                    excessRank: Int,
                ) = Unit

                override fun underGrade(id: Long) = Unit

                override fun brownout(
                    networkId: Int,
                    satisfiedFraction: Double,
                ) = Unit
            }
    }
}
