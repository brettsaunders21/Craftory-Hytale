package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable
import kotlin.math.ceil

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
    var lastRecipeTimeSeconds: Float? = null
    var status: MachineStatus = MachineStatus.IDLE
    var receivedFizzFromNetwork: Boolean = false
    internal var inventorySnapshot: OvenInventorySnapshot? = null

    override fun clone(): Component<ChunkStore> =
        OvenPower(storedFizz, lastFizzSolveTick).also {
            it.lastInputProgress = lastInputProgress
            it.lastRecipeTimeSeconds = lastRecipeTimeSeconds
            it.status = status
            it.receivedFizzFromNetwork = receivedFizzFromNetwork
            it.inventorySnapshot = inventorySnapshot?.copyStacks()
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

internal data class OvenInventorySnapshot(
    val inputStacks: List<ItemStack?>,
    val outputStacks: List<ItemStack?>,
) {
    fun copyStacks(): OvenInventorySnapshot =
        OvenInventorySnapshot(
            inputStacks.map { it?.let(::copyStack) },
            outputStacks.map { it?.let(::copyStack) },
        )

    companion object {
        fun capture(bench: ProcessingBenchBlock): OvenInventorySnapshot =
            OvenInventorySnapshot(
                (0 until bench.inputContainer.capacity)
                    .map { slot -> bench.inputContainer.getItemStack(slot.toShort())?.let(::copyStack) },
                (0 until bench.outputContainer.capacity)
                    .map { slot -> bench.outputContainer.getItemStack(slot.toShort())?.let(::copyStack) },
            )

        private fun copyStack(stack: ItemStack): ItemStack = stack.withQuantity(stack.quantity) ?: stack
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

    fun process(
        storedFizz: Long,
        previousProgress: Float?,
        currentProgress: Float,
        previousRecipeTimeSeconds: Float?,
    ): OvenProcessingUpdate {
        val stored = storedFizz.coerceIn(0, OvenPower.CAPACITY)
        if (previousProgress == null || !previousProgress.isFinite() || !currentProgress.isFinite()) {
            return OvenProcessingUpdate(stored, currentProgress.coerceAtLeast(0f), 0f, MachineStatus.IDLE)
        }

        val delta =
            if (currentProgress >= previousProgress) {
                currentProgress - previousProgress
            } else {
                val recipeTime =
                    previousRecipeTimeSeconds
                        ?.takeIf { it.isFinite() && it > previousProgress }
                        ?: return OvenProcessingUpdate(
                            stored,
                            previousProgress.coerceAtLeast(0f),
                            0f,
                            MachineStatus.IDLE,
                        )
                recipeTime - previousProgress + currentProgress
            }
        if (delta <= 0f) {
            return OvenProcessingUpdate(stored, currentProgress.coerceAtLeast(0f), 0f, MachineStatus.IDLE)
        }

        val cost = delta.toDouble() * FIZZ_PER_PROCESS_SECOND
        val required = ceil(cost - COST_EPSILON).toLong().coerceAtLeast(0)
        if (stored < required) {
            return OvenProcessingUpdate(
                stored,
                previousProgress.coerceAtLeast(0f),
                delta,
                MachineStatus.NO_POWER,
            )
        }
        return OvenProcessingUpdate(
            storedFizz = stored - required,
            inputProgress = currentProgress.coerceAtLeast(0f),
            deltaSeconds = delta,
            status = MachineStatus.RUNNING,
        )
    }

    const val FIZZ_PER_SOLVE_TICK = 30L
    const val FIZZ_PER_PROCESS_SECOND = 60L

    private const val COST_EPSILON = 1e-4
}
