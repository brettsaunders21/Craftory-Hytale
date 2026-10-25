package studio.craftory.core.api.energy

import studio.craftory.core.energy.CraftoryEnergyEvent
import studio.craftory.core.energy.CraftoryEnergyEvents
import studio.craftory.core.energy.OverloadEvent
import studio.craftory.core.energy.UnderGradeEvent

enum class MachineEnergyCondition {
    OVER_GRADED,
    OVERLOADED,
}

data class MachineEnergyConditionEvent(
    val memberId: Long,
    val condition: MachineEnergyCondition,
)

fun interface MachineEnergyConditionListener {
    fun onCondition(event: MachineEnergyConditionEvent)
}

object CraftoryEnergyEventsApi {
    fun subscribe(listener: MachineEnergyConditionListener): AutoCloseable =
        CraftoryEnergyEvents.subscribe { event ->
            event.toMachineCondition()?.let(listener::onCondition)
        }

    private fun CraftoryEnergyEvent.toMachineCondition(): MachineEnergyConditionEvent? =
        when (this) {
            is UnderGradeEvent -> MachineEnergyConditionEvent(memberId, MachineEnergyCondition.OVER_GRADED)
            is OverloadEvent -> MachineEnergyConditionEvent(memberId, MachineEnergyCondition.OVERLOADED)
            else -> null
        }
}
