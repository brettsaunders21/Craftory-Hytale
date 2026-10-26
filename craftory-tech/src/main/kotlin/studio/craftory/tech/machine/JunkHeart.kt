package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.Inspectable
import studio.craftory.core.api.multiblock.MultiblockQuality
import studio.craftory.core.api.multiblock.MultiblockState
import studio.craftory.core.api.multiblock.MultiblockTemplate
import studio.craftory.core.api.multiblock.TemplatePart
import studio.craftory.tech.CraftoryIds
import kotlin.math.abs
import kotlin.math.roundToLong

/** Junk Heart fuel slot and burn clock. Structure state lives on the Core MultiblockController. */
class JunkHeartState(
    var fuelItemId: String = "",
    var fuelCount: Int = 0,
    var burnTicks: Long = 0,
    var burning: Boolean = false,
    var lastAdvancedTick: Long = 0,
    var smokeTicks: Int = 0,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> =
        JunkHeartState(fuelItemId, fuelCount, burnTicks, burning, lastAdvancedTick, smokeTicks)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "fuelItemId" to fuelItemId,
            "fuelCount" to fuelCount.toString(),
            "burnTicks" to burnTicks.toString(),
            "burning" to burning.toString(),
        )

    companion object {
        lateinit var type: ComponentType<ChunkStore, JunkHeartState>

        val CODEC: BuilderCodec<JunkHeartState> =
            BuilderCodec
                .builder(JunkHeartState::class.java, ::JunkHeartState)
                .append(
                    KeyedCodec("FuelItemId", Codec.STRING),
                    { state, value -> state.fuelItemId = value },
                    { it.fuelItemId },
                ).add()
                .append(
                    KeyedCodec("FuelCount", Codec.INTEGER),
                    { state, value -> state.fuelCount = value },
                    { it.fuelCount },
                ).add()
                .append(
                    KeyedCodec("BurnTicks", Codec.LONG),
                    { state, value -> state.burnTicks = value },
                    { it.burnTicks },
                ).add()
                .append(KeyedCodec("Burning", Codec.BOOLEAN), { state, value -> state.burning = value }, { it.burning })
                .add()
                .build()
    }
}

internal object JunkHeartTemplate {
    val CHIMNEY_TOP = BlockPos(0, 3, 0)

    val template: MultiblockTemplate =
        MultiblockTemplate(
            CraftoryIds.JUNK_HEART_TEMPLATE,
            buildList {
                for (x in -1..1) {
                    for (z in -1..1) {
                        if (x == 0 && z == 0) continue
                        add(plating(BlockPos(x, 0, z)))
                    }
                }
                add(TemplatePart(BlockPos(0, 1, 0), setOf(CraftoryIds.CHIMNEY_BLOCK), CraftoryIds.CHIMNEY_BLOCK))
                add(TemplatePart(BlockPos(0, 2, 0), setOf(CraftoryIds.CHIMNEY_BLOCK), CraftoryIds.CHIMNEY_BLOCK))
            },
        )

    private fun plating(offset: BlockPos) =
        TemplatePart(offset, setOf(CraftoryIds.SCRAP_PLATING), CraftoryIds.SCRAP_PLATING)
}

internal object GutterBoilerTemplate {
    val WHISTLE_STACK = BlockPos(1, 1, 1)

    val template: MultiblockTemplate =
        MultiblockTemplate(
            CraftoryIds.GUTTER_BOILER_TEMPLATE,
            buildList {
                listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1)).forEach {
                    add(part(it, CraftoryIds.SCRAP_PLATING))
                }
                listOf(BlockPos(0, 1, 0), BlockPos(1, 1, 0), BlockPos(0, 1, 1)).forEach {
                    add(part(it, CraftoryIds.BOILER_TANK))
                }
                add(part(WHISTLE_STACK, CraftoryIds.BOILER_WHISTLE_STACK))
            },
        )

    private fun part(
        offset: BlockPos,
        itemId: String,
    ) = TemplatePart(offset, setOf(itemId), itemId)
}

internal object GasbagTemplate {
    val template: MultiblockTemplate =
        MultiblockTemplate(
            CraftoryIds.GASBAG_TEMPLATE,
            buildList {
                listOf(BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(1, 0, 1)).forEach {
                    add(part(it, CraftoryIds.GASBAG_FRAME))
                }
                for (y in 1..2) {
                    for (x in 0..1) {
                        for (z in 0..1) {
                            add(part(BlockPos(x, y, z), CraftoryIds.GASBAG_ENVELOPE))
                        }
                    }
                }
            },
        )

