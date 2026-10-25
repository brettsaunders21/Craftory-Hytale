package studio.craftory.tech.machine

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.CompletableFuture

internal class SelfTestRun : Resource<ChunkStore> {
    data class Run(
        val context: CommandContext,
        val world: World,
        val startTick: Long,
        val sectionRef: Ref<ChunkStore>,
        var phase: Int = 0,
        var passed: Int = 0,
        var failed: Int = 0,
        var cleanupTick: Long? = null,
    )

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = SelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, SelfTestRun>
    }
}

internal class SelfTestSystem : TickingSystem<ChunkStore>() {
    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(SelfTestRun.type).active ?: return
        val tick = run.world.tick
        if (!CraftoryCore.isEnergyTick(store)) return
        if (run.phase == 3) {
            if (tick >= (run.cleanupTick ?: Long.MAX_VALUE)) {
                CraftorySelfTest.cleanup(run.world, run.sectionRef)
                store.getResource(SelfTestRun.type).active = null
            }
            return
        }
        when {
            run.phase == 0 && tick >= run.startTick + 8 -> checkCrank(run, store)
            run.phase == 1 && tick >= run.startTick + 40 -> checkBurnerAndRemovePipe(run, store)
            run.phase == 2 && tick >= run.startTick + 44 -> checkSplitAndFinish(run, store)
        }
    }

    private fun checkCrank(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val ref =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.CRANK.x,
                CraftorySelfTest.CRANK.y,
                CraftorySelfTest.CRANK.z,
            )
        val member = ref?.let { store.getComponent(it, EnergyMember.type) }
        check(
            run,
            member?.production?.let { it > 0 } == true,
            "crank pulse",
            "production=${member?.production}",
        )
        run.phase = 1
    }

    private fun checkBurnerAndRemovePipe(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val burnerRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.BURNER.x,
                CraftorySelfTest.BURNER.y,
                CraftorySelfTest.BURNER.z,
            )
        val canisterRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.CANISTER.x,
                CraftorySelfTest.CANISTER.y,
                CraftorySelfTest.CANISTER.z,
            )
        val burner = burnerRef?.let { store.getComponent(it, BurnerState.type) }
        val canister = canisterRef?.let { store.getComponent(it, EnergyMember.type) }
        reportEnergyTopology(run, store)
        check(run, (canister?.stored ?: 0) > 0, "burner energy", "canisterStored=${canister?.stored}")
        check(
            run,
            burner?.visualBurning == true,
            "burner visual",
            "visual=${burner?.visualBurning}, burning=${burner?.burning}",
        )
        if (canisterRef != null) {
            val inspection =
                CraftoryCore
                    .inspectableComponents(store, canisterRef)
                    .joinToString(" ") { (id, component) ->
                        "$id{${component.describe().joinToString(" ") { (key, value) -> "$key=$value" }}}"
                    }
            run.context.sendMessage(Message.raw("selftest: inspect canister $inspection"))
        }
        BlockOperations.setBlock(
            run.world.chunkStore,
            run.sectionRef,
            CraftorySelfTest.PIPE.x,
            CraftorySelfTest.PIPE.y,
            CraftorySelfTest.PIPE.z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            0,
            0,
            SetBlockSettings.NO_DROP_ITEMS,
        )
        run.phase = 2
    }

    private fun reportEnergyTopology(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val burnerMember = memberAt(store, run.sectionRef, CraftorySelfTest.BURNER)
        val pipeMember = memberAt(store, run.sectionRef, CraftorySelfTest.PIPE)
        val canisterMember = memberAt(store, run.sectionRef, CraftorySelfTest.CANISTER)
        val pipeRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.PIPE.x,
                CraftorySelfTest.PIPE.y,
                CraftorySelfTest.PIPE.z,
            )
        val pipeSection =
            run.world.chunkStore.getChunkSectionReferenceAtBlock(
                CraftorySelfTest.PIPE.x,
                CraftorySelfTest.PIPE.y,
                CraftorySelfTest.PIPE.z,
            )
        val pipeSectionRef =
            pipeSection?.let {
                BlockModule.getBlockEntity(
                    store,
                    it,
                    CraftorySelfTest.PIPE.x,
                    CraftorySelfTest.PIPE.y,
                    CraftorySelfTest.PIPE.z,
                )
            }
        val pipeBlockType =
            pipeSection
                ?.let { store.getComponent(it, BlockSection.getComponentType()) }
                ?.get(
                    ChunkUtil.indexBlock(
                        CraftorySelfTest.PIPE.x,
                        CraftorySelfTest.PIPE.y,
                        CraftorySelfTest.PIPE.z,
                    ),
                )?.let { BlockType.getAssetMap().getAsset(it)?.id }
        val pipeComponent = pipeRef?.let { store.getComponent(it, EnergyMember.type) }
        val energy = CraftoryCore.energyAccess(run.world)
        val burnerNetwork = energy.networkOf(pack(CraftorySelfTest.BURNER))
        val pipeNetwork = energy.networkOf(pack(CraftorySelfTest.PIPE))
        val canisterNetwork = energy.networkOf(pack(CraftorySelfTest.CANISTER))
        run.context.sendMessage(
            Message.raw(
                "selftest: energy topology (members=${burnerMember != null}/${pipeMember != null}/" +
                    "${canisterMember != null}, networks=$burnerNetwork/$pipeNetwork/$canisterNetwork, " +
                    "production=${burnerMember?.production}, pipeType=${pipeMember?.typeId}, " +
                    "canisterType=${canisterMember?.typeId}, pipeRefs=${pipeRef != null}/${pipeSectionRef != null}, " +
                    "pipeComponent=${pipeComponent?.typeId}, pipeBlock=$pipeBlockType)",
            ),
        )
    }

    private fun memberAt(
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
    ): EnergyMember? =
        BlockModule
            .getBlockEntity(store, sectionRef, position.x, position.y, position.z)
            ?.let { store.getComponent(it, EnergyMember.type) }

    private fun pack(position: CraftorySelfTest.TestPosition): Long = BlockPos.pack(position.x, position.y, position.z)

    private fun checkSplitAndFinish(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val energy = CraftoryCore.energyAccess(run.world)
        val burnerNetwork =
            energy.networkOf(
                BlockPos.pack(
                    CraftorySelfTest.BURNER.x,
                    CraftorySelfTest.BURNER.y,
                    CraftorySelfTest.BURNER.z,
                ),
            )
        val canisterNetwork =
            energy.networkOf(
                BlockPos.pack(
                    CraftorySelfTest.CANISTER.x,
                    CraftorySelfTest.CANISTER.y,
                    CraftorySelfTest.CANISTER.z,
                ),
            )
        check(
            run,
            burnerNetwork >= 0 && canisterNetwork >= 0 && burnerNetwork != canisterNetwork,
            "pipe split",
            "burner=$burnerNetwork canister=$canisterNetwork",
        )
        val crank =
            BlockModule
                .getBlockEntity(
                    run.world,
                    CraftorySelfTest.CRANK.x,
                    CraftorySelfTest.CRANK.y,
                    CraftorySelfTest.CRANK.z,
                )?.let { store.getComponent(it, EnergyMember.type) }
        check(run, crank?.production == 0L, "crank expiry", "production=${crank?.production}")
        run.context.sendMessage(Message.raw("selftest: ${run.passed} passed, ${run.failed} failed"))
        run.phase = 3
        run.cleanupTick = run.world.tick + 1_000
    }

    private fun check(
        run: SelfTestRun.Run,
        passed: Boolean,
        name: String,
        detail: String,
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
    }
}

