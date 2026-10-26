package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CradleState
import studio.craftory.tech.machine.CradleSystem

internal object CradleActions {
    const val SEATED_KNOWLEDGE = "craftory:well/seated"
    const val SEATED_FLICKER_KNOWLEDGE = "craftory:well/seated_flicker"
    const val STEADY_SEATED_KNOWLEDGE = "craftory:well/steady_seated"

    fun seat(
        world: World,
        cradlePosition: BlockPos,
        playerUuid: java.util.UUID,
        filledJar: ItemStack,
        crews: CrewService,
    ): Ref<ChunkStore>? {
        if (filledJar.itemId != CraftoryIds.GOB_JAR_FILLED) return null
        if (!canSeatStability(filledJar.getFromMetadataOrNull(GobJarMetadata.STABILITY))) return null
        val temperament =
            filledJar.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT)?.let(Temperament::fromId) ?: return null
        val strength =
            filledJar
                .getFromMetadataOrNull(GobJarMetadata.STRENGTH)
                ?.let { raw -> WellStrength.entries.firstOrNull { it.name.equals(raw, true) } } ?: return null
        val taste = filledJar.getFromMetadataOrNull(GobJarMetadata.TASTE) ?: return null
        val above = BlockPos(cradlePosition.x, cradlePosition.y + 1, cradlePosition.z)
        if (CradleSystem.findWell(world.chunkStore.store, above) != null) return null
        val crew = crews.crewOf(playerUuid)
        val wellRef =
            CraftoryCore.wells().spawn(
                world,
                above,
                temperament,
                strength,
                WellState.SEATED,
                taste,
                crew.id,
            ) ?: return null
        val cradleRef =
            BlockModule.getBlockEntity(world, cradlePosition.x, cradlePosition.y, cradlePosition.z)
                ?: run {
                    CraftoryCore.wells().remove(world, above)
                    return null
                }
        val store = world.chunkStore.store
        val state = store.getComponent(cradleRef, CradleState.type)
        val well = store.getComponent(wellRef, Glimmerwell.type)
        if (state == null || well == null) {
            CraftoryCore.wells().remove(world, above)
            return null
        }
        state.seatedStrength = strength.name
        state.harmony = CradleSystem.harmonyAt(store, cradlePosition, taste)
        store.getComponent(cradleRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        crews.grantKnowledge(playerUuid, SEATED_KNOWLEDGE)
        if (strength == WellStrength.FLICKER) crews.grantKnowledge(playerUuid, SEATED_FLICKER_KNOWLEDGE)
        if (strength >= WellStrength.STEADY) crews.grantKnowledge(playerUuid, STEADY_SEATED_KNOWLEDGE)
        return wellRef
    }

    internal fun canSeatStability(stability: Double?): Boolean = stability == null || stability > 0.0
}
