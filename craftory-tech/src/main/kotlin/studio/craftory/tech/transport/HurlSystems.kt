package studio.craftory.tech.transport

import com.hypixel.hytale.builtin.mounts.MountSystems
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.tech.CraftoryIds
import kotlin.math.floor

internal class HurlRideSystem : EntityTickingSystem<EntityStore>() {
    override fun getDependencies(): Set<Dependency<EntityStore>> =
        setOf(SystemDependency(Order.AFTER, MountSystems.HandleMountInput::class.java))

    override fun getQuery(): Query<EntityStore> = HurlRide.type

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val ride = chunk.getComponent(index, HurlRide.type) ?: return
        HurlRides.advance(chunk.getReferenceTo(index), ride, store, commandBuffer)
    }
}

internal class HurlRideLifecycle : RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> = HurlRide.type

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        store.getResource(HurlCounters.type).capsules++
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val ride = commandBuffer.getComponent(ref, HurlRide.type) ?: return
        HurlRides.finish(ride, store, commandBuffer, ride.currentBlock(), true)
        store.getResource(HurlCounters.type).capsules--
    }
}

internal class HurlRiderLifecycle : RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> = HurlRider.type

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) = Unit

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val capsule = commandBuffer.getComponent(ref, HurlRider.type)?.capsule ?: return
        if (!capsule.isValid) return
        val ride = commandBuffer.getComponent(capsule, HurlRide.type) ?: return
        if (!ride.ended) {
            ride.rider = null
            HurlRides.eject(capsule, ride, store, commandBuffer)
        }
    }
}

internal class HurlFallDamageSystem : DamageEventSystem() {
    override fun getQuery(): Query<EntityStore> = Query.or(HurlFallGuard.type, Player.getComponentType())

    override fun getGroup() = DamageModule.get().filterDamageGroup

    @Suppress("DEPRECATION")
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: Damage,
    ) {
        val guard = chunk.getComponent(index, HurlFallGuard.type)
        val fall = DamageCause.FALL?.let { event.cause?.isOrInherits(it) } == true
        if (protectsFall(guard, store.externalData.world.tick, fall) { onNet(index, chunk, store) }) {
            event.isCancelled =
                true
        }
        if (guard != null && store.externalData.world.tick >= guard.expiryTick) {
            commandBuffer.tryRemoveComponent(chunk.getReferenceTo(index), HurlFallGuard.type)
        }
    }

    private fun onNet(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
    ): Boolean {
        if (chunk.getComponent(index, Player.getComponentType()) == null) return false
        val position = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: return false
        return blockId(
            store.externalData.world,
            floor(position.x).toInt(),
            floor(position.y - 0.1).toInt(),
            floor(position.z).toInt(),
        ) ==
            CraftoryIds.CATCH_NET
    }
}

internal fun protectsFall(
    guard: HurlFallGuard?,
    tick: Long,
    fall: Boolean,
    onNet: () -> Boolean,
): Boolean = fall && (guard?.protects(tick, true) == true || onNet())
