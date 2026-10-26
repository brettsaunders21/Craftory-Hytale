package studio.craftory.tech.well

import com.hypixel.hytale.builtin.beam.AttachedBeam
import com.hypixel.hytale.builtin.beam.BeamComponent
import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.world.World
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.machine.CradleState

internal fun spawnRaid(
    world: World,
    position: BlockPos,
) {
    val poachers = spawnOnVerifiedSurfaces(world, position, WellAfflictionFollower.POACHER, 2)
    val walker = spawnOnVerifiedSurfaces(world, position, WellAfflictionFollower.WALKER, 1, 2)
    if (poachers.size != 2 || walker.size != 1) {
        despawnFollowers(world, (poachers + walker).toTypedArray())
        return
    }
    val blockRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return
    val store = world.chunkStore.store
    val affliction = store.getComponent(blockRef, WellAffliction.type) ?: return
    affliction.raidNpcIds = (poachers + walker).toTypedArray()
    affliction.walkerId = walker.single()
    affliction.raidStartedTick = world.tick
    store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    val crewId =
        CraftoryCore
            .wells()
            .wellsNear(store, position, 0)
            .firstOrNull()
            ?.well
            ?.claimCrewId
    notifyCrew(world, crewId, Message.translation("server.craftory.affliction.poachers"))
}

internal fun processRaid(
    world: World,
    position: BlockPos,
    well: Glimmerwell,
    affliction: WellAffliction,
) {
    if (affliction.raidNpcIds.isEmpty()) return
    if (affliction.walkerId.isEmpty()) {
        if (world.tick - affliction.raidStartedTick >= WALKER_DEATH_TIMEOUT_SECONDS * TICKS_PER_SECOND) {
            despawnFollowers(world, affliction.raidNpcIds)
            clearRaid(world, position, affliction)
        }
        return
    }
    val uuid = runCatching { java.util.UUID.fromString(affliction.walkerId) }.getOrNull() ?: return
    val walkerRef = world.entityStore.getRefFromUUID(uuid) ?: return
    if (!walkerRef.isValid ||
        world.entityStore.store.getComponent(walkerRef, DeathComponent.getComponentType()) != null
    ) {
        return
    }
    val transform = world.entityStore.store.getComponent(walkerRef, TransformComponent.getComponentType()) ?: return
    val distance = transform.position.distanceSquared(position.x + 0.5, position.y + 0.5, position.z + 0.5)
    if (distance > SIPHON_DISTANCE_SQUARED) {
        clearSiphonBeam(world, uuid)
        return
    }
    refreshSiphonBeam(world, position, uuid)
    val key = WellKey(world.worldConfig.uuid, position.pack())
    val elapsed = siphonSeconds.merge(key, 1, Int::plus) ?: 1
    if (elapsed >= WellAfflictionRuntimeConfig.settings.siphonSeconds) {
        val nextStrength = WellAfflictionRules.strengthAfterSiphon(well.strength)
        if (CraftoryCore.wells().setStrength(world, position, nextStrength)) {
            synchronizeCradleStrength(world, position, nextStrength)
            notifyCrew(world, well.claimCrewId, Message.translation("server.craftory.affliction.siphoned"))
        }
        despawnFollowers(world, affliction.raidNpcIds)
        clearRaid(world, position, affliction)
    }
}

private fun synchronizeCradleStrength(
    world: World,
    wellPosition: BlockPos,
    strength: WellStrength,
) {
    val cradlePosition = BlockPos(wellPosition.x, wellPosition.y - 1, wellPosition.z)
    val sectionRef =
        world.chunkStore.getChunkSectionReferenceAtBlock(
            cradlePosition.x,
            cradlePosition.y,
            cradlePosition.z,
        ) ?: return
    val cradleRef =
        BlockModule.getBlockEntity(
            world.chunkStore.store,
            sectionRef,
            cradlePosition.x,
            cradlePosition.y,
            cradlePosition.z,
        ) ?: return
    val store = world.chunkStore.store
    val state = store.getComponent(cradleRef, CradleState.type) ?: return
    if (state.seatedStrength == strength.name) return
    state.seatedStrength = strength.name
    store.getComponent(cradleRef, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
}

private fun clearRaid(
    world: World,
    position: BlockPos,
    affliction: WellAffliction,
) {
    runCatching { java.util.UUID.fromString(affliction.walkerId) }.getOrNull()?.let { clearSiphonBeam(world, it) }
    affliction.raidNpcIds = emptyArray()
    affliction.walkerId = ""
    affliction.raidStartedTick = 0
    val ref = wellRef(world, position) ?: return
    world.chunkStore.store
        .getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
        ?.markNeedsSaving(world.chunkStore.store)
    siphonSeconds.remove(WellKey(world.worldConfig.uuid, position.pack()))
}

private fun refreshSiphonBeam(
    world: World,
    position: BlockPos,
    walkerId: java.util.UUID,
) {
    if (world.tick % BEAM_REFRESH_TICKS != 0L) return
    val beamIndex = Beam.getAssetMap().getIndex(SIPHON_BEAM_ID)
    if (beamIndex < 0) return
    world.execute {
        val ref = world.entityStore.getRefFromUUID(walkerId) ?: return@execute
        val follower = world.entityStore.store.getComponent(ref, WellAfflictionFollower.type) ?: return@execute
        if (follower.kind != WellAfflictionFollower.WALKER || follower.wellPosition != position.pack()) return@execute
        val transform =
            world.entityStore.store.getComponent(ref, TransformComponent.getComponentType()) ?: return@execute
        clearSiphonBeam(world, walkerId)
        siphonBeams[walkerId] =
            BeamComponent.spawn(
                world.entityStore.store,
                Vector3d(transform.position),
                0.9f,
                AttachedBeam.toBlock(beamIndex, 1f, null, position.x, position.y, position.z),
            )
    }
}

internal fun clearSiphonBeam(
    world: World,
    walkerId: java.util.UUID,
) {
    siphonBeams.remove(walkerId)?.takeIf { it.isValid }?.let {
        world.entityStore.store.removeEntity(it, RemoveReason.REMOVE)
    }
}
