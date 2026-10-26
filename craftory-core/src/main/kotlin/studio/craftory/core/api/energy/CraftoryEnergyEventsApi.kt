package studio.craftory.core.api.energy

import studio.craftory.core.energy.CraftoryEnergyEvent
import studio.craftory.core.energy.CraftoryEnergyEvents
import studio.craftory.core.energy.OverloadEvent
import studio.craftory.core.energy.UnderGradeEvent
import java.util.UUID

enum class MachineEnergyCondition {
    OVER_GRADED,
    OVERLOADED,
}

data class MachineEnergyConditionEvent(
    val memberId: Long,
    val condition: MachineEnergyCondition,
    val worldUuid: UUID,
)

fun interface MachineEnergyConditionListener {
    fun onCondition(event: MachineEnergyConditionEvent)
}

object CraftoryEnergyEventsApi {
    fun subscribe(listener: MachineEnergyConditionListener): AutoCloseable =
        CraftoryEnergyEvents.subscribeWithWorld { worldUuid, event ->
            event.toMachineCondition(worldUuid)?.let(listener::onCondition)
        }

    private fun CraftoryEnergyEvent.toMachineCondition(worldUuid: UUID): MachineEnergyConditionEvent? =
        when (this) {
            is UnderGradeEvent -> MachineEnergyConditionEvent(memberId, MachineEnergyCondition.OVER_GRADED, worldUuid)
            is OverloadEvent -> MachineEnergyConditionEvent(memberId, MachineEnergyCondition.OVERLOADED, worldUuid)
            else -> null
        }
}
