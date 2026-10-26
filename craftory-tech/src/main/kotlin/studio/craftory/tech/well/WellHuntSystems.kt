package studio.craftory.tech.well

import com.hypixel.hytale.builtin.beam.AttachedBeam
import com.hypixel.hytale.builtin.beam.BeamComponent
import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import java.util.UUID
import kotlin.math.atan2

internal class StormVaneSystem : EntityTickingSystem<ChunkStore>() {
    private val position = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(StormVaneState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (WellSelfTestExclusions.isBlock(chunk.getReferenceTo(index))) return
        val state = chunk.getComponent(index, StormVaneState.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        val tick = world.tick
        if (!CraftoryCore.isAreaActive(store, blockState) || !blockState.fillWorldPos(commandBuffer, position)) return
        if (state.lastUpdateTick != Long.MIN_VALUE && tick - state.lastUpdateTick < 20L) return
        state.lastUpdateTick = tick
        val section = store.getComponent(blockState.sectionRef, EnvironmentSection.getComponentType()) ?: return
        val environment = section.get(position.x and 31, position.y and 31, position.z and 31) ?: return
        val resource = world.entityStore.store.getResource(WeatherResource.getResourceType())
        val weatherId =
            Weather
                .getAssetMap()
                .getAsset(resource.getEffectiveWeatherIndex(environment))
                ?.id
                .orEmpty()
        val on = StormHuntRules.weatherKind(weatherId) == WeatherKind.STORM
        if (state.visualOn == on) return
        state.visualOn = on
        blockState.markNeedsSaving(commandBuffer)
        val blockPosition = Vector3i(position)
        world.execute {
            BlockOperations.setBlockInteractionState(
                world.chunkStore,
                blockState.sectionRef,
                blockPosition.x,
                blockPosition.y,
                blockPosition.z,
                BlockType.fromString(CraftoryIds.STORM_VANE) ?: return@execute,
                if (on) "On" else "Off",
                true,
            )
        }
    }
}

internal class FizzDamperSystem : EntityTickingSystem<ChunkStore>() {
    private val position = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(
            FizzDamperState.type,
            EnergyMember.type,
            BlockModule.BlockStateInfo.getComponentType(),
        )

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (WellSelfTestExclusions.isBlock(chunk.getReferenceTo(index))) return
        val state = chunk.getComponent(index, FizzDamperState.type) ?: return
        val member = chunk.getComponent(index, EnergyMember.type) ?: return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val world = store.externalData.world
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val areaActive = CraftoryCore.isAreaActive(store, blockState)
        val pos = BlockPos(position.x, position.y, position.z)
        val powered = areaActive && member.delivered >= 2
        val demand = if (areaActive) 2L else 0L
        if (member.demand != demand) {
            CraftoryCore.energyAccess(world).setDemand(BlockPos.pack(position.x, position.y, position.z), demand)
        }
        if (!areaActive) return
        val tick = world.tick
        updateDamperVisual(state, blockState, powered, world, commandBuffer)
        updateFoam(state, powered, tick, pos, world)
        updateSmothering(state, powered, tick, pos, store)
    }

    private fun updateDamperVisual(
        state: FizzDamperState,
        blockState: BlockModule.BlockStateInfo,
        powered: Boolean,
        world: World,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (state.visualOn == powered) return
        state.visualOn = powered
        blockState.markNeedsSaving(commandBuffer)
        val point = Vector3i(position)
        world.execute {
            BlockOperations.setBlockInteractionState(
                world.chunkStore,
                blockState.sectionRef,
                point.x,
                point.y,
                point.z,
                BlockType.fromString(CraftoryIds.FIZZ_DAMPER) ?: return@execute,
                if (powered) "On" else "Off",
                true,
            )
        }
    }

