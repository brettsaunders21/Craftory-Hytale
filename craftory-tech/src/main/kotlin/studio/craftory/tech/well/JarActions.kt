package studio.craftory.tech.well

import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import java.util.UUID

sealed interface JarCatchResult {
    data object NotWell : JarCatchResult

    data object TooBig : JarCatchResult

    data object TierLocked : JarCatchResult

    data class Caught(
        val temperament: Temperament,
        val strength: WellStrength,
        val taste: String,
    ) : JarCatchResult
}

object JarActions {
    const val FIRST_FLICKER_ID = "craftory:flicker/first"

    fun filledJar(caught: JarCatchResult.Caught): ItemStack =
        ItemStack(CraftoryIds.GOB_JAR_FILLED, 1)
            .withMetadata(GobJarMetadata.TEMPERAMENT, caught.temperament.id)
            .withMetadata(GobJarMetadata.STRENGTH, caught.strength.name)
            .withMetadata(GobJarMetadata.TASTE, caught.taste)

    fun catchFlicker(
        world: World,
        ref: Ref<ChunkStore>,
        pos: BlockPos,
        playerUuid: UUID,
        crews: CrewService,
        store: ComponentAccessor<ChunkStore>,
    ): JarCatchResult {
        val well = store.getComponent(ref, Glimmerwell.type) ?: return JarCatchResult.NotWell
        if (well.strength != WellStrength.FLICKER) return JarCatchResult.TooBig
        val crew = crews.crewOf(playerUuid)
        if (crew.tier < 2) return JarCatchResult.TierLocked
        val caught = JarCatchResult.Caught(well.temperament, well.strength, well.taste)
        if (!CraftoryCore.wells().remove(world, pos)) return JarCatchResult.NotWell
        crews.addInsight(crew.id, well.temperament, 15)
        crews.grantKnowledge(playerUuid, FIRST_FLICKER_ID)
        return caught
    }
}
