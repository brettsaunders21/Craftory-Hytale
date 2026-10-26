package studio.craftory.tech.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.mob.CraftoryMobs
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import kotlin.math.floor

internal class WellGuardianCalmSystem : DelayedEntitySystem<EntityStore>(1.0f) {
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
        val chunkStore = world.chunkStore.store
        val guardians = store.getResource(WellGuardianIndex.type)
        for (index in 0 until chunk.size()) {
            val transform = chunk.getComponent(index, TransformComponent.getComponentType())
            if (transform != null) tryCalmNear(world, chunkStore, transform.position, guardians)
        }
    }

    private fun tryCalmNear(
        world: com.hypixel.hytale.server.core.universe.world.World,
        store: Store<ChunkStore>,
        position: Vector3d,
        guardians: WellGuardianIndex,
    ) {
        val origin = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
        val section = world.chunkStore.getChunkSectionReferenceAtBlock(origin.x, origin.y, origin.z) ?: return
        if (!CraftoryCore.isAreaActive(store, section)) return
        CraftoryCore.wells().wellsNear(store, origin, 16).forEach { near ->
            trySpawnGuardian(world, world.entityStore.store, near.pos, near.well, guardians)
        }
    }

    private fun trySpawnGuardian(
        world: com.hypixel.hytale.server.core.universe.world.World,
        store: Store<EntityStore>,
        position: BlockPos,
        well: Glimmerwell,
        guardians: WellGuardianIndex,
    ) {
        if (WellSelfTestExclusions.isWell(position)) return
        if (well.state != WellState.WILD || well.temperament != Temperament.SPARK ||
            well.strength != WellStrength.STEADY
        ) {
            return
        }
        val id = position.pack().toString()
        if (guardians.refsByWellId[id]?.isValid == true) return
        guardians.refsByWellId.remove(id)
        val spawned =
            CraftoryMobs.spawn(
                world,
                CraftoryIds.SPARKJAW,
                Vector3d(position.x + 1.5, position.y.toDouble(), position.z + 0.5),
                position,
            ) ?: return
        if (spawned.isValid) {
            store.putComponent(spawned, SparkjawGuardian.type, SparkjawGuardian(id))
            guardians.refsByWellId[id] = spawned
        }
    }
}

internal class WellGuardianLifecycle : RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> = SparkjawGuardian.type

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val id = commandBuffer.getComponent(ref, SparkjawGuardian.type)?.wellId ?: return
        if (id.isNotEmpty()) store.getResource(WellGuardianIndex.type).refsByWellId[id] = ref
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val id = commandBuffer.getComponent(ref, SparkjawGuardian.type)?.wellId ?: return
        val index = store.getResource(WellGuardianIndex.type)
        if (index.refsByWellId[id] == ref) index.refsByWellId.remove(id)
    }
}

internal class WellGuardianDeathSystem : DeathSystems.OnDeathSystem() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(DeathComponent.getComponentType(), NPCEntity.getComponentType()!!, SparkjawGuardian.type)

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val packed = commandBuffer.getComponent(ref, SparkjawGuardian.type)?.wellId?.toLongOrNull() ?: return
        val position = BlockPos.unpack(packed)
        val well =
            CraftoryCore
                .wells()
                .wellsNear(store.externalData.world.chunkStore.store, position, 0)
                .firstOrNull()
                ?.well ?: return
        if (well.state != WellState.JARRED) well.state = WellState.DORMANT
    }
}
