package studio.craftory.tech.well

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.entities.NPCEntity
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import java.util.UUID

internal class WellAfflictionDeathSystem : DeathSystems.OnDeathSystem() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(DeathComponent.getComponentType(), NPCEntity.getComponentType()!!, WellAfflictionFollower.type)

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val follower = commandBuffer.getComponent(ref, WellAfflictionFollower.type) ?: return
        val position = BlockPos.unpack(follower.wellPosition)
        val world = store.externalData.world
        val well = CraftoryCore.wells().wellsNear(world.chunkStore.store, position, 0).firstOrNull() ?: return
        val uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType())?.uuid?.toString() ?: return
        runCatching { UUID.fromString(uuid) }.getOrNull()?.let {
            WellAfflictionSystem.onFollowerDeath(world, it, position, follower.kind)
        }
        val affliction = world.chunkStore.store.getComponent(well.ref, WellAffliction.type) ?: return
        when (follower.kind) {
            WellAfflictionFollower.MOTH -> {
                affliction.mothIds = affliction.mothIds.filterNot { it == uuid }.toTypedArray()
            }

            WellAfflictionFollower.VOID -> {
                affliction.voidIds = affliction.voidIds.filterNot { it == uuid }.toTypedArray()
            }

            WellAfflictionFollower.POACHER, WellAfflictionFollower.WALKER -> {
                affliction.raidNpcIds = affliction.raidNpcIds.filterNot { it == uuid }.toTypedArray()
                if (follower.kind == WellAfflictionFollower.WALKER) {
                    affliction.walkerId = ""
                    affliction.raidStartedTick = world.tick
                }
                if (affliction.raidNpcIds.isEmpty() && affliction.walkerId.isEmpty()) {
                    WellAfflictionSystem.dropRaidRewards(world, position)
                    affliction.raidStartedTick = 0
                }
            }
        }
        world.chunkStore.store
            .getComponent(well.ref, BlockModule.BlockStateInfo.getComponentType())
            ?.markNeedsSaving(world.chunkStore.store)
    }
}
