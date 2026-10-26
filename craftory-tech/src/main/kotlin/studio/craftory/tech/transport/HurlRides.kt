package studio.craftory.tech.transport

import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.transport.HurlArc
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.JarCarrier
import java.util.UUID

enum class HurlStartFailure { NO_ROUTE, ALREADY_RIDING, INSUFFICIENT_ENERGY, SPAWN_FAILED, FORBIDDEN, PENDING }

data class HurlStartResult(
    val capsule: Ref<EntityStore>? = null,
    val failure: HurlStartFailure? = null,
)

internal object HurlRides {
    fun counters(world: World): HurlCounters = world.entityStore.store.getResource(HurlCounters.type)

    fun start(
        world: World,
        from: Long,
        to: Long,
        rider: Ref<EntityStore>? = null,
        riderUuid: UUID? = null,
        completed: ((HurlStartResult) -> Unit)? = null,
    ): HurlStartResult {
        world.debugAssertInTickingThread()
        val store = world.entityStore.store
        if (rider != null && carriesFilledJar(store, rider)) {
            return HurlStartResult(failure = HurlStartFailure.FORBIDDEN)
        }
        val identity = StationPermissions.riderIdentity(store, rider, riderUuid)
        if (!StationPermissions.mayStart(world, from, to, identity, rider != null || riderUuid != null)) {
            return HurlStartResult(failure = HurlStartFailure.FORBIDDEN)
        }
        if (rider != null && HurlMounts.occupied(rider, store)) {
            return HurlStartResult(failure = HurlStartFailure.ALREADY_RIDING)
        }
        val route =
            CraftoryCore.tubeAccess(world).route(from, to)
                ?: return HurlStartResult(failure = HurlStartFailure.NO_ROUTE)
        val config = counters(world).config
        val rank = CraftoryCore.gradeRank(config.costGrade)
        val cost =
            config.energyCost(route.lengthBlocks)
                ?: return HurlStartResult(failure = HurlStartFailure.INSUFFICIENT_ENERGY)
        val energy = CraftoryCore.energyAccess(world)
        if (rank == null || energy.networkStored(from, rank) < cost) {
            return HurlStartResult(failure = HurlStartFailure.INSUFFICIENT_ENERGY)
        }
        val ride =
            HurlRide().also {
                it.route = route
                it.rider = rider
                it.riderUuid = identity
                it.speed = config.speed
            }
        route.positionAt(0.0, ride.output)
        return HurlBoarding.spawn(world, ride, resume = { ready ->
            val result =
                if (ready) {
                    start(world, from, to, rider, identity, completed)
                } else {
                    HurlStartResult(failure = HurlStartFailure.SPAWN_FAILED)
                }
            completePending(world, rider, result, completed)
        }) { energy.drawStored(from, cost, rank) }
    }

    fun startArc(
        world: World,
        launch: HurlPoint,
        landing: HurlPoint,
        rider: Ref<EntityStore>? = null,
        riderUuid: UUID? = null,
        completed: ((HurlStartResult) -> Unit)? = null,
    ): HurlStartResult {
        world.debugAssertInTickingThread()
        val store = world.entityStore.store
        if (rider != null && carriesFilledJar(store, rider)) {
            return HurlStartResult(failure = HurlStartFailure.FORBIDDEN)
        }
        if (rider != null && HurlMounts.occupied(rider, store)) {
            return HurlStartResult(failure = HurlStartFailure.ALREADY_RIDING)
        }
        val trajectory = HurlArc.create(launch, landing) ?: return HurlStartResult(failure = HurlStartFailure.NO_ROUTE)
        val ride =
            HurlRide().also {
                it.mode = HurlMode.ARC
                it.arc = trajectory
                it.rider = rider
                it.riderUuid = riderUuid
                trajectory.positionAt(0.0, it.output)
            }
        return HurlBoarding.spawn(world, ride, resume = { ready ->
            val result =
                if (ready) {
                    startArc(world, launch, landing, rider, riderUuid, completed)
                } else {
                    HurlStartResult(failure = HurlStartFailure.SPAWN_FAILED)
                }
            completePending(world, rider, result, completed)
        })
    }

    private fun carriesFilledJar(
        store: com.hypixel.hytale.component.Store<EntityStore>,
        rider: Ref<EntityStore>,
    ): Boolean {
        if (store.getComponent(rider, JarCarrier.type) != null) return true
        val inventory = InventoryComponent.getCombined(store, rider, *InventoryComponent.EVERYTHING)
        return (0 until inventory.capacity.toInt()).any { slot ->
            inventory.getItemStack(slot.toShort())?.itemId == CraftoryIds.GOB_JAR_FILLED
        }
    }

