package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.universe.PlayerRef
import studio.craftory.core.api.crew.CrewService
import java.util.UUID

internal fun mayUseMachine(
    ownerUuid: UUID?,
    playerUuid: UUID,
    playerRef: PlayerRef?,
    crews: CrewService,
): Boolean {
    if (ownerUuid == playerUuid) return true
    if (ownerUuid == null) {
        return playerRef?.hasPermission("hytale.command.craftory.wrench.any") == true
    }
    if (playerRef?.hasPermission("hytale.command.craftory.wrench.any") == true) return true
    return ownerUuid in crews.crewOf(playerUuid).members
}
