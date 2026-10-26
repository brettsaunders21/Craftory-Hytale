package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
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
            if (well.well.state != WellState.WILD) return@forEach
            val id = well.pos.pack()
            if (!WellBirthRules.cooldownElapsed(state.lastPulseByPosition[id], tick, 20L)) return@forEach
            state.lastPulseByPosition[id] = tick
            val drift = sin(tick / 8.0) * 0.25
            ParticleUtil.spawnParticleEffect(
                "Impact_Spark",
                Vector3d(well.pos.x + 0.5 + drift, well.pos.y + 1.2, well.pos.z + 0.5),
                store,
            )
            if (well.well.strength == WellStrength.FLICKER) {
                ParticleUtil.spawnParticleEffect(
                    "Impact_Spark",
                    Vector3d(well.pos.x + 0.5 - drift, well.pos.y + 1.6, well.pos.z + 0.5),
                    store,
                )
            }
        }
    }
}