    private fun updateFoam(
        state: FizzDamperState,
        powered: Boolean,
        tick: Long,
        pos: BlockPos,
        world: World,
    ) {
        if (!powered || (state.lastFoamTick != Long.MIN_VALUE && tick - state.lastFoamTick < 20L)) return
        state.lastFoamTick = tick
        ParticleUtil.spawnParticleEffect(
            "Dungeon_Goblin_Toxic_Water",
            Vector3d(pos.x + 0.5, pos.y + 0.8, pos.z + 0.5),
            world.entityStore.store,
        )
    }

    private fun updateSmothering(
        state: FizzDamperState,
        powered: Boolean,
        tick: Long,
        pos: BlockPos,
        store: Store<ChunkStore>,
    ) {
        if (!powered || (state.lastSmotherTick != Long.MIN_VALUE && tick - state.lastSmotherTick < 20L)) return
        state.lastSmotherTick = tick
        CraftoryCore.wells().wellsNear(store, pos, 4).forEach { near -> smotherIfReady(store, near.pos, near.well) }
    }

    internal fun smotherIfReady(
        store: Store<ChunkStore>,
        position: BlockPos,
        well: Glimmerwell,
    ) {
        if (well.state != WellState.WILD || countPoweredDampers(store, position) < 3) return
        well.state = WellState.DORMANT
        well.strength =
            when (well.strength) {
                WellStrength.PRIMAL -> WellStrength.ROARING
                WellStrength.ROARING -> WellStrength.STEADY
                WellStrength.STEADY -> WellStrength.FLICKER
                WellStrength.FLICKER -> WellStrength.FLICKER
            }
        markWellNeedsSaving(store, position)
    }

    internal fun countPoweredDampers(
        store: Store<ChunkStore>,
        center: BlockPos,
    ): Int {
        var count = 0
        for (x in center.x - 4..center.x + 4) {
            for (y in center.y - 4..center.y + 4) {
                for (z in center.z - 4..center.z + 4) {
                    if (isPoweredDamperAt(store, center, x, y, z)) count++
                }
            }
        }
        return count
    }

    private fun isPoweredDamperAt(
        store: Store<ChunkStore>,
        center: BlockPos,
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val dx = (x - center.x).toLong()
        val dy = (y - center.y).toLong()
        val dz = (z - center.z).toLong()
        if (dx * dx + dy * dy + dz * dz > 16L) return false
        val section =
            store.externalData.world.chunkStore
                .getChunkSectionReferenceAtBlock(x, y, z) ?: return false
        val ref = BlockModule.getBlockEntity(store, section, x, y, z) ?: return false
        val state = store.getComponent(ref, FizzDamperState.type) ?: return false
        val member = store.getComponent(ref, EnergyMember.type) ?: return false
        val blockState = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return false
        return CraftoryCore.isAreaActive(store, blockState) && state.visualOn && member.delivered >= 2
    }
}

internal class TetherStakePlacementSystem :
    EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent,
    ) {
        if (event.isCancelled || event.itemInHand?.itemId != CraftoryIds.TETHER_STAKE) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val position = Vector3i(event.targetBlock)
        val world = store.externalData.world
        world.execute {
            val stakeRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return@execute
            val state = world.chunkStore.store.getComponent(stakeRef, TetherStakeState.type) ?: return@execute
            val stakePos = BlockPos(position.x, position.y, position.z)
            val nearest =
                CraftoryCore
                    .wells()
                    .wellsNear(world.chunkStore.store, stakePos, 7)
                    .firstOrNull {
                        it.well.state == WellState.DORMANT &&
                            horizontalDistance(it.pos, stakePos) in 3.0..6.0
                    }
                    ?: return@execute
            state.wellId = nearest.pos.pack().toString()
            state.placerUuid = player.uuid.toString()
            val blockState =
                world.chunkStore.store.getComponent(stakeRef, BlockModule.BlockStateInfo.getComponentType())
                    ?: return@execute
            blockState.markNeedsSaving(world.chunkStore.store)
            recomputeStakeRing(world, nearest.pos, nearest.well, player.uuid)
        }
    }
}

