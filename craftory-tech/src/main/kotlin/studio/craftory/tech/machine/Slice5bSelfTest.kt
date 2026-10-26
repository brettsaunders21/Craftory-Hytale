package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.tech.well.WellAffliction
import studio.craftory.tech.well.WellAfflictionFollower
import studio.craftory.tech.well.WellSelfTestExclusions
import studio.craftory.tech.well.feedBeams
import studio.craftory.tech.well.siphonBeams
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer

internal class Slice5bSelfTestRun : Resource<ChunkStore> {
    class Run(
        val context: com.hypixel.hytale.server.core.command.system.CommandContext,
        val world: World,
    ) {
        val fixtures = Slice5bSelfTestFixtures.POSITIONS
        val sectionRefs = arrayOfNulls<com.hypixel.hytale.component.Ref<ChunkStore>>(fixtures.size)
        val keptChunks = linkedSetOf<Long>()
        val fixtureCoverageIds = linkedSetOf<Long>()
        val cleanupWellPositions = fixtures.toMutableSet()
        val cleanupBlockPositions = linkedSetOf<BlockPos>()
        val excludedBlockRefs = linkedSetOf<Ref<ChunkStore>>()
        val syntheticPlayers = linkedSetOf<Ref<EntityStore>>()
        val spawnedNpcKinds = linkedMapOf<String, String>()
        val removedNpcIds = linkedSetOf<String>()
        val removedNpcs =
            mutableMapOf(
                "moths" to 0,
                "voidMobs" to 0,
                "poachers" to 0,
                "walkers" to 0,
            )
        var removedAfflictions = 0
        var removedBeams = 0
        var exclusionsReleased = 0
        var loading = false
        var loaded = false
        var stage = 0
        var stageStarted = world.tick
        var passed = 0
        var failed = 0
        var cleaned = false
        var asyncPersistence = false
        var catalysisStarted = false
        var syntheticPlayersRemoved = 0
    }

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = Slice5bSelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, Slice5bSelfTestRun>
    }
}

internal class Slice5bSelfTestSystem : TickingSystem<ChunkStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(Slice5bSelfTestRun.type).active ?: return
        if (!run.loaded) {
            if (!run.loading) Slice5bSelfTestFixtures.load(run)
            return
        }
        if (run.asyncPersistence) {
            if (run.world.tick - run.stageStarted > PERSISTENCE_TIMEOUT_TICKS) {
                Slice5bSelfTestSupport.timeout(run, "TAPPED Well reload", "Corrupted Well reload")
                run.asyncPersistence = false
                run.stage = Slice5bSelfTestChecks.CHECK_COUNT
            }
            return
        }
        if (run.stage == Slice5bSelfTestChecks.CATALYSIS_STAGE) {
            if (!run.catalysisStarted) {
                val result = runCatching { Slice5bCatalysisCheck.start(run) }
                if (result.isFailure) {
                    Slice5bCatalysisCheck.fail(
                        run,
                        result.exceptionOrNull()?.message ?: "fixture setup failed",
                    )
                    run.stage++
                    run.stageStarted = run.world.tick
                } else {
                    run.catalysisStarted = true
                    run.stageStarted = run.world.tick
                }
            } else if (run.world.tick - run.stageStarted >= Slice5bSelfTestChecks.CATALYSIS_TICKS) {
                Slice5bCatalysisCheck.finish(run)
                run.catalysisStarted = false
                run.stage++
                run.stageStarted = run.world.tick
            }
            return
        }
        if (run.stage < Slice5bSelfTestChecks.CHECK_COUNT) {
            if (run.stage == Slice5bSelfTestChecks.PERSISTENCE_STAGE) {
                run.asyncPersistence = true
                run.stageStarted = run.world.tick
                Slice5bSelfTestSupport.runPersistence(run) {
                    run.asyncPersistence = false
                    run.stage += 2
                    run.stageStarted = run.world.tick
                }
                return
            }
            val started = run.world.tick
            Slice5bSelfTestChecks.run(run, run.stage)
            run.stage++
            run.stageStarted = started
            return
        }
        Slice5bSelfTestFixtures.cleanup(run)
        run.context.sendMessage(Message.raw("selftest: slice5b ${run.passed} passed, ${run.failed} failed"))
        store.getResource(Slice5bSelfTestRun.type).active = null
    }

    companion object {
        private const val PERSISTENCE_TIMEOUT_TICKS = 6_000L
    }
}

