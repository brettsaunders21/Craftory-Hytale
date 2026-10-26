package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.spatial.SpatialResource
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.modules.entity.EntityModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.Config
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import kotlin.math.floor
import kotlin.math.sin

internal class WellPulseSystem(
    private val config: Config<WellConfig>,
) : DelayedEntitySystem<EntityStore>(1.0f) {
    override fun getQuery(): Query<EntityStore> =
        Query.and(PlayerRef.getComponentType(), TransformComponent.getComponentType())

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val state = store.getResource(WellPulseState.type)
        val tick = world.tick
        for (index in 0 until chunk.size()) {
            val player = chunk.getComponent(index, PlayerRef.getComponentType())
            val position = chunk.getComponent(index, TransformComponent.getComponentType())?.position
            if (player != null && position != null) {
                val origin = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
                val activeSection = world.chunkStore.getChunkSectionReferenceAtBlock(origin.x, origin.y, origin.z)
                if (activeSection != null && CraftoryCore.isAreaActive(world.chunkStore.store, activeSection)) {
                    pulseWells(world, origin, tick, state, store)
                }
            }
        }
    }

    private fun pulseWells(
        world: World,
        origin: BlockPos,
        tick: Long,
        state: WellPulseState,
        store: Store<EntityStore>,
    ) {
        val wells =
            CraftoryCore
                .wells()
                .wellsNear(world.chunkStore.store, origin, config.get().pulseRadius.coerceAtLeast(0))
        wells.forEach { well ->
            if (WellSelfTestExclusions.isWell(well.pos)) return@forEach
            if (well.well.state != WellState.WILD && !well.well.corrupted) return@forEach
            val id = well.pos.pack()
            if (!WellBirthRules.cooldownElapsed(
                    state.lastPulseByPosition[id],
                    tick,
                    WellPulseAppearance.intervalTicks(well.well.strength),
                )
            ) {
                return@forEach
            }
            state.lastPulseByPosition[id] = tick
            if (well.well.corrupted) {
                val drift = sin(tick / 8.0) * 0.25
                ParticleUtil.spawnParticleEffect(
                    CraftoryIds.CHIMNEY_SMOKE_PARTICLE,
                    Vector3d(well.pos.x + 0.5 + drift, well.pos.y + 1.2, well.pos.z + 0.5),
                    store,
                )
                if (well.well.strength == WellStrength.FLICKER) {
                    ParticleUtil.spawnParticleEffect(
                        CraftoryIds.CHIMNEY_SMOKE_PARTICLE,
                        Vector3d(well.pos.x + 0.5 - drift, well.pos.y + 1.6, well.pos.z + 0.5),
                        store,
                    )
                }
            } else {
                WellPulseAppearance.particles(well.well.strength, tick).forEach { particle ->
                    val position =
                        Vector3d(
                            well.pos.x + 0.5 + particle.horizontalOffset,
                            well.pos.y + particle.height,
                            well.pos.z + 0.5,
                        )
                    spawnTintedParticle(
                        store,
                        position,
                        particle.scale,
                        WellPulseAppearance.tint(well.well.temperament),
                    )
                }
            }
        }
    }

    private fun spawnTintedParticle(
        store: Store<EntityStore>,
        position: Vector3d,
        scale: Float,
        tint: com.hypixel.hytale.protocol.Color,
    ) {
        val spatial = store.getResource(EntityModule.get().playerSpatialResourceType)
        val players: MutableList<Ref<EntityStore>> = SpatialResource.getThreadLocalReferenceList()
        spatial.spatialStructure.collect(position, ParticleUtil.DEFAULT_PARTICLE_DISTANCE, players)
        ParticleUtil.spawnParticleEffect(
            WellPulseAppearance.PARTICLE_ID,
            position,
            0f,
            0f,
            0f,
            scale,
            tint,
            players,
            store,
        )
    }
}
