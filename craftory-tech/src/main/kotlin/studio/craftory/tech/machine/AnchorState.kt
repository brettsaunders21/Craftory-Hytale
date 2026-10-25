package studio.craftory.tech.machine

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class AnchorState(
    var chargeSeconds: Long = 0,
    var visualActive: Boolean? = null,
    var coverageActive: Boolean? = null,
    var coverageId: Long? = null,
    var drainRemainderSeconds: Float = 0f,
    val keepLoadedChunks: MutableSet<Long> = mutableSetOf(),
    var areaRefreshTicks: Int = 9,
    var drainElapsedSeconds: Float = 0f,
    var areaHasNoPlayer: Boolean = false,
    val pendingChunkLoads: MutableSet<Long> = mutableSetOf(),
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> =
        AnchorState(
            chargeSeconds,
            visualActive,
            coverageActive,
            coverageId,
            drainRemainderSeconds,
            keepLoadedChunks.toMutableSet(),
            areaRefreshTicks,
            drainElapsedSeconds,
            areaHasNoPlayer,
            pendingChunkLoads.toMutableSet(),
        )

    override fun describe(): List<Pair<String, String>> = listOf("chargeSeconds" to chargeSeconds.toString())

    companion object {
        lateinit var type: ComponentType<ChunkStore, AnchorState>

        val CODEC: BuilderCodec<AnchorState> =
            BuilderCodec
                .builder(AnchorState::class.java, ::AnchorState)
                .append(
                    KeyedCodec("ChargeSeconds", Codec.LONG),
                    { state, value -> state.chargeSeconds = value },
                    { it.chargeSeconds },
                ).add()
                .build()
    }
}

internal data class AnchorDrainResult(
    val chargeSeconds: Long,
    val remainderSeconds: Float,
)

internal object AnchorChargeMath {
    fun canCharge(maxChargeMinutes: Int): Boolean = maxChargeMinutes > 0

    fun tryAddCharge(
        currentSeconds: Long,
        maxChargeMinutes: Int,
    ): Long? {
        if (!canCharge(maxChargeMinutes)) return null
        val maximumSeconds = maxChargeMinutes.toLong() * 60L
        val next = addCharge(currentSeconds, CHARGE_ITEM_SECONDS, maximumSeconds)
        return next.takeIf { it > currentSeconds }
    }

    fun addCharge(
        currentSeconds: Long,
        addedSeconds: Long,
        maxChargeSeconds: Long,
    ): Long {
        if (maxChargeSeconds <= 0) return 0
        val current = currentSeconds.coerceIn(0, maxChargeSeconds)
        val added = addedSeconds.coerceAtLeast(0)
        return if (Long.MAX_VALUE - current < added) {
            maxChargeSeconds
        } else {
            (current + added).coerceAtMost(maxChargeSeconds)
        }
    }

    fun clamp(
        currentSeconds: Long,
        maxChargeSeconds: Long,
    ): Long = currentSeconds.coerceIn(0, maxChargeSeconds.coerceAtLeast(0))

    fun drain(
        currentSeconds: Long,
        remainderSeconds: Float,
        dtSeconds: Float,
        coveringNonTickingArea: Boolean,
    ): AnchorDrainResult {
        if (!coveringNonTickingArea || currentSeconds <= 0) {
            return AnchorDrainResult(currentSeconds.coerceAtLeast(0), remainderSeconds.coerceAtLeast(0f))
        }
        val elapsed = remainderSeconds.coerceAtLeast(0f) + dtSeconds.coerceAtLeast(0f)
        val wholeSeconds = elapsed.toLong()
        val remaining = (currentSeconds - wholeSeconds).coerceAtLeast(0)
        return AnchorDrainResult(remaining, if (remaining == 0L) 0f else elapsed - wholeSeconds)
    }

    const val CHARGE_ITEM_SECONDS = 15L * 60L
}
