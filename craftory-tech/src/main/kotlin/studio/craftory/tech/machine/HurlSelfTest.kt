package studio.craftory.tech.machine

import com.hypixel.hytale.codec.ExtraInfo
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.HurlArc
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.transport.HurlBoarding
import studio.craftory.tech.transport.HurlMode
import studio.craftory.tech.transport.HurlRide
import studio.craftory.tech.transport.HurlRides
import studio.craftory.tech.transport.HurlStartFailure
import studio.craftory.tech.transport.HurlStation
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Suppress("TooManyFunctions")
internal class HurlSelfTest(
    private val run: SelfTestRun.Run,
) {
    private val world = run.world
    private val store = world.chunkStore.store
    private val positions = mutableSetOf<Long>()
    private val capsules = mutableListOf<Ref<EntityStore>>()
    private val source = pos(20)
    private val destination = pos(28)
    private val storage = BlockPos.pack(10_020, 155, 10_009)
    private var complete: () -> Unit = {}

    fun start(done: () -> Unit) {
        complete = done
        run.hurlCleanup = ::cleanup
        guarded {
            val baseline = CraftoryCore.tubeAccess(world).nodeCount
            val networks = CraftoryCore.tubeAccess(world).networkCount
            for (x in 20..28) {
                if (x !=
                    24
                ) {
                    place(pos(x), if (x == 20 || x == 28) CraftoryIds.HURL_STATION else CraftoryIds.HURL_TUBE)
                }
            }
            val tubes = CraftoryCore.tubeAccess(world)
            check(
                tubes.nodeCount == baseline + 8 && tubes.networkOf(source) != tubes.networkOf(destination),
                "hurl graph separate components",
            )
            place(pos(24), CraftoryIds.HURL_TUBE)
            check(
                tubes.nodeCount == baseline + 9 && tubes.networkCount == networks + 1 &&
                    tubes.networkOf(source) == tubes.networkOf(destination),
                "hurl graph build and merge",
            )
            check(tubes.stationsReachableFrom(source) == listOf(destination), "hurl reachable stations")
            clear(pos(24))
            check(
                tubes.networkOf(
                    source,
                ) != tubes.networkOf(destination) && tubes.stationsReachableFrom(source).isEmpty(),
                "hurl split on break",
            )
            place(pos(24), CraftoryIds.HURL_TUBE)
            verifyUnload(baseline + 9)
            val ref = entity(source)
            val station = requireNotNull(store.getComponent(ref, HurlStation.type))
            station.rename("  Selftest Station  ", ref, store)
            check(
                store.getComponent(ref, HurlStation.type)?.name == "Selftest Station",
                "hurl station rename persisted component",
            )
            val encoded = HurlStation.CODEC.encode(station, ExtraInfo())
            check(
                HurlStation.CODEC.decode(encoded, ExtraInfo())?.name == station.name,
                "hurl station naming codec round trip",
            )
            place(storage, CraftoryIds.CANISTER)
            seed(10_000)
            after(4) {
                reviewRegressions()
                startTube()
            }
        }
    }

    private fun verifyUnload(expectedNodes: Int) {
        val tubes = CraftoryCore.tubeAccess(world)
        val unloaded = store.removeEntity(entity(pos(24)), RemoveReason.UNLOAD)
        check(
            tubes.nodeCount == expectedNodes && tubes.route(source, destination) != null,
            "hurl graph retains unloaded tube",
        )
        store.addEntity(unloaded, AddReason.LOAD)
        check(
            tubes.nodeCount == expectedNodes && tubes.route(source, destination) != null,
            "hurl graph reload is idempotent",
        )
    }

    private fun reviewRegressions() {
        val energy = CraftoryCore.energyAccess(world)
        val before = energy.stored(storage)
        for (position in listOf(source, destination)) {
            val owner = requireNotNull(store.getComponent(entity(position), MachineOwner.type))
            val previous = owner.placerUuid
            try {
                owner.placerUuid = UUID.randomUUID()
                check(
                    HurlRides.start(world, source, destination, riderUuid = UUID.randomUUID()).failure ==
                        HurlStartFailure.FORBIDDEN && energy.stored(storage) == before,
                    "hurl foreign station refuses ride without debit",
                    "station=$position",
                )
            } finally {
                owner.placerUuid = previous
            }
        }
        val route = requireNotNull(CraftoryCore.tubeAccess(world).route(source, destination))
        val ride =
            HurlRide().also {
                it.route = route
                route.positionAt(0.0, it.output)
            }
        check(
            HurlBoarding.spawn(world, ride) { false }.failure == HurlStartFailure.INSUFFICIENT_ENERGY &&
                energy.stored(storage) == before && HurlRides.counters(world).capsules == 0,
            "hurl boarding payment refusal cleans capsule without debit",
        )
        val failed =
            HurlRide().also {
                it.mode = HurlMode.ARC
                it.arc = HurlArc.create(HurlPoint(0.5, -64.0, 0.5), HurlPoint(1.5, -64.0, 0.5))
                requireNotNull(it.arc).positionAt(0.0, it.output)
            }
        var charged = false
        val result =
            HurlBoarding.spawn(world, failed) {
                charged = true
                true
            }
        check(
            result.failure == HurlStartFailure.SPAWN_FAILED && !charged && energy.stored(storage) == before,
            "hurl failed spawn does not debit energy",
        )
    }

    private fun startTube() {
        val energy = CraftoryCore.energyAccess(world)
        val before = energy.stored(storage)
        val route =
            requireNotNull(CraftoryCore.tubeAccess(world).route(source, destination)) { "hurl topology disappeared" }
        val cost = requireNotNull(HurlRides.counters(world).config.energyCost(route.lengthBlocks))
        val started = HurlRides.start(world, source, destination)
        val capsule = requireNotNull(started.capsule) { "hurl start ${started.failure}; stored=$before" }
        capsules.add(capsule)
        val ride = requireNotNull(world.entityStore.store.getComponent(capsule, HurlRide.type))
        check(
            before - energy.stored(storage) == cost,
            "hurl exact cost debit",
            "before=$before after=${energy.stored(storage)} cost=$cost",
        )
        check(capsule.isValid, "hurl ride starts")
        await(100, { ride.distance > 0.0 }) {
            val tick = world.tick
            val distance = ride.distance
            after(4) {
                val expected = DoubleArray(3)
                route.positionAt(distance + (world.tick - tick) * ride.speed, expected)
                val point =
                    world.entityStore.store
                        .getComponent(
                            capsule,
                            TransformComponent.getComponentType(),
                        )?.position
                check(
                    point != null && abs(point.x - expected[0]) < 0.001 && abs(point.y - expected[1]) < 0.001 &&
                        abs(point.z - expected[2]) < 0.001,
                    "hurl spline position after ticks",
                    "tickDelta=${world.tick - tick} distance=${ride.distance}",
                )
                await(100, { !capsule.isValid }) {
                    check(ride.exitPosition == ride.landing, "hurl arrival removes capsule at destination")
                    seed(0)
                    after(4) {
                        check(
                            HurlRides.start(world, source, destination).failure == HurlStartFailure.INSUFFICIENT_ENERGY,
                            "hurl insufficient energy refusal",
                        )
                        seed(10_000)
                        after(4) { breakRide() }
                    }
                }
            }
        }
    }

    private fun breakRide() {
        val capsule = requireNotNull(HurlRides.start(world, source, destination).capsule)
        capsules.add(capsule)
        val ride = requireNotNull(world.entityStore.store.getComponent(capsule, HurlRide.type))
        await(100, { ride.distance >= 1.0 }) {
            val broken = 20 + kotlin.math.ceil(ride.distance).toInt() + 1
            clear(pos(broken))
            await(100, { !capsule.isValid }) {
                val expected = HurlPoint(10_000 + broken - 0.5, 154.5, 10_009.5)
                check(
                    ride.exitPosition == expected,
                    "hurl broken tube ejects at last present block",
                    "exit=${ride.exitPosition} expected=$expected",
                )
                arcRide()
            }
        }
    }

    private fun arcRide() {
        place(BlockPos.pack(10_020, 154, 10_012), CraftoryIds.HURL_CANNON)
        val net = BlockPos.pack(10_028, 154, 10_012)
        place(net, CraftoryIds.CATCH_NET)
        val target = HurlPoint(10_028.5, 155.0, 10_012.5)
        val capsule = requireNotNull(HurlRides.startArc(world, HurlPoint(10_020.5, 155.0, 10_012.5), target).capsule)
        capsules.add(capsule)
        val ride = requireNotNull(world.entityStore.store.getComponent(capsule, HurlRide.type))
        await(100, { !capsule.isValid }) {
            check(
                ride.exitPosition == target && ride.landing == target,
                "hurl cannon arc lands on Catch Net",
                "landing=${ride.exitPosition}",
            )
            cleanup()
            after(4) {
                val counters = HurlRides.counters(world)
                check(
                    counters.capsules == 0 && counters.chunkHolders == 0,
                    "hurl cleanup zero capsules and chunk holders",
                    "capsules=${counters.capsules} chunkHolders=${counters.chunkHolders}",
                )
                complete()
            }
        }
    }

    private fun seed(amount: Long) {
        val ref = entity(storage)
        val holder = store.removeEntity(ref, RemoveReason.REMOVE)
        holder.putComponent(
            EnergyMember.type,
            EnergyMember(
                CraftoryIds.CANISTER_TYPE,
                amount,
                requireNotNull(CraftoryCore.gradeRank(HurlRides.counters(world).config.costGrade)),
            ),
        )
        store.addEntity(holder, AddReason.SPAWN)
    }

    private fun place(
        position: Long,
        id: String,
    ) {
        positions.add(position)
        val point = BlockPos.unpack(position)
        val type = requireNotNull(BlockType.fromString(id))
        val section = requireNotNull(world.chunkStore.getChunkSectionReferenceAtBlock(point.x, point.y, point.z))
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                section,
                point.x,
                point.y,
                point.z,
                BlockType.getAssetMap().getIndex(id),
                type,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            ),
        )
        if (type.blockEntity != null) {
            BlockEntity.ensureBlockEntity(
                store,
                section,
                requireNotNull(store.getComponent(section, BlockComponentSection.getComponentType())),
                point.x and 31,
                point.y and 31,
                point.z and 31,
                type,
                0,
            )
        }
    }

    private fun clear(position: Long) {
        val p = BlockPos.unpack(position)
        val section = world.chunkStore.getChunkSectionReferenceAtBlock(p.x, p.y, p.z) ?: return
        BlockOperations.setBlock(
            world.chunkStore,
            section,
            p.x,
            p.y,
            p.z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            0,
            0,
            SetBlockSettings.NO_DROP_ITEMS,
        )
    }

    fun cleanup() {
        capsules.filter { it.isValid }.forEach { world.entityStore.store.removeEntity(it, RemoveReason.REMOVE) }
        positions.forEach(::clear)
    }

    private fun entity(position: Long) =
        BlockPos.unpack(position).let {
            requireNotNull(BlockModule.getBlockEntity(world, it.x, it.y, it.z))
        }

    private fun pos(x: Int) = BlockPos.pack(10_000 + x, 154, 10_009)

    private fun await(
        remaining: Int,
        predicate: () -> Boolean,
        action: () -> Unit,
    ) {
        if (predicate()) {
            action()
        } else {
            require(remaining > 0) { "hurl wait timed out" }
            after(1) { await(remaining - 1, predicate, action) }
        }
    }

    private fun after(
        ticks: Long,
        action: () -> Unit,
    ) {
        val target = world.tick + ticks

        fun next() {
            if (store.getResource(SelfTestRun.type).active !== run) return
            if (world.tick >= target) guarded(action) else world.scheduleAfter({ next() }, 50, TimeUnit.MILLISECONDS)
        }
        world.scheduleAfter({ next() }, ticks * 50, TimeUnit.MILLISECONDS)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun guarded(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            check(
                false,
                "hurl exception",
                "$error at ${error.stackTrace.firstOrNull { it.className == javaClass.name }}",
            )
            cleanup()
            complete()
        }
    }

    private fun check(
        passed: Boolean,
        name: String,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
    }
}