internal class TetherStakeLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(TetherStakeState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) = Unit

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != RemoveReason.REMOVE) return
        val state = commandBuffer.getComponent(ref, TetherStakeState.type) ?: return
        val packed = state.wellId.toLongOrNull() ?: return
        val position = BlockPos.unpack(packed)
        val wellRef =
            CraftoryCore
                .wells()
                .wellsNear(store, position, 0)
                .firstOrNull()
                ?: return
        if (wellRef.well.state == WellState.TETHERED) {
            wellRef.well.state = WellState.DORMANT
            markWellNeedsSaving(store, position)
        }
    }
}

internal class TetherStakeBeamSystem : EntityTickingSystem<ChunkStore>() {
    private val position = Vector3i()

    override fun getQuery(): Query<ChunkStore> =
        Query.and(TetherStakeState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<ChunkStore>,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (WellSelfTestExclusions.isBlock(chunk.getReferenceTo(index))) return
        val world = store.externalData.world
        if (world.tick % BEAM_REFRESH_TICKS != 0L) return
        val state = chunk.getComponent(index, TetherStakeState.type) ?: return
        if (state.leaning) return
        val blockState = chunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType()) ?: return
        if (!CraftoryCore.isAreaActive(store, blockState) || !blockState.fillWorldPos(commandBuffer, position)) return
        val packed = state.wellId.toLongOrNull() ?: return
        val wellPosition = BlockPos.unpack(packed)
        val well =
            CraftoryCore
                .wells()
                .wellsNear(store, wellPosition, 0)
                .firstOrNull()
                ?.well ?: return
        if (well.state != WellState.TETHERED) return
        val stakePosition = BlockPos(position.x, position.y, position.z)
        world.execute { spawnTetherBeam(world, wellPosition, stakePosition) }
    }

    companion object {
        private const val BEAM_REFRESH_TICKS = 20L
    }
}

internal fun straightenTetherStake(
    world: World,
    position: Vector3i,
    player: PlayerRef,
): Boolean =
    straightenTetherStake(
        world,
        position,
        player,
        CraftoryCore.crewService(),
        player.hasPermission("hytale.command.craftory.wrench.any"),
    )

internal fun straightenTetherStake(
    world: World,
    position: Vector3i,
    player: PlayerRef,
    crews: CrewService,
    hasOverride: Boolean,
): Boolean {
    val straightened =
        straightenTetherStake(
            world,
            position,
            player.uuid,
            crews,
            hasOverride,
        )
    if (straightened) player.sendMessage(Message.raw("The tether stake is straightened."))
    return straightened
}

internal fun straightenTetherStake(
    world: World,
    position: Vector3i,
    placerUuid: UUID,
    crews: CrewService,
    hasOverride: Boolean = false,
): Boolean {
    val section = world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return false
    val store = world.chunkStore.store
    val ref = BlockModule.getBlockEntity(store, section, position.x, position.y, position.z) ?: return false
    val state = store.getComponent(ref, TetherStakeState.type) ?: return false
    if (!state.leaning) return false
    val packed = state.wellId.toLongOrNull() ?: return false
    val wellPosition = BlockPos.unpack(packed)
    val wellRef =
        CraftoryCore
            .wells()
            .wellsNear(store, wellPosition, 0)
            .firstOrNull()
            ?.ref ?: return false
    val well = store.getComponent(wellRef, Glimmerwell.type) ?: return false
    val placer = runCatching { UUID.fromString(state.placerUuid) }.getOrDefault(placerUuid)
    val placerCrewId = runCatching { crews.crewOf(placer).id }.getOrNull()
    if (!JarActions.mayUseWellClaim(placerUuid, well.claimCrewId, placerCrewId, crews, hasOverride)) return false
    state.leaning = false
    store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    tetherStakeIfComplete(world, wellPosition, well, placer, crews, hasOverride)
    return true
}

