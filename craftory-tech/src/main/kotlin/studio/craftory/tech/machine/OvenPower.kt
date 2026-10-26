package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class OvenPower(
    storedFizz: Long = 0,
    var lastFizzSolveTick: Long = Long.MIN_VALUE,
) : Component<ChunkStore>,
    Inspectable {
    var storedFizz: Long = storedFizz.coerceIn(0, CAPACITY)
        set(value) {
            field = value.coerceIn(0, CAPACITY)
        }

    var lastInputProgress: Float? = null
    var lastRecipeId: String? = null
    var lastRecipeTimeSeconds: Float? = null
    var status: MachineStatus = MachineStatus.IDLE
    var receivedFizzFromNetwork: Boolean = false
    internal var tickStartProgress: Float? = null
    internal var tickRecipeId: String? = null
    internal var tickRecipeTimeSeconds: Float? = null
    internal var recipeChangedBeforeTick: Boolean = false
    internal var gateStatus: MachineStatus? = null

    override fun clone(): Component<ChunkStore> =
        OvenPower(storedFizz, lastFizzSolveTick).also {
            it.lastInputProgress = lastInputProgress
            it.lastRecipeId = lastRecipeId
            it.lastRecipeTimeSeconds = lastRecipeTimeSeconds
            it.status = status
            it.receivedFizzFromNetwork = receivedFizzFromNetwork
            it.tickStartProgress = tickStartProgress
            it.tickRecipeId = tickRecipeId
            it.tickRecipeTimeSeconds = tickRecipeTimeSeconds
            it.recipeChangedBeforeTick = recipeChangedBeforeTick
            it.gateStatus = gateStatus
        }

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "storedFizz" to storedFizz.toString(),
        )

    companion object {
        const val CAPACITY = 600L

        lateinit var type: ComponentType<ChunkStore, OvenPower>

        val CODEC: BuilderCodec<OvenPower> =
            BuilderCodec
                .builder(OvenPower::class.java, ::OvenPower)
                .append(
                    KeyedCodec("AccumulatedFizz", Codec.LONG),
                    { power, value -> power.storedFizz = value },
                    { it.storedFizz },
                ).add()
                .build()
    }
}

internal data class OvenFizzUpdate(
    val demand: Long,
    val storedFizz: Long,
    val lastFizzSolveTick: Long,
)

internal data class OvenProcessingUpdate(
    val storedFizz: Long,
    val inputProgress: Float,
    val deltaSeconds: Float,
    val status: MachineStatus,
) {
    val advanced: Boolean
        get() = status == MachineStatus.RUNNING
}

internal data class OvenGateUpdate(
    val allowedGameElapsedMillis: Long,
    val effectiveDtSeconds: Float,
    val status: MachineStatus?,
)

internal object OvenFizzAccounting {
    fun update(
        storedFizz: Long,
        lastFizzSolveTick: Long,
        completedSolveTick: Long,
        deliveredFizz: Long,
        energyUse: Double,
    ): OvenFizzUpdate {
        val currentStored = storedFizz.coerceIn(0, OvenPower.CAPACITY)
        val demand =
            if (currentStored < OvenPower.CAPACITY) {
                (FIZZ_PER_SOLVE_TICK * energyUse).toLong().coerceAtLeast(0)
            } else {
                0L
            }
        val completed =
            completedSolveTick != Long.MIN_VALUE &&
                completedSolveTick != lastFizzSolveTick
        val amount = if (completed) deliveredFizz.coerceAtLeast(0) else 0
        val nextStored =
            currentStored + amount.coerceAtMost(OvenPower.CAPACITY - currentStored)
        return OvenFizzUpdate(
            demand = demand,
            storedFizz = nextStored,
            lastFizzSolveTick = if (completed) completedSolveTick else lastFizzSolveTick,
        )
    }

    fun gate(
        storedFizz: Long,
        activeArea: Boolean,
        stalled: Boolean,
        pendingGameElapsedMillis: Long,
        secondsPerTick: Double,
    ): OvenGateUpdate {
        val allowedGameElapsedMillis =
            affordableGameElapsedMillis(
                storedFizz,
                activeArea,
                stalled,
                pendingGameElapsedMillis,
                secondsPerTick,
            )
        val status =
            gateStatus(
                storedFizz,
                activeArea,
                stalled,
                pendingGameElapsedMillis,
                allowedGameElapsedMillis,
            )
        return OvenGateUpdate(
            allowedGameElapsedMillis = allowedGameElapsedMillis,
            effectiveDtSeconds = effectiveDtSeconds(allowedGameElapsedMillis, secondsPerTick),
            status = status,
        )
    }

