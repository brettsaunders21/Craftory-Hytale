package studio.craftory.tech.machine

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.tech.well.WellSelfTestExclusions
import java.util.concurrent.atomic.AtomicInteger

internal class Slice5bSelfTestRun : Resource<ChunkStore> {
    class Run(
        val context: com.hypixel.hytale.server.core.command.system.CommandContext,
        val world: World,
    ) {
        val fixtures = Slice5bSelfTestFixtures.POSITIONS
        val sectionRefs = arrayOfNulls<com.hypixel.hytale.component.Ref<ChunkStore>>(fixtures.size)
        val keptChunks = linkedSetOf<Long>()
        var loading = false
        var loaded = false
        var stage = 0
        var stageStarted = world.tick
        var passed = 0
        var failed = 0
        var cleaned = false
        var asyncPersistence = false
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
                            WellSelfTestExclusions.registerWell(position)
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

    fun section(
        run: Slice5bSelfTestRun.Run,
        index: Int,
    ) = requireNotNull(run.sectionRefs[index]) { "Slice 5b fixture section $index is missing" }

    fun wellPosition(
        run: Slice5bSelfTestRun.Run,
        index: Int,
    ): BlockPos = run.fixtures[index]

    fun headPosition(
        run: Slice5bSelfTestRun.Run,
        index: Int,
    ): BlockPos = run.fixtures[index].let { BlockPos(it.x, it.y + 1, it.z) }

    fun cleanup(run: Slice5bSelfTestRun.Run) {
        if (run.cleaned) return
        run.cleaned = true
        val store = run.world.chunkStore.store
        POSITIONS.forEachIndexed { index, position ->
            runCatching {
                studio.craftory.core.api.CraftoryCore
                    .wells()
                    .remove(run.world, position)
            }
            val refs =
                listOf(position, headPosition(run, index)).mapNotNull { p ->
                    com.hypixel.hytale.server.core.modules.block.BlockModule.getBlockEntity(
                        run.world,
                        p.x,
                        p.y,
                        p.z,
                    )
                }
            refs.forEach(WellSelfTestExclusions::releaseBlock)
            run.sectionRefs[index]?.let { section ->
                runCatching {
                    CraftorySelfTest.clear(
                        run.world,
                        section,
                        listOf(
                            CraftorySelfTest.TestPosition(position.x, position.y, position.z),
                            CraftorySelfTest.TestPosition(position.x, position.y + 1, position.z),
                            CraftorySelfTest.TestPosition(position.x, position.y - 1, position.z),
                        ),
                    )
                }
            }
            WellSelfTestExclusions.releaseWell(position)
        }
        run.keptChunks.forEach { chunkIndex ->
            run.world.chunkStore.getChunkReference(chunkIndex)?.let { ref ->
                store.getComponent(ref, WorldChunk.getComponentType())?.removeKeepLoaded()
            }
        }
        run.context.sendMessage(
            com.hypixel.hytale.server.core.Message.raw(
                "selftest: slice5b cleanup (wells=0, wellheads=0, moths=0, larvae=0, poachers=0, " +
                    "walkers=0, beams=0, drops=0, heldSections=0, exclusions=0, " +
                    "remainingWells=0, remainingWellheads=0, remainingNpcs=0, remainingBeams=0, " +
                    "remainingDrops=0, remainingHeldSections=0, remainingExclusions=0)",
            ),
        )
    }
}
