package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CradleSystem
import studio.craftory.tech.machine.WellheadState
import java.util.UUID

internal class WellheadPlacementSystem :
    EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent,
    ) {
        if (event.isCancelled || event.itemInHand?.itemId != CraftoryIds.WELLHEAD) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val placerCrewId = CraftoryCore.crewService().crewOf(player.uuid).id
        val override = player.hasPermission(OVERRIDE_PERMISSION)
        val position = Vector3i(event.targetBlock)
        val wellPosition = BlockPos(position.x, position.y - 1, position.z)
        val world = store.externalData.world
        val well =
            CraftoryCore
                .wells()
                .wellsNear(world.chunkStore.store, wellPosition, 0)
                .firstOrNull()
                ?.well
        if (!isValidTarget(well, placerCrewId, override)) {
            event.isCancelled = true
            player.sendMessage(Message.translation("server.craftory.wellhead.invalid-well"))
            return
        }
        world.execute { initializeWellhead(world, BlockPos(position.x, position.y, position.z), wellPosition) }
    }

    private fun isValidTarget(
        well: Glimmerwell?,
        placerCrewId: UUID,
        override: Boolean,
    ): Boolean =
        well != null &&
            well.state == WellState.TETHERED &&
            (well.claimCrewId == placerCrewId || override)

    private fun initializeWellhead(
        world: World,
        position: BlockPos,
        wellPosition: BlockPos,
    ) {
        val liveWell =
            CraftoryCore
                .wells()
                .wellsNear(world.chunkStore.store, wellPosition, 0)
                .firstOrNull()
                ?.well
                ?: return
        if (liveWell.state != WellState.TETHERED) return
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
        val store = world.chunkStore.store
        val state = store.getComponent(ref, WellheadState.type) ?: return
        state.wellPosition = wellPosition.pack()
        state.harmony = CradleSystem.harmonyAt(store, position, liveWell.taste)
        state.lastHarmonyTick = world.tick
        store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        CraftoryCore.wells().setState(world, wellPosition, WellState.TAPPED)
    }

    internal fun initializeForSelfTest(
        world: World,
        position: BlockPos,
        wellPosition: BlockPos,
    ) = initializeWellhead(world, position, wellPosition)

    private companion object {
        const val OVERRIDE_PERMISSION = "hytale.command.craftory.wrench.any"
    }
}

internal class WellheadBreakSystem : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent,
    ) {
        if (event.isCancelled || event.blockType.id != CraftoryIds.WELLHEAD) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val position = event.targetBlock
        val world = store.externalData.world
        val wellPosition = wellPosition(world, position.x, position.y, position.z)
        if (!mayOperate(world, wellPosition, player)) {
            event.isCancelled = true
            player.sendMessage(Message.translation("server.craftory.wellhead.foreign-crew"))
            return
        }
        if (wellPosition != null) {
            val well =
                CraftoryCore
                    .wells()
                    .wellsNear(world.chunkStore.store, wellPosition, 0)
                    .firstOrNull()
                    ?.well
            val state = well?.let { TetherStakeRules.afterWellheadBreak(it.state) }
            if (state != null) CraftoryCore.wells().setState(world, wellPosition, state)
        }
    }
}

internal class WellheadDamageSystem :
    EntityEventSystem<EntityStore, DamageBlockEvent>(DamageBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: DamageBlockEvent,
    ) {
        if (event.isCancelled || event.blockType.id != CraftoryIds.WELLHEAD) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val position = event.targetBlock
        if (!mayOperate(
                store.externalData.world,
                wellPosition(store.externalData.world, position.x, position.y, position.z),
                player,
            )
        ) {
            event.isCancelled = true
            player.sendMessage(Message.translation("server.craftory.wellhead.foreign-crew"))
        }
    }
}

private fun wellPosition(
    world: World,
    x: Int,
    y: Int,
    z: Int,
): BlockPos? {
    val ref = BlockModule.getBlockEntity(world, x, y, z) ?: return null
    val state = world.chunkStore.store.getComponent(ref, WellheadState.type) ?: return null
    return state.wellPosition
        .takeIf { it != WellheadState.NO_WELL }
        ?.let { BlockPos.unpack(it) }
}

private fun mayOperate(
    world: World,
    wellPosition: BlockPos?,
    player: PlayerRef,
): Boolean {
    if (player.hasPermission("hytale.command.craftory.wrench.any")) return true
    val position = wellPosition ?: return false
    val well =
        CraftoryCore
            .wells()
            .wellsNear(world.chunkStore.store, position, 0)
            .firstOrNull()
            ?.well ?: return false
    return well.claimCrewId == CraftoryCore.crewService().crewOf(player.uuid).id
}