    private fun gateStatus(
        storedFizz: Long,
        activeArea: Boolean,
        stalled: Boolean,
        pendingGameElapsedMillis: Long,
        allowedGameElapsedMillis: Long,
    ): MachineStatus? =
        when {
            !activeArea -> MachineStatus.IDLE
            stalled -> MachineStatus.STALLED
            storedFizz <= 0 -> MachineStatus.NO_POWER
            pendingGameElapsedMillis > 0 && allowedGameElapsedMillis == 0L -> MachineStatus.NO_POWER
            else -> null
        }

    private fun affordableGameElapsedMillis(
        storedFizz: Long,
        activeArea: Boolean,
        stalled: Boolean,
        pendingGameElapsedMillis: Long,
        secondsPerTick: Double,
    ): Long {
        if (!activeArea || stalled || storedFizz <= 0) {
            return 0
        }
        if (!secondsPerTick.isFinite() || secondsPerTick <= 0.0) {
            return 0
        }
        val affordableMillis =
            floor((storedFizz.toDouble() / FIZZ_PER_PROCESS_SECOND) * secondsPerTick * 1000.0)
                .toLong()
                .coerceAtLeast(0)
        var allowedMillis = min(pendingGameElapsedMillis.coerceAtLeast(0), affordableMillis)
        while (
            allowedMillis > 0 &&
            requiredFizz(allowedMillis, secondsPerTick) > storedFizz
        ) {
            allowedMillis -= 1
        }
        return allowedMillis
    }

    private fun effectiveDtSeconds(
        gameElapsedMillis: Long,
        secondsPerTick: Double,
    ): Float =
        if (secondsPerTick.isFinite() && secondsPerTick > 0.0) {
            ((gameElapsedMillis.toFloat() / 1000f).toDouble() / secondsPerTick).toFloat()
        } else {
            0f
        }

    private fun requiredFizz(
        gameElapsedMillis: Long,
        secondsPerTick: Double,
    ): Long =
        ceil(effectiveDtSeconds(gameElapsedMillis, secondsPerTick).toDouble() * FIZZ_PER_PROCESS_SECOND - COST_EPSILON)
            .toLong()
            .coerceAtLeast(0)

    @Suppress("CyclomaticComplexMethod")
    fun process(
        storedFizz: Long,
        previousProgress: Float?,
        currentProgress: Float,
        previousRecipeTimeSeconds: Float?,
        recipeChanged: Boolean = false,
        gateStatus: MachineStatus? = null,
    ): OvenProcessingUpdate {
        val stored = storedFizz.coerceIn(0, OvenPower.CAPACITY)
        if (previousProgress == null || !previousProgress.isFinite() || !currentProgress.isFinite()) {
            return OvenProcessingUpdate(
                stored,
                currentProgress.coerceAtLeast(0f),
                0f,
                gateStatus ?: MachineStatus.IDLE,
            )
        }

        val startProgress = if (recipeChanged) 0f else previousProgress
        val delta =
            if (recipeChanged || currentProgress >= startProgress) {
                currentProgress - startProgress
            } else {
                val recipeTime =
                    previousRecipeTimeSeconds
                        ?.takeIf { it.isFinite() && it > startProgress }
                        ?: return OvenProcessingUpdate(
                            stored,
                            startProgress.coerceAtLeast(0f),
                            0f,
                            MachineStatus.IDLE,
                        )
                recipeTime - startProgress + currentProgress
            }
        if (delta <= 0f) {
            return OvenProcessingUpdate(
                stored,
                currentProgress.coerceAtLeast(0f),
                0f,
                gateStatus ?: MachineStatus.IDLE,
            )
        }

        val cost = delta.toDouble() * FIZZ_PER_PROCESS_SECOND
        val required = ceil(cost - COST_EPSILON).toLong().coerceAtLeast(0)
        return OvenProcessingUpdate(
            storedFizz = (stored - required).coerceAtLeast(0),
            inputProgress = currentProgress.coerceAtLeast(0f),
            deltaSeconds = delta,
            status = MachineStatus.RUNNING,
        )
    }

    const val FIZZ_PER_SOLVE_TICK = 30L
    const val FIZZ_PER_PROCESS_SECOND = 60L

    private const val COST_EPSILON = 1e-4
}
