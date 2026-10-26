package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class OvenPower(
    var accumulatedFizz: Long = 0,
    var lastFizzSolveTick: Long = Long.MIN_VALUE,
    var lastDeliveredTick: Long? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = OvenPower(accumulatedFizz, lastFizzSolveTick, lastDeliveredTick)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "accumulatedFizz" to accumulatedFizz.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, OvenPower>

        val CODEC: BuilderCodec<OvenPower> =
            BuilderCodec
                .builder(OvenPower::class.java, ::OvenPower)
                .append(
                    KeyedCodec("AccumulatedFizz", Codec.LONG),
                    { power, value -> power.accumulatedFizz = value },
                    { it.accumulatedFizz },
                ).add()
                .build()
    }
}

internal data class OvenFizzUpdate(
    val demand: Long,
    val accumulatedFizz: Long,
    val lastFizzSolveTick: Long,
    val shouldInsertCharge: Boolean,
)

internal object OvenFizzAccounting {
    fun update(
        accumulatedFizz: Long,
        lastFizzSolveTick: Long,
        completedSolveTick: Long,
        deliveredFizz: Long,
        deliveredRank: Int = TinkeredSpeed.PRESSURISED_RANK,
        hasRecipeInput: Boolean,
        chargeCount: Int,
        fuelSlotAvailable: Boolean,
    ): OvenFizzUpdate {
        val demand =
            if (hasRecipeInput && chargeCount < MAX_CHARGES && fuelSlotAvailable) {
                FIZZ_PER_SOLVE_TICK * TinkeredSpeed.percent(deliveredRank) / 100
            } else {
                0L
            }
        var nextAccumulatedFizz = accumulatedFizz
        var nextLastFizzSolveTick = lastFizzSolveTick
        if (completedSolveTick != Long.MIN_VALUE && completedSolveTick != lastFizzSolveTick) {
            val delivered = deliveredFizz.coerceAtLeast(0)
            nextAccumulatedFizz =
                if (nextAccumulatedFizz > Long.MAX_VALUE - delivered) {
                    Long.MAX_VALUE
                } else {
                    nextAccumulatedFizz + delivered
                }
            nextLastFizzSolveTick = completedSolveTick
        }
        return OvenFizzUpdate(
            demand,
            nextAccumulatedFizz,
            nextLastFizzSolveTick,
            nextAccumulatedFizz >= FIZZ_PER_CHARGE && chargeCount < MAX_CHARGES && fuelSlotAvailable,
        )
    }

    const val FIZZ_PER_SOLVE_TICK = 30L
    const val FIZZ_PER_CHARGE = 120L
    const val MAX_CHARGES = 2
}

internal object OvenPowerMath {
    fun updatePowerLoss(
        lastDeliveredTick: Long?,
        currentTick: Long,
        hasDelivery: Boolean,
    ): OvenPowerLossUpdate {
        val nextLastDeliveredTick =
            if (hasDelivery) {
                currentTick
            } else {
                lastDeliveredTick ?: currentTick
            }
        return OvenPowerLossUpdate(
            nextLastDeliveredTick,
            !hasDelivery && shouldStopAfterPowerLoss(nextLastDeliveredTick, currentTick),
        )
    }

    fun shouldStopAfterPowerLoss(
        lastDeliveredTick: Long?,
        currentTick: Long,
    ): Boolean {
        val lossStartTick = lastDeliveredTick ?: currentTick
        return currentTick >= lossStartTick &&
            currentTick - lossStartTick >= POWER_LOSS_TIMEOUT_TICKS
    }

    const val POWER_LOSS_TIMEOUT_TICKS = 120L
}

internal data class OvenPowerLossUpdate(
    val lastDeliveredTick: Long,
    val shouldStop: Boolean,
)
