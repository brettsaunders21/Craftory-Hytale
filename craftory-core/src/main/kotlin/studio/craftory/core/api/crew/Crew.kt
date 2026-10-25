package studio.craftory.core.api.crew

import studio.craftory.core.api.well.Temperament
import java.util.UUID

data class Crew(
    val id: UUID,
    val name: String,
    val owner: UUID,
    val members: Set<UUID>,
    val pendingInvites: Set<UUID>,
    val knowledge: Set<String>,
    val tier: Int,
    val solo: Boolean,
    val insight: Map<Temperament, Long> = emptyMap(),
)