    private fun completePending(
        world: World,
        rider: Ref<EntityStore>?,
        result: HurlStartResult,
        completed: ((HurlStartResult) -> Unit)?,
    ) {
        if (result.failure == HurlStartFailure.PENDING) return
        if (rider != null && rider.isValid) {
            result.failure?.let {
                world.entityStore.store
                    .getComponent(rider, PlayerRef.getComponentType())
                    ?.sendMessage(Message.translation(it.messageKey()))
            }
        }
        completed?.invoke(result)
    }

    fun advance(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ) {
        if (ride.ended) return
        val world = store.externalData.world
        if (ride.loadFailed || (ride.rider != null && !ride.rider!!.isValid)) {
            eject(capsule, ride, store, commands)
            return
        }
        if (!HurlChunks.refresh(world, ride)) {
            HurlMotion.move(capsule, ride, commands)
            return
        }
        if (ride.mode == HurlMode.ARC) {
            advanceArc(capsule, ride, store, commands)
            return
        }
        advanceTube(capsule, ride, store, commands)
    }

    private fun advanceArc(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ) {
        val landed = ride.stepArc()
        HurlMotion.move(capsule, ride, commands)
        if (landed) arrive(capsule, ride, store, commands)
    }

    private fun advanceTube(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val next = (ride.distance + ride.speed).coerceAtMost(ride.route.lengthBlocks)
        val tubes = CraftoryCore.tubeAccess(world)
        var lastPresent = ride.route.blockAt(ride.distance)
        for (block in ride.route.blocksAhead(ride.distance, next - ride.distance)) {
            if (!tubes.isPresent(block)) {
                if (block == lastPresent) {
                    lastPresent = ride.route.nodes
                        .takeWhile { it != block }
                        .lastOrNull { tubes.isPresent(it) }
                        ?: ride.route.nodes.first()
                }
                eject(capsule, ride, store, commands, lastPresent)
                return
            }
            lastPresent = block
        }
        ride.distance = next
        ride.route.positionAt(next, ride.output)
        HurlMotion.move(capsule, ride, commands)
        if (next >= ride.route.lengthBlocks) arrive(capsule, ride, store, commands)
    }

    fun arrive(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
    ) {
        if (ride.mode ==
            HurlMode.ARC
        ) {
            requireNotNull(ride.arc).positionAt(requireNotNull(ride.arc).flightTicks, ride.output)
        }
        finish(
            ride,
            store,
            commands,
            if (ride.mode ==
                HurlMode.TUBE
            ) {
                ride.route.nodes.last()
            } else {
                ride.currentBlock()
            },
            ride.mode == HurlMode.ARC,
        )
        commands.tryRemoveEntity(capsule, RemoveReason.REMOVE)
    }

    fun eject(
        capsule: Ref<EntityStore>,
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
        lastPresent: Long = ride.currentBlock(),
    ) {
        finish(ride, store, commands, lastPresent, true)
        commands.tryRemoveEntity(capsule, RemoveReason.REMOVE)
    }

    internal fun finish(
        ride: HurlRide,
        store: Store<EntityStore>,
        commands: CommandBuffer<EntityStore>,
        block: Long,
        fallGuard: Boolean,
    ) {
        if (ride.ended) return
        ride.ended = true
        val world = store.externalData.world
        HurlChunks.releaseAll(world, ride)
        val pos = BlockPos.unpack(block)
        val height = if (fallGuard) 0.5 else 1.0
        ride.rider?.takeIf { it.isValid }?.let { rider ->
            HurlMounts.detach(rider, commands)
            commands.tryRemoveComponent(rider, HurlRider.type)
            if (fallGuard) {
                commands.putComponent(
                    rider,
                    HurlFallGuard.type,
                    HurlFallGuard(world.tick + HurlConfig.FALL_GUARD_TICKS),
                )
            }
        }
        val exit =
            if (ride.mode ==
                HurlMode.ARC
            ) {
                Vector3d(HurlMotion.vector(ride))
            } else {
                Vector3d(pos.x + 0.5, pos.y + height, pos.z + 0.5)
            }
        ride.exitPosition = HurlPoint(exit.x, exit.y, exit.z)
        HurlMotion.teleport(ride, commands, exit)
    }
}