object CraftorySelfTest {
    fun execute(context: CommandContext): CompletableFuture<Void> {
        val world = Universe.get().defaultWorld
        if (world == null) {
            context.sendMessage(Message.raw("selftest: FAIL default world is unavailable"))
            return CompletableFuture.completedFuture(null)
        }
        context.sendMessage(Message.raw("selftest: loading isolated area in ${world.name}"))
        world.chunkStore.getChunkSectionReferenceAtBlockAsync(BURNER.x, BURNER.y, BURNER.z).whenComplete {
            sectionRef,
            error,
            ->
            world.execute {
                if (error != null || sectionRef == null) {
                    context.sendMessage(
                        Message.raw("selftest: FAIL chunk load (${error?.cause?.message ?: "chunk load failed"})"),
                    )
                    return@execute
                }
                setup(context, world, sectionRef)
            }
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun setup(
        context: CommandContext,
        world: World,
        sectionRef: Ref<ChunkStore>,
    ) {
        try {
            val store = world.chunkStore.store
            val section =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                    ?: error("block component section is missing")
            clear(world, sectionRef, listOf(BURNER, PIPE, CANISTER, CRANK))
            place(world, store, sectionRef, section, CraftoryIds.BURNER, BURNER, BurnerState.type)
            place(world, store, sectionRef, section, CraftoryIds.PIPE, PIPE, EnergyMember.type)
            place(world, store, sectionRef, section, CraftoryIds.CANISTER, CANISTER, EnergyMember.type)
            place(world, store, sectionRef, section, CraftoryIds.CRANK, CRANK, CrankState.type)

            val burnerRef =
                BlockModule.getBlockEntity(world, BURNER.x, BURNER.y, BURNER.z)
                    ?: error("burner entity missing")
            val burner = store.getComponent(burnerRef, BurnerState.type) ?: error("burner component missing")
            val burnerMember =
                store.getComponent(burnerRef, EnergyMember.type) ?: error("burner energy component missing")
            val burnerBlock =
                store.getComponent(burnerRef, BlockModule.BlockStateInfo.getComponentType())
                    ?: error("burner block state missing")
            check(
                MachineActions.addFuel(
                    burner,
                    burnerMember,
                    burnerBlock,
                    store,
                    world.tick,
                    "Plant_Crop_Mushroom_Common_Brown",
                ) > 0,
            )
            check(MachineActions.kick(burner, burnerMember, burnerBlock, store, world.tick))

            val crankRef = BlockModule.getBlockEntity(world, CRANK.x, CRANK.y, CRANK.z) ?: error("crank entity missing")
            val crank = store.getComponent(crankRef, CrankState.type) ?: error("crank component missing")
            val crankMember = store.getComponent(crankRef, EnergyMember.type) ?: error("crank energy component missing")
            val crankBlock =
                store.getComponent(crankRef, BlockModule.BlockStateInfo.getComponentType())
                    ?: error("crank block state missing")
            check(MachineActions.pulse(crank, crankMember, crankBlock, store, world.tick))
            store.getResource(SelfTestRun.type).active = SelfTestRun.Run(context, world, world.tick, sectionRef)
        } catch (failure: IllegalStateException) {
            context.sendMessage(
                Message.raw("selftest: FAIL setup (${failure.message ?: failure.javaClass.simpleName})"),
            )
            runCatching { clear(world, sectionRef, listOf(BURNER, PIPE, CANISTER, CRANK)) }
        }
    }

    private fun place(
        world: World,
        store: Store<ChunkStore>,
        sectionRef: Ref<ChunkStore>,
        section: BlockComponentSection,
        blockId: String,
        position: TestPosition,
        component: com.hypixel.hytale.component.ComponentType<
            ChunkStore,
            out com.hypixel.hytale.component.Component<ChunkStore>,
        >,
    ) {
        val blockType = BlockType.fromString(blockId) ?: error("missing block type $blockId")
        val index = BlockType.getAssetMap().getIndex(blockType.id)
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                index,
                blockType,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            ),
        ) { "failed to place $blockId" }
        val created =
            BlockEntity.ensureBlockEntity(
                store,
                sectionRef,
                section,
                position.x and 31,
                position.y and 31,
                position.z and 31,
                blockType,
                0,
            ) ?: error("ensureBlockEntity failed for $blockId")
        check(BlockEntity.declaresComponent(blockType, component)) { "$blockId component template missing" }
        check(BlockModule.getBlockEntity(world, position.x, position.y, position.z) == created) {
            "block entity lookup mismatch for $blockId"
        }
    }

    private fun clear(
        world: World,
        sectionRef: Ref<ChunkStore>,
        positions: List<TestPosition>,
    ) {
        positions.forEach { position ->
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            )
        }
    }

    internal fun cleanup(
        world: World,
        sectionRef: Ref<ChunkStore>,
    ) {
        listOf(BURNER, PIPE, CANISTER, CRANK).forEach { position ->
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            )
        }
    }

    internal data class TestPosition(
        val x: Int,
        val y: Int,
        val z: Int,
    )

    internal val BURNER = TestPosition(10_002, 148, 10_000)
    internal val PIPE = TestPosition(10_002, 149, 10_000)
    internal val CANISTER = TestPosition(10_002, 150, 10_000)
    internal val CRANK = TestPosition(10_010, 150, 10_000)
}