internal object Slice5bSelfTest {
    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
    ) {
        world.execute {
            val resource = world.chunkStore.store.getResource(Slice5bSelfTestRun.type)
            if (resource.active != null) return@execute
            resource.active = Slice5bSelfTestRun.Run(context, world)
            context.sendMessage(Message.raw("selftest: starting slice5b checks"))
        }
    }
}

internal object Slice5bSelfTestFixtures {
    val POSITIONS =
        listOf(10_600, 10_632, 10_664, 10_696, 10_728, 10_760)
            .map { BlockPos(it, 150, 10_000) }

    fun load(run: Slice5bSelfTestRun.Run) {
        run.loading = true
        val completed = AtomicInteger()
        POSITIONS.forEachIndexed { index, position ->
            run.world.chunkStore
                .getChunkSectionReferenceAtBlockAsync(
                    position.x,
                    position.y,
                    position.z,
                    GetChunkFlags.SET_TICKING,
                ).whenComplete { ref, failure ->
                    run.world.execute {
                        if (failure != null || ref == null || !ref.isValid) {
                            run.context.sendMessage(
                                Message.raw(
                                    "selftest: FAIL 5b fixture load x=${position.x} " +
                                        "(timeoutTicks=6000, reason=${failure?.message ?: "invalid section"})",
                                ),
                            )
                            run.failed++
                        } else {
                            run.sectionRefs[index] = ref
                            val chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z)
                            val chunkRef = run.world.chunkStore.getChunkReference(chunkIndex)
                            if (chunkRef != null && run.keptChunks.add(chunkIndex)) {
                                run.world.chunkStore.store
                                    .getComponent(chunkRef, WorldChunk.getComponentType())
                                    ?.addKeepLoaded()
                            }
                            val coverageId = position.pack()
                            CraftoryCore.setAnchorCoverage(
                                run.world.chunkStore.store,
                                coverageId,
                                position.x shr 5,
                                position.z shr 5,
                                true,
                            )
                            run.fixtureCoverageIds.add(coverageId)
                            WellSelfTestExclusions.registerWell(position)
                            run.cleanupWellPositions.add(position)
                        }
                        if (completed.incrementAndGet() == POSITIONS.size) {
                            run.loaded = run.sectionRefs.all { it?.isValid == true }
                            if (!run.loaded) {
                                Slice5bSelfTestSupport.failAll(run, "fixture loading failed")
                                run.stage = Slice5bSelfTestChecks.CHECK_COUNT
                            }
                        }
                    }
                }
        }
    }

    fun cleanup(run: Slice5bSelfTestRun.Run) {
        if (run.cleaned) return
        run.cleaned = true
        val store = run.world.chunkStore.store
        val blockPositions = run.cleanupBlockPositions + run.cleanupWellPositions
        val blockRefs = run.excludedBlockRefs
        blockPositions.forEach { position ->
            BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)?.let(blockRefs::add)
        }
        val wellsRemoved = removeWellsAndAfflictions(run, store)
        val afflictionsRemoved = run.removedAfflictions
        removeTrackedNpcs(run)
        val blocksRemoved = blockPositions.count { isOccupied(run, it) }
        run.exclusionsReleased =
            blockRefs.count(WellSelfTestExclusions::isBlock) +
            run.syntheticPlayers.count(WellSelfTestExclusions::isEntity) +
            run.cleanupWellPositions.count(WellSelfTestExclusions::isWell)
        blockRefs.forEach(WellSelfTestExclusions::releaseBlock)
        run.syntheticPlayers.forEach { entity ->
            if (entity.isValid) storeSyntheticPlayer(run, entity)
            if (!entity.isValid) run.syntheticPlayersRemoved++
            WellSelfTestExclusions.releaseEntity(entity)
        }
        run.cleanupWellPositions.forEach(WellSelfTestExclusions::releaseWell)
        clearBlocks(run, blockPositions)
        run.fixtureCoverageIds.forEach { CraftoryCore.removeAnchorCoverage(store, it) }
        run.keptChunks.forEach { chunkIndex ->
            run.world.chunkStore.getChunkReference(chunkIndex)?.let { ref ->
                store.getComponent(ref, WorldChunk.getComponentType())?.removeKeepLoaded()
            }
        }
        Slice5bSelfTestCleanup.reportResiduals(
            run,
            blockPositions,
            blockRefs,
            blocksRemoved,
            wellsRemoved,
            afflictionsRemoved,
        )
    }

    internal fun recordNpc(
        run: Slice5bSelfTestRun.Run,
        id: String,
        kind: String,
    ) {
        run.spawnedNpcKinds[id] = kind
    }

    internal fun deferSyntheticPlayerRemoval(
        run: Slice5bSelfTestRun.Run,
        ref: Ref<EntityStore>,
    ) {
        run.syntheticPlayers.add(ref)
        WellSelfTestExclusions.registerEntity(ref)
        run.world.execute {
            if (ref.isValid) {
                run.world.entityStore.store.removeEntity(
                    ref,
                    com.hypixel.hytale.component.RemoveReason.REMOVE,
                )
            }
        }
    }

    private fun removeWellsAndAfflictions(
        run: Slice5bSelfTestRun.Run,
        store: Store<ChunkStore>,
    ): Int {
        var removed = 0
        run.cleanupWellPositions.forEach { position ->
            val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
            if (ref != null && store.getComponent(ref, WellAffliction.type) != null) {
                store.removeComponent(ref, WellAffliction.type)
                run.removedAfflictions++
            }
            if (ref != null && store.getComponent(ref, Glimmerwell.type) != null) {
                CraftoryCore.wells().remove(run.world, position)
                removed++
            }
        }
        return removed
    }

    private fun removeTrackedNpcs(run: Slice5bSelfTestRun.Run): Int {
        trackedFollowers(run).forEach { (ref, follower) ->
            val uuid =
                run.world.entityStore.store
                    .getComponent(ref, UUIDComponent.getComponentType())
                    ?.uuid
                    ?.toString()
                    ?: return@forEach
            run.spawnedNpcKinds.putIfAbsent(uuid, follower.kind)
        }
        run.spawnedNpcKinds.forEach { (raw, kind) ->
            val uuid = runCatching { java.util.UUID.fromString(raw) }.getOrNull() ?: return@forEach
            val ref = run.world.entityStore.getRefFromUUID(uuid)
            if (ref?.isValid == true) {
                run.removedBeams += removeBeams(run, uuid)
                run.world.entityStore.store
                    .removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE)
            }
            if (run.removedNpcIds.add(raw)) {
                val key =
                    when (kind) {
                        WellAfflictionFollower.MOTH -> "moths"
                        WellAfflictionFollower.VOID -> "voidMobs"
                        WellAfflictionFollower.POACHER -> "poachers"
                        WellAfflictionFollower.WALKER -> "walkers"
                        else -> return@forEach
                    }
                run.removedNpcs[key] = (run.removedNpcs[key] ?: 0) + 1
            }
        }
        return run.removedNpcIds.size
    }

    private fun removeBeams(
        run: Slice5bSelfTestRun.Run,
        uuid: java.util.UUID,
    ): Int {
        val feed = feedBeams.remove(uuid)?.takeIf { it.isValid }
        val siphon = siphonBeams.remove(uuid)?.takeIf { it.isValid }
        if (feed !=
            null
        ) {
            run.world.entityStore.store
                .removeEntity(feed, com.hypixel.hytale.component.RemoveReason.REMOVE)
        }
        if (siphon !=
            null
        ) {
            run.world.entityStore.store
                .removeEntity(siphon, com.hypixel.hytale.component.RemoveReason.REMOVE)
        }
        return listOfNotNull(feed, siphon).size
    }

    internal fun trackedFollowers(run: Slice5bSelfTestRun.Run): List<Pair<Ref<EntityStore>, WellAfflictionFollower>> {
        val positions = run.cleanupWellPositions.mapTo(HashSet()) { it.pack() }
        val result = mutableListOf<Pair<Ref<EntityStore>, WellAfflictionFollower>>()
        run.world.entityStore.store.forEachChunk(
            Query.and(WellAfflictionFollower.type),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val follower = chunk.getComponent(index, WellAfflictionFollower.type) ?: continue
                    if (follower.wellPosition in positions) result.add(chunk.getReferenceTo(index) to follower)
                }
            },
        )
        return result
    }

    internal fun isOccupied(
        run: Slice5bSelfTestRun.Run,
        position: BlockPos,
    ): Boolean {
        val sectionRef =
            run.world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z) ?: return false
        val section =
            run.world.chunkStore.store
                .getComponent(sectionRef, BlockSection.getComponentType()) ?: return false
        return section.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
    }

    private fun clearBlocks(
        run: Slice5bSelfTestRun.Run,
        positions: Set<BlockPos>,
    ) {
        positions.groupBy { ChunkUtil.indexChunkFromBlock(it.x, it.z) }.values.forEach { group ->
            val first = group.first()
            val section =
                run.world.chunkStore.getChunkSectionReferenceAtBlock(first.x, first.y, first.z) ?: return@forEach
            CraftorySelfTest.clear(
                run.world,
                section,
                group.map { CraftorySelfTest.TestPosition(it.x, it.y, it.z) },
            )
        }
    }

    private fun storeSyntheticPlayer(
        run: Slice5bSelfTestRun.Run,
        ref: Ref<EntityStore>,
    ) {
        run.world.entityStore.store
            .removeEntity(ref, com.hypixel.hytale.component.RemoveReason.REMOVE)
    }
}

