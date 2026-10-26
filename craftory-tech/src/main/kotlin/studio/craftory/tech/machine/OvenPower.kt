package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable
import kotlin.math.ceil
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
    internal var frozenProgress: Float? = null
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
            it.frozenProgress = frozenProgress
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
    val progress: Float,
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
        progress: Float,
        recipeTimeSeconds: Float?,
    ): OvenGateUpdate {
        val status =
            when {
                !activeArea -> MachineStatus.IDLE
                stalled -> MachineStatus.STALLED
                storedFizz < MIN_TICK_FIZZ -> MachineStatus.NO_POWER
                else -> null
            }
        val recipeLimit =
            recipeTimeSeconds
                ?.takeIf { it.isFinite() }
                ?.minus(COMPLETION_GUARD_SECONDS)
                ?.coerceAtLeast(0f)
        return OvenGateUpdate(
            progress =
                if (status != null && recipeLimit != null) {
                    min(progress.coerceAtLeast(0f), recipeLimit)
                } else {
                    progress.coerceAtLeast(0f)
                },
            status = status,
        )
    }

    @Suppress("CyclomaticComplexMethod")
    fun process(
        storedFizz: Long,
        previousProgress: Float?,
        currentProgress: Float,
        previousRecipeTimeSeconds: Float?,
        recipeChanged: Boolean = false,
        currentRecipeTimeSeconds: Float? = previousRecipeTimeSeconds,
        frozenProgress: Float? = null,
        gateStatus: MachineStatus? = null,
    ): OvenProcessingUpdate {
        val stored = storedFizz.coerceIn(0, OvenPower.CAPACITY)
        if (gateStatus != null) {
            val safeProgress =
                if (recipeChanged) {
                    currentRecipeTimeSeconds
                        ?.takeIf { it.isFinite() }
                        ?.minus(COMPLETION_GUARD_SECONDS)
                        ?.coerceAtLeast(0f)
                        ?.let { min(currentProgress.coerceAtLeast(0f), it) }
                        ?: currentProgress.coerceAtLeast(0f)
                } else {
                    frozenProgress ?: previousProgress ?: currentProgress
                }
            return OvenProcessingUpdate(stored, safeProgress.coerceAtLeast(0f), 0f, gateStatus)
        }
        if (previousProgress == null || !previousProgress.isFinite() || !currentProgress.isFinite()) {
            return OvenProcessingUpdate(stored, currentProgress.coerceAtLeast(0f), 0f, MachineStatus.IDLE)
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
            return OvenProcessingUpdate(stored, currentProgress.coerceAtLeast(0f), 0f, MachineStatus.IDLE)
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
    const val MIN_TICK_FIZZ = 6L
    const val COMPLETION_GUARD_SECONDS = 0.25f

    private const val COST_EPSILON = 1e-4
}
