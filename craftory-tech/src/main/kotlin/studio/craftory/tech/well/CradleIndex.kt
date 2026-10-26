package studio.craftory.tech.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.WellState
import studio.craftory.tech.machine.CradleState
import studio.craftory.tech.machine.CradleSystem

@Suppress("MaxLineLength")
internal class CradleIndex : Resource<ChunkStore> {
    private val positions = HashSet<Long>()

    override fun clone(): Resource<ChunkStore> = CradleIndex()

    fun add(position: Long) {
        positions.add(position)
    }

    fun remove(position: Long) {
        positions.remove(position)
    }

    fun near(position: BlockPos): LongArray =
        positions
            .filter { packed ->
                val cradle = BlockPos.unpack(packed)
                kotlin.math.abs(cradle.x - position.x) <= HARMONY_RADIUS &&
                    kotlin.math.abs(cradle.y - position.y) <= HARMONY_RADIUS &&
                    kotlin.math.abs(cradle.z - position.z) <= HARMONY_RADIUS
            }.toLongArray()

    fun clearSeatedWell(
        world: com.hypixel.hytale.server.core.universe.world.World,
        wellPosition: BlockPos,
    ) {
        val cradle = BlockPos(wellPosition.x, wellPosition.y - 1, wellPosition.z)
        if (cradle.pack() !in positions) return
        val ref = BlockModule.getBlockEntity(world, cradle.x, cradle.y, cradle.z) ?: return
        val store = world.chunkStore.store
        val state = store.getComponent(ref, CradleState.type) ?: return
        state.seatedStrength = ""
        state.harmony = 0
        store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
    }

    companion object {
        const val HARMONY_RADIUS = 4
        lateinit var type: ResourceType<ChunkStore, CradleIndex>
    }
}

internal class CradleLifecycle : RefSystem<ChunkStore>() {
    override fun getQuery(): Query<ChunkStore> =
        Query.and(CradleState.type, BlockModule.BlockStateInfo.getComponentType())

    override fun onEntityAdded(
        ref: Ref<ChunkStore>,
        reason: AddReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        if (reason != AddReason.SPAWN && reason != AddReason.LOAD) return
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val packed = BlockPos.pack(position.x, position.y, position.z)
        store.getResource(CradleIndex.type).add(packed)
    }

    override fun onEntityRemove(
        ref: Ref<ChunkStore>,
        reason: RemoveReason,
        store: Store<ChunkStore>,
        commandBuffer: CommandBuffer<ChunkStore>,
    ) {
        val blockState = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val position = Vector3i()
        if (!blockState.fillWorldPos(commandBuffer, position)) return
        val cradle = BlockPos(position.x, position.y, position.z)
        store.getResource(CradleIndex.type).remove(cradle.pack())
        if (reason != RemoveReason.REMOVE) return
        val wellRef = CradleSystem.findWell(store, BlockPos(cradle.x, cradle.y + 1, cradle.z)) ?: return
        val well = commandBuffer.getComponent(wellRef, Glimmerwell.type) ?: return
        well.state = WellState.WILD
        well.claimCrewId = null
        val wellBlockState = commandBuffer.getComponent(wellRef, BlockModule.BlockStateInfo.getComponentType())
        wellBlockState?.markNeedsSaving(commandBuffer)
    }
}

internal class CradlePlaceSystem : EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent,
    ) {
        if (!event.isCancelled) CradleHarmonyEvents.recheck(store, event.targetBlock)
    }
}

internal class CradleBreakSystem : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent,
    ) {
        if (!event.isCancelled) CradleHarmonyEvents.recheck(store, event.targetBlock)
    }
}

internal object CradleHarmonyEvents {
    internal fun recheck(
        store: Store<EntityStore>,
        target: org.joml.Vector3ic,
    ) {
        val world = store.externalData.world
        val position = BlockPos(target.x(), target.y(), target.z())
        world.execute {
            val liveStore = world.chunkStore.store
            val positions = liveStore.getResource(CradleIndex.type).near(position)
            if (positions.isEmpty()) return@execute
            positions.forEach { packed ->
                val cradle = BlockPos.unpack(packed)
                val ref =
                    com.hypixel.hytale.server.core.modules.block.BlockModule.getBlockEntity(
                        world,
                        cradle.x,
                        cradle.y,
                        cradle.z,
                    ) ?: return@forEach
                val state = liveStore.getComponent(ref, CradleState.type) ?: return@forEach
                val well =
                    CradleSystem
                        .findWell(liveStore, BlockPos(cradle.x, cradle.y + 1, cradle.z))
                        ?.let { liveStore.getComponent(it, Glimmerwell.type) } ?: return@forEach
                if (well.state != WellState.SEATED) return@forEach
                val harmony = CradleSystem.harmonyAt(liveStore, cradle, well.taste)
                if (state.harmony != harmony) {
                    state.harmony = harmony
                    val blockState = liveStore.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())
                    blockState?.markNeedsSaving(liveStore)
                }
            }
        }
    }
}
