package studio.craftory.tech.transport

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

internal object HurlBoarding {
    @Suppress("TooGenericExceptionCaught")
    fun spawn(
        world: World,
        ride: HurlRide,
        resume: ((Boolean) -> Unit)? = null,
        pay: () -> Boolean = { true },
    ): HurlStartResult {
        val store = world.entityStore.store
        var capsule: Ref<EntityStore>? = null
        try {
            val section = activateLaunch(world, ride)
            if (!section.isDone && resume != null) {
                section.whenComplete { ref, failure ->
                    world.execute { resume(failure == null && isTicking(world, ref)) }
                }
                return HurlStartResult(failure = HurlStartFailure.PENDING)
            }
            if (!section.isDone || !isTicking(world, section.getNow(null))) {
                return failed(world, ride, capsule, HurlStartFailure.SPAWN_FAILED)
            }
            val holder = EntityStore.REGISTRY.newHolder()
            holder.addComponent(HurlRide.type, ride)
            holder.ensureComponent(EntityStore.REGISTRY.nonSerializedComponentType)
            holder.ensureComponent(UUIDComponent.getComponentType())
            HurlMounts.prepare(holder, store)
            holder.addComponent(
                TransformComponent.getComponentType(),
                TransformComponent(HurlMotion.vector(ride), Rotation3f.IDENTITY),
            )
            HurlChunks.refresh(world, ride)
            val spawned = store.addEntity(holder, AddReason.SPAWN)
            capsule = spawned
            if (spawned == null || !spawned.isValid || ride.ended) {
                return failed(world, ride, capsule, HurlStartFailure.SPAWN_FAILED)
            }
            ride.rider?.let { store.putComponent(it, HurlRider.type, HurlRider(spawned)) }
            HurlMounts.attach(store, spawned, ride, HurlRides.counters(world).config)
            if (!pay()) return failed(world, ride, capsule, HurlStartFailure.INSUFFICIENT_ENERGY)
            return HurlStartResult(spawned)
        } catch (exception: RuntimeException) {
            Logger.getLogger("CraftoryTech").warning("Hurl boarding failed: ${exception.javaClass.simpleName}")
            return failed(world, ride, capsule, HurlStartFailure.SPAWN_FAILED)
        }
    }

    private fun activateLaunch(
        world: World,
        ride: HurlRide,
    ): CompletableFuture<Ref<ChunkStore>> {
        val pos = BlockPos.unpack(ride.currentBlock())
        val grid = world.chunkStore
        return grid
            .getChunkReferenceAsync(ChunkUtil.indexChunk(pos.x shr 5, pos.z shr 5), GetChunkFlags.SET_TICKING)
            .thenCompose {
                grid.getChunkSectionReferenceAsync(
                    pos.x shr 5,
                    pos.y shr 5,
                    pos.z shr 5,
                    GetChunkFlags.SET_TICKING,
                )
            }
    }

    private fun isTicking(
        world: World,
        ref: Ref<ChunkStore>?,
    ): Boolean =
        ref != null && ref.isValid &&
            !world.chunkStore.store
                .getArchetype(ref)
                .contains(ChunkStore.REGISTRY.nonTickingComponentType)

    private fun failed(
        world: World,
        ride: HurlRide,
        capsule: Ref<EntityStore>?,
        reason: HurlStartFailure,
    ): HurlStartResult {
        if (capsule != null && capsule.isValid) world.entityStore.store.removeEntity(capsule, RemoveReason.REMOVE)
        ride.ended = true
        HurlChunks.releaseAll(world, ride)
        return HurlStartResult(failure = reason)
    }
}