internal object Slice5bSelfTestCleanup {
    fun reportResiduals(
        run: Slice5bSelfTestRun.Run,
        blockPositions: Set<BlockPos>,
        blockRefs: Set<Ref<ChunkStore>>,
        blocksRemoved: Int,
        wellsRemoved: Int,
        afflictionsRemoved: Int,
    ) {
        val store = run.world.chunkStore.store
        val remainingBlocks = blockPositions.count { Slice5bSelfTestFixtures.isOccupied(run, it) }
        val remainingWells =
            run.cleanupWellPositions.count { position ->
                val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
                ref != null && store.getComponent(ref, Glimmerwell.type) != null
            }
        val remainingAfflictions =
            run.cleanupWellPositions.count { position ->
                val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
                ref != null && store.getComponent(ref, WellAffliction.type) != null
            }
        val remainingNpcs = Slice5bSelfTestFixtures.trackedFollowers(run).size
        val remainingExclusions =
            blockRefs.count(WellSelfTestExclusions::isBlock) +
                run.syntheticPlayers.count(WellSelfTestExclusions::isEntity) +
                run.cleanupWellPositions.count(WellSelfTestExclusions::isWell)
        if (remainingBlocks + remainingWells + remainingNpcs + remainingAfflictions + remainingExclusions > 0) {
            run.failed++
        }
        val message =
            "selftest: slice5b cleanup (blocks=$blocksRemoved, wells=$wellsRemoved, " +
                "moths=${run.removedNpcs["moths"]}, voidMobs=${run.removedNpcs["voidMobs"]}, " +
                "poachers=${run.removedNpcs["poachers"]}, walkers=${run.removedNpcs["walkers"]}, " +
                "syntheticPlayers=${run.syntheticPlayersRemoved}, afflictions=$afflictionsRemoved, " +
                "beams=${run.removedBeams}, exclusionsReleased=${run.exclusionsReleased}, " +
                "fixtureCoverageRemoved=${run.fixtureCoverageIds.size}, " +
                "remainingBlocks=$remainingBlocks, remainingWells=$remainingWells, " +
                "remainingNpcs=$remainingNpcs, remainingAfflictions=$remainingAfflictions, " +
                "remainingExclusions=$remainingExclusions)"
        run.context.sendMessage(Message.raw(message))
    }
}
