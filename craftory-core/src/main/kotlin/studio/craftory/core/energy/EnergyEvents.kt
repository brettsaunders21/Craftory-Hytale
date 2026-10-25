package studio.craftory.core.energy

import studio.craftory.engine.energy.EnergyEventSink
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

sealed interface CraftoryEnergyEvent

data class OverloadEvent(
    val memberId: Long,
    val excessRank: Int,
) : CraftoryEnergyEvent

data class UnderGradeEvent(
    val memberId: Long,
) : CraftoryEnergyEvent

data class BrownoutEvent(
    val networkId: Int,
    val satisfiedFraction: Double,
) : CraftoryEnergyEvent

fun interface CraftoryEnergyListener {
    fun onEnergyEvent(event: CraftoryEnergyEvent)
}

fun interface CraftoryWorldEnergyListener {
    fun onEnergyEvent(
        worldUuid: UUID,
        event: CraftoryEnergyEvent,
    )
}

object CraftoryEnergyEvents {
    private val listeners = CopyOnWriteArrayList<CraftoryEnergyListener>()
    private val worldListeners = CopyOnWriteArrayList<CraftoryWorldEnergyListener>()

    fun subscribe(listener: CraftoryEnergyListener): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun subscribeWithWorld(listener: CraftoryWorldEnergyListener): AutoCloseable {
        worldListeners.add(listener)
        return AutoCloseable { worldListeners.remove(listener) }
    }

    internal fun dispatch(
        worldUuid: UUID,
        event: CraftoryEnergyEvent,
    ) {
        listeners.forEach { it.onEnergyEvent(event) }
        worldListeners.forEach { it.onEnergyEvent(worldUuid, event) }
    }
}

internal class EnergyEventBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) : EnergyEventSink {
    private val eventKinds = IntArray(capacity)
    private val eventIds = LongArray(capacity)
    private val eventValues = IntArray(capacity)
    private val eventFractions = DoubleArray(capacity)
    private var readIndex = 0
    private var writeIndex = 0
    private var size = 0

    init {
        require(capacity > 0)
    }

    override fun overload(
        id: Long,
        excessRank: Int,
    ) = enqueue(OVERLOAD, id, excessRank, 0.0)

    override fun underGrade(id: Long) = enqueue(UNDER_GRADE, id, 0, 0.0)

    override fun brownout(
        networkId: Int,
        satisfiedFraction: Double,
    ) = enqueue(BROWNOUT, networkId.toLong(), 0, satisfiedFraction)

    internal fun drain(worldUuid: UUID) {
        while (size > 0) {
            val kind = eventKinds[readIndex]
            val id = eventIds[readIndex]
            val value = eventValues[readIndex]
            val fraction = eventFractions[readIndex]
            readIndex = (readIndex + 1) % capacity
            size--
            val event =
                when (kind) {
                    OVERLOAD -> OverloadEvent(id, value)
                    UNDER_GRADE -> UnderGradeEvent(id)
                    else -> BrownoutEvent(id.toInt(), fraction)
                }
            CraftoryEnergyEvents.dispatch(worldUuid, event)
        }
    }

    internal fun drain() {
        drain(UUID(0L, 0L))
    }

    private fun enqueue(
        kind: Int,
        id: Long,
        value: Int,
        fraction: Double,
    ) {
        if (size == capacity) return
        eventKinds[writeIndex] = kind
        eventIds[writeIndex] = id
        eventValues[writeIndex] = value
        eventFractions[writeIndex] = fraction
        writeIndex = (writeIndex + 1) % capacity
        size++
    }

    private companion object {
        const val DEFAULT_CAPACITY = 1024
        const val OVERLOAD = 1
        const val UNDER_GRADE = 2
        const val BROWNOUT = 3
    }
}