    private fun part(
        offset: BlockPos,
        itemId: String,
    ) = TemplatePart(offset, setOf(itemId), itemId)
}

internal object JunkHeartMachine {
    private val fuels = JunkPowerFuels.definitions.associate { it.itemId to it.burnTicks.toLong() }

    fun acceptsFuel(
        state: JunkHeartState,
        itemId: String,
    ): Boolean =
        itemId in fuels &&
            state.fuelCount < FUEL_SLOT_CAPACITY &&
            (state.fuelCount == 0 || state.fuelItemId == itemId)

    fun addFuel(
        state: JunkHeartState,
        itemId: String,
    ): Boolean {
        if (!acceptsFuel(state, itemId)) return false
        state.fuelItemId = itemId
        state.fuelCount += 1
        return true
    }

    /** Burns fuel from the slot while formed. A stalled heart keeps its fuel and burn time. */
    fun advanceTo(
        state: JunkHeartState,
        currentTick: Long,
        structure: MultiblockState,
    ): Boolean {
        val previous = state.lastAdvancedTick
        state.lastAdvancedTick = currentTick
        val wasBurning = state.burning
        if (structure != MultiblockState.FORMED || currentTick <= previous) {
            state.burning = false
            return wasBurning
        }
        var elapsed = currentTick - previous
        var changed = false
        while (elapsed > 0) {
            if (state.burnTicks == 0L && !takeFuel(state)) break
            val burned = minOf(elapsed, state.burnTicks)
            state.burnTicks -= burned
            elapsed -= burned
            changed = true
        }
        state.burning = state.burnTicks > 0 || state.fuelCount > 0
        return changed || wasBurning != state.burning
    }

    fun productionPerSolveTick(
        state: JunkHeartState,
        structure: MultiblockState,
        quality: MultiblockQuality,
    ): Long {
        if (structure != MultiblockState.FORMED || !state.burning) return 0
        return (BurnerMachine.FIZZ_PER_SOLVE_TICK * OUTPUT_MULTIPLIER * quality.multiplier).roundToLong()
    }

    private fun takeFuel(state: JunkHeartState): Boolean {
        if (state.fuelCount <= 0) return false
        val ticks = fuels[state.fuelItemId] ?: return false
        state.fuelCount -= 1
        state.burnTicks += ticks
        if (state.fuelCount == 0) state.fuelItemId = ""
        return true
    }

    const val OUTPUT_MULTIPLIER = 4L
    const val FUEL_SLOT_CAPACITY = 64
}

/** Kick-start gauge: a needle sweeps a 3 s cycle and the sweet spot sits at the 1.5 s mark. */
internal object KickStartTiming {
    const val CYCLE_SECONDS = 3.0
    const val SWEET_SPOT_SECONDS = 1.5
    const val MASTERWORK_WINDOW_SECONDS = 0.15
    const val STURDY_WINDOW_SECONDS = 0.5
    private const val GAUGE_WIDTH = 30

    fun phaseSeconds(
        elapsedTicks: Long,
        ticksPerSecond: Int,
    ): Double {
        val seconds = elapsedTicks.coerceAtLeast(0).toDouble() / ticksPerSecond.coerceAtLeast(1)
        return seconds % CYCLE_SECONDS
    }

    fun errorSeconds(phaseSeconds: Double): Double = abs(phaseSeconds - SWEET_SPOT_SECONDS)

    fun quality(errorSeconds: Double): MultiblockQuality =
        when {
            errorSeconds <= MASTERWORK_WINDOW_SECONDS -> MultiblockQuality.MASTERWORK
            errorSeconds <= STURDY_WINDOW_SECONDS -> MultiblockQuality.STURDY
            else -> MultiblockQuality.WOBBLY
        }

    /** A text gauge such as `[-----===|^|===-----]`, with the needle at [phaseSeconds]. */
    fun gauge(phaseSeconds: Double): String {
        val needle = ((phaseSeconds / CYCLE_SECONDS) * GAUGE_WIDTH).toInt().coerceIn(0, GAUGE_WIDTH - 1)
        val cells =
            (0 until GAUGE_WIDTH).map { cell ->
                val center = (cell + 0.5) * CYCLE_SECONDS / GAUGE_WIDTH
                val error = errorSeconds(center)
                when {
                    cell == needle -> '^'
                    error <= MASTERWORK_WINDOW_SECONDS -> '|'
                    error <= STURDY_WINDOW_SECONDS -> '='
                    else -> '-'
                }
            }
        return cells.joinToString("", prefix = "[", postfix = "]")
    }
}