internal fun recomputeStakeRing(
    world: World,
    wellPosition: BlockPos,
    well: Glimmerwell,
    placer: UUID,
    crews: CrewService = CraftoryCore.crewService(),
) {
    val store = world.chunkStore.store
    val stakeRefs = collectStakes(world, store, wellPosition)
    val angles =
        stakeRefs.map { (_, _, pos) ->
            Math.toDegrees(atan2((pos.z - wellPosition.z).toDouble(), (pos.x - wellPosition.x).toDouble()))
        }
    val leaning = TetherRules.stakeSpread(angles)
    stakeRefs.forEachIndexed { index, (ref, state, _) ->
        state.leaning = index in leaning
        store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    }
    tetherStakeIfComplete(world, wellPosition, well, placer, crews)
}

private fun tetherStakeIfComplete(
    world: World,
    wellPosition: BlockPos,
    well: Glimmerwell,
    placer: UUID,
    crews: CrewService,
    hasOverride: Boolean = false,
) {
    val stakes = collectStakes(world, world.chunkStore.store, wellPosition)
    val leaningCount = stakes.count { it.second.leaning }
    if (!TetherRules.tetherComplete(well.state, well.strength, stakes.size, leaningCount)) return
    val claimant = runCatching { crews.crewOf(placer).id }.getOrNull() ?: return
    if (!canTetherWellClaim(well.claimCrewId, claimant, hasOverride)) return
    well.state = WellState.TETHERED
    well.claimCrewId = claimant
    markWellNeedsSaving(world.chunkStore.store, wellPosition)
}

internal fun canTetherWellClaim(
    existingClaim: UUID?,
    claimant: UUID,
    hasOverride: Boolean,
): Boolean = hasOverride || existingClaim == null || existingClaim == claimant

private fun collectStakes(
    world: World,
    store: Store<ChunkStore>,
    wellPosition: BlockPos,
): MutableList<Triple<Ref<ChunkStore>, TetherStakeState, BlockPos>> {
    val packedWellId = wellPosition.pack().toString()
    val stakes = mutableListOf<Triple<Ref<ChunkStore>, TetherStakeState, BlockPos>>()
    for (x in wellPosition.x - 6..wellPosition.x + 6) {
        for (y in wellPosition.y - 2..wellPosition.y + 2) {
            for (z in wellPosition.z - 6..wellPosition.z + 6) {
                val stake = stakeAt(world, store, x, y, z, packedWellId)
                if (stake != null) stakes.add(Triple(stake.first, stake.second, BlockPos(x, y, z)))
            }
        }
    }
    return stakes
}

private fun stakeAt(
    world: World,
    store: Store<ChunkStore>,
    x: Int,
    y: Int,
    z: Int,
    packedWellId: String,
): Pair<Ref<ChunkStore>, TetherStakeState>? {
    val section = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return null
    val ref = BlockModule.getBlockEntity(store, section, x, y, z) ?: return null
    val state = store.getComponent(ref, TetherStakeState.type) ?: return null
    return if (state.wellId == packedWellId) ref to state else null
}

internal fun spawnTetherBeam(
    world: World,
    well: BlockPos,
    stake: BlockPos,
): Ref<EntityStore>? {
    val beamIndex = Beam.getAssetMap().getIndex(CraftoryIds.TETHER_CYAN_BEAM)
    if (beamIndex < 0) return null
    val origin = Vector3d(well.x + 0.5, well.y + 0.5, well.z + 0.5)
    return BeamComponent.spawn(
        world.entityStore.store,
        origin,
        1.2f,
        AttachedBeam.toBlock(
            beamIndex,
            1f,
            null,
            stake.x,
            stake.y,
            stake.z,
        ),
    )
}

private fun horizontalDistance(
    left: BlockPos,
    right: BlockPos,
): Double {
    val dx = (left.x - right.x).toDouble()
    val dz = (left.z - right.z).toDouble()
    return kotlin.math.sqrt(dx * dx + dz * dz)
}
