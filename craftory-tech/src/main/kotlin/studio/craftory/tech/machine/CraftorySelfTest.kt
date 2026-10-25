package studio.craftory.tech.machine

import com.hypixel.hytale.builtin.crafting.component.BenchBlock
import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.dependency.Dependency
import com.hypixel.hytale.component.dependency.Order
import com.hypixel.hytale.component.dependency.SystemDependency
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.EnergySolveSystem
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPages
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

internal class SelfTestRun : Resource<ChunkStore> {
    data class Run(
        val context: CommandContext,
        val world: World,
        val startTick: Long,
        val sectionRef: Ref<ChunkStore>,
        var sectionEntityTicking: Boolean = true,
        var tickingStateReported: Boolean = false,
        var ovenCompletionTickOffset: Long = 360,
        var phase: Int = 0,
        var passed: Int = 0,
        var failed: Int = 0,
        var finalReported: Boolean = false,
        var stoppedReported: Boolean = false,
        var cleanupTick: Long? = null,
        var powerLossTick: Long? = null,
        var ovenProgressAtSixSeconds: Float? = null,
        var ovenInputAtSixSeconds: String? = null,
        var ovenOutputAtSixSeconds: String? = null,
        var spawnedGritDrops: Int = 0,
        var spawnedGritQuantity: Int = 0,
        var gnasherBaselineQuantity: Int = 0,
        var gnasherBaselineParkedQuantity: Int = 0,
        var crankSpinWithoutAnchor: Long = 0,
        var anchorInitialCharge: Long = 0,
        var anchorDisableTick: Long? = null,
        var anchorDisableStartedTick: Long? = null,
        var anchorDisabledReported: Boolean = false,
        var anchorDisablePrepared: Boolean = false,
        var anchorSpinAtInactive: Long? = null,
        var anchorMaxChargeBeforeTest: Int = CraftoryCore.anchorMaxChargeMinutes(),
        val testKeepLoadedChunks: MutableSet<Long> = mutableSetOf(),
        var anchorStateForTest: AnchorState? = null,
    )

    var active: Run? = null

    override fun clone(): Resource<ChunkStore> = SelfTestRun()

    companion object {
        lateinit var type: ResourceType<ChunkStore, SelfTestRun>
    }
}

internal data class SelfTestCleanupResult(
    val liveDrops: Int,
    val sectionHolders: Int,
    val chunkHolders: Int,
)

private fun reportCleanup(
    run: SelfTestRun.Run,
    result: SelfTestCleanupResult,
) {
    run.context.sendMessage(
        Message.raw(
            "selftest: cleanup (liveGrit=${result.liveDrops}, " +
                "sectionHolders=${result.sectionHolders}, chunkHolders=${result.chunkHolders})",
        ),
    )
}

private fun abort(
    run: SelfTestRun.Run,
    reason: String,
) {
    CraftoryCore.setAnchorMaxChargeMinutesInMemory(run.anchorMaxChargeBeforeTest)
    runCatching { CraftorySelfTest.releaseTestKeepLoaded(run) }
    runCatching { CraftorySelfTest.cleanup(run.world, run.sectionRef) }
        .onSuccess { reportCleanup(run, it) }
        .onFailure { failure ->
            run.context.sendMessage(Message.raw("selftest: cleanup failed (${failure.message})"))
        }
    run.world.chunkStore.store
        .getResource(SelfTestRun.type)
        .active = null
    run.context.sendMessage(Message.raw("selftest: FAIL $reason"))
}

@Suppress("TooManyFunctions", "LargeClass")
internal class SelfTestSystem : TickingSystem<ChunkStore>() {
    override fun getDependencies(): Set<Dependency<ChunkStore>> =
        setOf(SystemDependency(Order.AFTER, EnergySolveSystem::class.java))

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<ChunkStore>,
    ) {
        val run = store.getResource(SelfTestRun.type).active ?: return
        val tick = run.world.tick
        if (tick >= run.startTick + 2_400L) {
            abort(run, "timed out")
            return
        }
        run.sectionEntityTicking =
            store.getComponent(
                run.sectionRef,
                ChunkStore.REGISTRY.getNonTickingComponentType(),
            ) == null
        if (!CraftoryCore.isEnergyTick(store)) return
        reportTickingState(run, tick)
        if (run.phase == 4) {
            try {
                tickPowerLossPhase(run, store, tick)
            } catch (failure: Exception) {
                abort(run, "exception (${failure.message})")
            }
            return
        }
        if (run.phase == 5) {
            try {
                tickAnchorDisablePhase(run, store, tick)
            } catch (failure: Exception) {
                abort(run, "exception (${failure.message})")
            }
            return
        }
        try {
            tickSelfTestPhase(run, store, tick)
        } catch (failure: Exception) {
            abort(run, "exception (${failure.message})")
        }
    }

    private fun reportTickingState(
        run: SelfTestRun.Run,
        tick: Long,
    ) {
        if (run.tickingStateReported || tick < run.startTick + 8) return
        run.tickingStateReported = true
        if (run.sectionEntityTicking) {
            check(run, true, "selftest section entity-ticking", "NonTicking absent")
        } else {
            run.context.sendMessage(
                Message.raw("selftest: SKIP isolated section ticking is player-dependent (NonTicking)"),
            )
        }
    }

    private fun tickPowerLossPhase(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        tick: Long,
    ) {
        val powerLossTick = run.powerLossTick ?: return
        if (!run.finalReported && tick >= powerLossTick + 120) {
            if (run.sectionEntityTicking) {
                captureOvenAfterPowerLoss(run, store)
            } else {
                run.context.sendMessage(Message.raw("selftest: SKIP oven power-loss buffer check (NonTicking)"))
            }
            run.finalReported = true
            run.cleanupTick = tick + 20
        } else if (
            run.finalReported &&
            !run.stoppedReported &&
            tick >= (run.cleanupTick ?: Long.MAX_VALUE) - 12
        ) {
            if (run.sectionEntityTicking) {
                checkOvenStopped(run, store)
            } else {
                run.context.sendMessage(Message.raw("selftest: SKIP oven stop-after-power-loss check (NonTicking)"))
            }
            run.stoppedReported = true
            run.anchorDisableTick = tick + 1
            run.anchorDisableStartedTick = tick + 1
            run.phase = 5
            run.cleanupTick = tick + 100
        }
    }

    private fun tickSelfTestPhase(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        tick: Long,
    ) {
        when {
            run.phase == 0 && tick >= run.startTick + 8 -> {
                checkCrank(run, store)
            }

            run.phase == 1 && tick >= run.startTick + 40 -> {
                checkBurnerAndOvenCharge(run, store)
            }

            run.phase == 2 && tick >= run.startTick + run.ovenCompletionTickOffset -> {
                checkPoweredOvenOutputAndDisconnect(run, store)
            }

            run.phase == 3 && tick >= run.startTick + 124 -> {
                checkSplitAndFinish(run, store)
            }
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
        val crank = ref?.let { store.getComponent(it, CrankState.type) }
        val blockState = ref?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        if (run.sectionEntityTicking) {
            run.context.sendMessage(Message.raw("selftest: SKIP crank pause without Anchor (section already ticking)"))
        } else {
            val paused =
                blockState != null &&
                    !CraftoryCore.isAreaActive(store, blockState) &&
                    crank?.spinTicks == run.crankSpinWithoutAnchor
            check(
                run,
                paused,
                "crank paused without Anchor",
                "sectionTicking=${run.sectionEntityTicking}, spin=${crank?.spinTicks}",
            )
        }
        check(
            run,
            member?.production?.let { it > 0 } == true,
            "crank pulse",
            "production=${member?.production}",
        )
        val anchorRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.ANCHOR.x,
                CraftorySelfTest.ANCHOR.y,
                CraftorySelfTest.ANCHOR.z,
            ) ?: return
        val anchor = store.getComponent(anchorRef, AnchorState.type) ?: return
        run.anchorStateForTest = anchor
        val anchorBlock = store.getComponent(anchorRef, BlockModule.BlockStateInfo.getComponentType()) ?: return
        anchor.chargeSeconds = 900
        anchorInitialCharge(run, anchor.chargeSeconds)
        MachineActions.activateAnchor(
            run.world,
            anchor,
            anchorBlock,
            store,
            BlockPos(CraftorySelfTest.ANCHOR.x, CraftorySelfTest.ANCHOR.y, CraftorySelfTest.ANCHOR.z),
        )
        check(run, CraftoryCore.isAreaActive(store, blockState ?: anchorBlock), "anchor activates Craftory area")
        run.phase = 1
        CraftorySelfTest.startGnasherSequence(run, store)
    }

    private fun anchorInitialCharge(
        run: SelfTestRun.Run,
        chargeSeconds: Long,
    ) {
        run.anchorInitialCharge = chargeSeconds
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun checkBurnerAndOvenCharge(
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
        check(
            run,
            run.anchorStateForTest?.keepLoadedChunks?.size == 9,
            "anchor covers all nine chunks",
            "keepLoaded=${run.anchorStateForTest?.keepLoadedChunks?.size}",
        )
        check(run, (canister?.stored ?: 0) > 0, "burner energy", "canisterStored=${canister?.stored}")
        check(
            run,
            burner?.visualBurning == true,
            "burner visual",
            "visual=${burner?.visualBurning}, burning=${burner?.burning}",
        )
        val anchorRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.ANCHOR.x,
                CraftorySelfTest.ANCHOR.y,
                CraftorySelfTest.ANCHOR.z,
            )
        val anchorCharge = anchorRef?.let { store.getComponent(it, AnchorState.type)?.chargeSeconds }
        check(
            run,
            anchorCharge != null && anchorCharge < run.anchorInitialCharge,
            "anchor drains while players are away",
            "initial=${run.anchorInitialCharge}, remaining=$anchorCharge",
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
        if (!run.sectionEntityTicking) {
            run.context.sendMessage(Message.raw("selftest: SKIP oven solve-path charge and activation (NonTicking)"))
            run.ovenCompletionTickOffset = 48
            run.phase = 2
            return
        }
        val ovenPosition = CraftorySelfTest.OVEN
        val ovenRef = BlockModule.getBlockEntity(run.world, ovenPosition.x, ovenPosition.y, ovenPosition.z)
        val bench = ovenRef?.let { store.getComponent(it, ProcessingBenchBlock.getComponentType()) }
        val power = ovenRef?.let { store.getComponent(it, OvenPower.type) }
        val ovenMember = ovenRef?.let { store.getComponent(it, EnergyMember.type) }
        val energy = CraftoryCore.energyAccess(run.world)
        val burnerNetwork = energy.networkOf(pack(CraftorySelfTest.BURNER))
        val ovenNetwork = energy.networkOf(pack(ovenPosition))
        val fuel = bench?.fuelContainer?.getItemStack(0.toShort())
        val chargeAvailable =
            (fuel != null && fuel.itemId == CraftoryIds.FIZZ_CHARGE && fuel.quantity > 0) ||
                (bench?.fuelTime ?: 0f) > 0f
        val solverPathUsed = power?.lastFizzSolveTick?.let { it > run.startTick } == true
        val poweredNetwork = burnerNetwork >= 0 && ovenNetwork == burnerNetwork
        if (!(chargeAvailable && solverPathUsed && poweredNetwork)) {
            check(
                run,
                false,
                "oven solve-path Fizz Charge insertion",
                "oven charge insertion failed " +
                    "(fuel=${fuel?.itemId}x${fuel?.quantity}, fuelTime=${bench?.fuelTime}, " +
                    "lastSolve=${power?.lastFizzSolveTick}, delivered=${ovenMember?.delivered}, " +
                    "networks=$burnerNetwork/$ovenNetwork)",
            )
            run.phase = 2
            return
        }
        check(
            run,
            true,
            "oven solve-path Fizz Charge insertion",
            "fuel=${fuel?.itemId}x${fuel?.quantity}, fuelTime=${bench?.fuelTime}, " +
                "lastSolve=${power?.lastFizzSolveTick}, delivered=${ovenMember?.delivered}, network=$ovenNetwork",
        )
        val benchBlock = ovenRef?.let { store.getComponent(it, BenchBlock.getComponentType()) }
        val blockState = ovenRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        if (bench != null && benchBlock != null && blockState != null) {
            bench.setActive(true, benchBlock, blockState)
        }
        check(run, bench?.isActive() == true, "oven vanilla processing activated", "active=${bench?.isActive()}")
        run.phase = 2
    }

    private fun checkPoweredOvenOutputAndDisconnect(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val ovenPosition = CraftorySelfTest.OVEN
        val ref = BlockModule.getBlockEntity(run.world, ovenPosition.x, ovenPosition.y, ovenPosition.z)
        val bench = ref?.let { store.getComponent(it, ProcessingBenchBlock.getComponentType()) }
        val output = bench?.outputContainer?.getItemStack(0.toShort())
        val input = bench?.inputContainer?.getItemStack(0.toShort())
        if (run.sectionEntityTicking) {
            check(
                run,
                output?.itemId == "Ingredient_Bar_Copper" && (output?.quantity ?: 0) > 0,
                "powered oven processes copper",
                "active=${bench?.isActive()}, input=${input?.itemId}x${input?.quantity}, " +
                    "output=${output?.itemId}x${output?.quantity}, " +
                    "progress=${bench?.inputProgress}, fuelTime=${bench?.fuelTime}",
            )
        } else {
            run.context.sendMessage(Message.raw("selftest: SKIP oven copper completion (NonTicking)"))
        }
        val link = CraftorySelfTest.OVEN_LINK_PIPE
        BlockOperations.setBlock(
            run.world.chunkStore,
            run.sectionRef,
            link.x,
            link.y,
            link.z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            0,
            0,
            SetBlockSettings.NO_DROP_ITEMS,
        )
        run.phase = 3
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
            burnerNetwork >= 0 && canisterNetwork >= 0 && burnerNetwork == canisterNetwork,
            "pipe split",
            "burner=$burnerNetwork canister=$canisterNetwork",
        )
        val ovenNetwork = energy.networkOf(pack(CraftorySelfTest.OVEN))
        check(run, ovenNetwork >= 0 && ovenNetwork != canisterNetwork, "oven isolated from supply", "oven=$ovenNetwork")
        val crank =
            BlockModule
                .getBlockEntity(
                    run.world,
                    CraftorySelfTest.CRANK.x,
                    CraftorySelfTest.CRANK.y,
                    CraftorySelfTest.CRANK.z,
                )?.let { store.getComponent(it, EnergyMember.type) }
        check(run, crank?.production == 0L, "crank expiry", "production=${crank?.production}")
        reportInspectable(run, store, CraftorySelfTest.OVEN, "oven")
        reportInspectable(run, store, CraftorySelfTest.GNASHER, "gnasher")
        reportInspectable(run, store, CraftorySelfTest.ANCHOR, "anchor")
        CraftorySelfTest.testWrench(run, store)
        run.phase = 4
        run.powerLossTick = run.world.tick
    }

    private fun captureOvenAfterPowerLoss(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val position = CraftorySelfTest.OVEN
        val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
        val bench = ref?.let { store.getComponent(it, ProcessingBenchBlock.getComponentType()) }
        val input = bench?.inputContainer?.getItemStack(0.toShort())
        run.ovenProgressAtSixSeconds = bench?.inputProgress
        run.ovenInputAtSixSeconds = stackDescription(input)
        run.ovenOutputAtSixSeconds = stackDescription(bench?.outputContainer?.getItemStack(0.toShort()))
        check(
            run,
            bench != null && !bench.isActive(),
            "oven stops within six seconds after power loss",
            "active=${bench?.isActive()}, fuelTime=${bench?.fuelTime}",
        )
    }

    private fun checkOvenStopped(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val position = CraftorySelfTest.OVEN
        val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
        val bench = ref?.let { store.getComponent(it, ProcessingBenchBlock.getComponentType()) }
        val input = stackDescription(bench?.inputContainer?.getItemStack(0.toShort()))
        val output = stackDescription(bench?.outputContainer?.getItemStack(0.toShort()))
        val progress = bench?.inputProgress
        val stopped =
            bench != null &&
                !bench.isActive() &&
                input == run.ovenInputAtSixSeconds &&
                output == run.ovenOutputAtSixSeconds &&
                progress != null &&
                kotlin.math.abs(progress - (run.ovenProgressAtSixSeconds ?: Float.NaN)) < 0.001f
        check(
            run,
            stopped,
            "oven stops after six seconds unpowered",
            "fuelTime=${bench?.fuelTime}, progress=$progress, input=$input, output=$output",
        )
    }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "LongMethod")
    private fun tickAnchorDisablePhase(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        tick: Long,
    ) {
        val disableTick = run.anchorDisableTick ?: return
        if (run.anchorDisabledReported) {
            if (tick >= (run.cleanupTick ?: Long.MAX_VALUE)) {
                CraftoryCore.setAnchorMaxChargeMinutesInMemory(run.anchorMaxChargeBeforeTest)
                reportCleanup(run, CraftorySelfTest.cleanup(run.world, run.sectionRef))
                store.getResource(SelfTestRun.type).active = null
                TransportSelfTest.start(run.context, run.world)
            }
            return
        }
        if (tick < disableTick) return
        if (!run.anchorDisablePrepared) {
            prepareAnchorDisable(run, store, tick)
            return
        }
        val anchorRef =
            blockEntityAt(
                run.world,
                CraftorySelfTest.ANCHOR.x,
                CraftorySelfTest.ANCHOR.y,
                CraftorySelfTest.ANCHOR.z,
            )
        val anchor = anchorRef?.let { store.getComponent(it, AnchorState.type) }
        val crankRef =
            blockEntityAt(
                run.world,
                CraftorySelfTest.CRANK.x,
                CraftorySelfTest.CRANK.y,
                CraftorySelfTest.CRANK.z,
            )
        val crank = crankRef?.let { store.getComponent(it, CrankState.type) }
        val crankBlock = crankRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        val rejected = AnchorChargeMath.tryAddCharge(0, CraftoryCore.anchorMaxChargeMinutes()) == null
        if (anchor == null || crank == null || crankBlock == null) {
            if (tick < (run.anchorDisableStartedTick ?: tick) + 360) {
                run.anchorDisableTick = tick + 20
                return
            }
            val anchorForTest = run.anchorStateForTest
            val areaActive = if (run.sectionRef.isValid) CraftoryCore.isAreaActive(store, run.sectionRef) else null
            if (anchorForTest != null && areaActive == false) {
                check(
                    run,
                    anchorForTest.chargeSeconds == 0L && anchorForTest.keepLoadedChunks.isEmpty(),
                    "empty Anchor pauses machines again",
                    "charge=${anchorForTest.chargeSeconds}, areaActive=$areaActive, " +
                        "anchorKeepLoaded=${anchorForTest.keepLoadedChunks.size}",
                )
            } else {
                run.context.sendMessage(
                    Message.raw(
                        "selftest: SKIP empty Anchor pause " +
                            "(anchor=${anchorForTest?.chargeSeconds}, areaActive=$areaActive)",
                    ),
                )
            }
            check(run, rejected, "max 0 rejects Anchor charging")
            finishAnchorDisable(run, tick)
            return
        }
        if (CraftoryCore.isAreaActive(store, crankBlock)) {
            if (tick < (run.anchorDisableStartedTick ?: tick) + 360) {
                run.anchorDisableTick = tick + 20
                return
            }
            run.context.sendMessage(Message.raw("selftest: SKIP empty Anchor pause (section stayed ticking)"))
        } else {
            val previousSpin = run.anchorSpinAtInactive
            if (previousSpin == null) {
                run.anchorSpinAtInactive = crank.spinTicks
                run.anchorDisableTick = tick + 20
                return
            }
            check(
                run,
                anchor.chargeSeconds == 0L &&
                    crank.spinTicks == previousSpin &&
                    anchor.keepLoadedChunks.isEmpty(),
                "empty Anchor pauses machines again",
                "spin=$previousSpin/${crank.spinTicks}, anchorKeepLoaded=${anchor.keepLoadedChunks.size}",
            )
        }
        check(run, rejected, "max 0 rejects Anchor charging")
        finishAnchorDisable(run, tick)
    }

    @Suppress("ComplexCondition")
    private fun prepareAnchorDisable(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        tick: Long,
    ) {
        val anchorRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.ANCHOR.x,
                CraftorySelfTest.ANCHOR.y,
                CraftorySelfTest.ANCHOR.z,
            )
        val anchor = anchorRef?.let { store.getComponent(it, AnchorState.type) }
        val crankRef =
            BlockModule.getBlockEntity(
                run.world,
                CraftorySelfTest.CRANK.x,
                CraftorySelfTest.CRANK.y,
                CraftorySelfTest.CRANK.z,
            )
        val crank = crankRef?.let { store.getComponent(it, CrankState.type) }
        val crankMember = crankRef?.let { store.getComponent(it, EnergyMember.type) }
        val crankBlock = crankRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        if (anchor == null || crank == null || crankMember == null || crankBlock == null) {
            if (tick < (run.anchorDisableStartedTick ?: tick) + 360) {
                run.anchorDisableTick = tick + 20
                return
            }
            CraftoryCore.setAnchorMaxChargeMinutesInMemory(0)
            run.anchorDisablePrepared = true
            run.anchorDisableTick = tick + 1
            return
        }
        CraftorySelfTest.retainTestKeepLoaded(run, store, anchor)
        MachineActions.pulse(crank, crankMember, crankBlock, store, tick)
        anchor.chargeSeconds = 0
        anchorRef?.let {
            store.getComponent(it, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
        }
        CraftoryCore.setAnchorMaxChargeMinutesInMemory(0)
        run.anchorDisablePrepared = true
        run.anchorDisableTick = tick + 20
    }

    private fun finishAnchorDisable(
        run: SelfTestRun.Run,
        tick: Long,
    ) {
        CraftoryCore.setAnchorMaxChargeMinutesInMemory(run.anchorMaxChargeBeforeTest)
        CraftorySelfTest.releaseTestKeepLoaded(run)
        run.anchorDisabledReported = true
        run.context.sendMessage(Message.raw("selftest: ${run.passed} passed, ${run.failed} failed"))
        run.cleanupTick = tick + 20
    }

    private fun blockEntityAt(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ): Ref<ChunkStore>? =
        BlockModule.getBlockEntity(
            world,
            x,
            y,
            z,
        )

    private fun stackDescription(stack: ItemStack?): String? = stack?.let { "${it.itemId}x${it.quantity}" }

    private fun check(
        run: SelfTestRun.Run,
        passed: Boolean,
        name: String,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
    }

    private fun reportInspectable(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        position: CraftorySelfTest.TestPosition,
        name: String,
    ) {
        val ref = BlockModule.getBlockEntity(run.world, position.x, position.y, position.z)
        val details =
            ref?.let { blockRef ->
                CraftoryCore.inspectableComponents(store, blockRef).joinToString(" ") { (id, component) ->
                    "$id{${component.describe().joinToString(" ") { (key, value) -> "$key=$value" }}}"
                }
            } ?: "block entity unavailable"
        run.context.sendMessage(Message.raw("selftest: inspect $name $details"))
    }
}

@Suppress("LargeClass", "TooManyFunctions")
object CraftorySelfTest {
    internal fun retainTestKeepLoaded(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        anchor: AnchorState,
    ) {
        val grid = run.world.chunkStore
        anchor.keepLoadedChunks.forEach { chunkIndex ->
            val chunkRef = grid.getChunkReference(chunkIndex)
            if (chunkRef != null && chunkRef.isValid) {
                val worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType())
                if (worldChunk != null && run.testKeepLoadedChunks.add(chunkIndex)) {
                    worldChunk.addKeepLoaded()
                }
            }
        }
    }

    internal fun releaseTestKeepLoaded(run: SelfTestRun.Run) {
        val grid = run.world.chunkStore
        run.testKeepLoadedChunks.forEach { chunkIndex ->
            val chunkRef = grid.getChunkReference(chunkIndex)
            if (chunkRef != null && chunkRef.isValid) {
                grid.store.getComponent(chunkRef, WorldChunk.getComponentType())?.removeKeepLoaded()
            }
        }
        run.testKeepLoadedChunks.clear()
    }

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

    @Suppress("TooGenericExceptionCaught")
    private fun setup(
        context: CommandContext,
        world: World,
        sectionRef: Ref<ChunkStore>,
    ) {
        val store = world.chunkStore.store
        val run = SelfTestRun.Run(context, world, world.tick, sectionRef)
        try {
            val section =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                    ?: error("block component section is missing")
            val initialCleanup = GnasherSelfTestHolders.clearDrops(world, sectionRef, GNASHER)
            clear(world, sectionRef, testPositions())
            place(world, store, sectionRef, section, CraftoryIds.BURNER, BURNER, BurnerState.type)
            pipePositions().forEach {
                place(world, store, sectionRef, section, CraftoryIds.PIPE, it, EnergyMember.type)
            }
            place(world, store, sectionRef, section, CraftoryIds.CANISTER, CANISTER, EnergyMember.type)
            place(world, store, sectionRef, section, CraftoryIds.CRANK, CRANK, CrankState.type)
            place(world, store, sectionRef, section, CraftoryIds.SCORCH_OVEN, OVEN, OvenPower.type)
            place(world, store, sectionRef, section, CraftoryIds.GNASHER_PIT, GNASHER, GnasherState.type)
            place(world, store, sectionRef, section, CraftoryIds.WAKEBELL_ANCHOR, ANCHOR, AnchorState.type)
            runPartCMachineProbes(world, store)

            fuelAndKickBurner(world, store)

            val crankRef = BlockModule.getBlockEntity(world, CRANK.x, CRANK.y, CRANK.z) ?: error("crank entity missing")
            val crank = store.getComponent(crankRef, CrankState.type) ?: error("crank component missing")
            val crankMember = store.getComponent(crankRef, EnergyMember.type) ?: error("crank energy component missing")
            val crankBlock =
                store.getComponent(crankRef, BlockModule.BlockStateInfo.getComponentType())
                    ?: error("crank block state missing")
            check(MachineActions.pulse(crank, crankMember, crankBlock, store, world.tick))
            run.crankSpinWithoutAnchor = crank.spinTicks
            CraftoryCore.setAnchorMaxChargeMinutesInMemory(maxOf(run.anchorMaxChargeBeforeTest, 240))
            store.getResource(SelfTestRun.type).active = run
            context.sendMessage(
                Message.raw(
                    "selftest: cleanup before run (liveGrit=${initialCleanup.liveDrops}, " +
                        "sectionHolders=${initialCleanup.sectionHolders}, " +
                        "chunkHolders=${initialCleanup.chunkHolders})",
                ),
            )
        } catch (failure: Exception) {
            abort(run, "setup (${failure.message ?: failure.javaClass.simpleName})")
        }
    }

    private fun fuelAndKickBurner(
        world: World,
        store: Store<ChunkStore>,
    ) {
        val burnerRef =
            BlockModule.getBlockEntity(world, BURNER.x, BURNER.y, BURNER.z)
                ?: error("burner entity missing")
        val burner = store.getComponent(burnerRef, BurnerState.type) ?: error("burner component missing")
        val member = store.getComponent(burnerRef, EnergyMember.type) ?: error("burner energy component missing")
        val blockState =
            store.getComponent(burnerRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("burner block state missing")
        repeat(2) {
            check(
                MachineActions.addFuel(
                    burner,
                    member,
                    blockState,
                    store,
                    world.tick,
                    "Plant_Crop_Mushroom_Common_Brown",
                ) > 0,
            )
        }
        check(MachineActions.kick(burner, member, blockState, store, world.tick))
    }

    private fun runPartCMachineProbes(
        world: World,
        store: Store<ChunkStore>,
    ) {
        val ovenRef = BlockModule.getBlockEntity(world, OVEN.x, OVEN.y, OVEN.z) ?: error("oven block entity missing")
        val bench =
            store.getComponent(ovenRef, ProcessingBenchBlock.getComponentType())
                ?: error("processing bench missing")
        check(bench.inputContainer.setItemStackForSlot(0.toShort(), ItemStack("Ore_Copper", 2)).succeeded()) {
            "oven copper input setup failed"
        }

        val gnasherRef =
            BlockModule.getBlockEntity(world, GNASHER.x, GNASHER.y, GNASHER.z)
                ?: error("gnasher block entity missing")
        val state = store.getComponent(gnasherRef, GnasherState.type) ?: error("gnasher state missing")
        state.oreQueue = ""
        state.progressPulses = 0
        state.jammed = false
        state.lastAutoCrankTick = world.tick
        state.lastPulseTick = null
        val blockState =
            store.getComponent(gnasherRef, BlockModule.BlockStateInfo.getComponentType())
                ?: error("gnasher block state missing")
        repeat(2) {
            check(MachineActions.depositGnasher(state, blockState, store, "Ore_Copper")) {
                "gnasher rejected copper ore"
            }
        }
    }

    internal fun startGnasherSequence(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val ref = BlockModule.getBlockEntity(run.world, GNASHER.x, GNASHER.y, GNASHER.z) ?: return
        val state = store.getComponent(ref, GnasherState.type) ?: return
        val blockState = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType()) ?: return
        val holders = GnasherSelfTestHolders.snapshot(store, run.sectionRef, GNASHER)
        val liveQuantity = gritQuantityNear(run.world, GNASHER)
        run.gnasherBaselineQuantity =
            liveQuantity + holders.entityChunkGrit + holders.entitySectionGrit
        run.gnasherBaselineParkedQuantity = holders.entityChunkGrit + holders.entitySectionGrit
        run.context.sendMessage(
            Message.raw(
                "selftest: Gnasher baseline before manual pulses " +
                    "(live=$liveQuantity, parked=${run.gnasherBaselineParkedQuantity})",
            ),
        )
        pulseGnasher(run, store, state, blockState, 1)
    }

    private fun pulseGnasher(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
        pulseNumber: Int,
    ) {
        if (store.getResource(SelfTestRun.type).active !== run) return
        val holdersBefore =
            if (pulseNumber % GnasherMachine.PULSES_PER_ORE == 0) {
                GnasherSelfTestHolders.snapshot(store, run.sectionRef, GNASHER)
            } else {
                null
            }
        holdersBefore?.let {
            GnasherSelfTestHolders.log(run, "before drop ${run.spawnedGritDrops + 1}", it)
        }
        val action =
            MachineActions.pulseGnasher(
                run.world,
                state,
                blockState,
                store,
                run.world.tick,
                BlockPos(GNASHER.x, GNASHER.y, GNASHER.z),
            )
        if (!action.pulse.accepted) {
            check(run, false, "gnasher pulse $pulseNumber accepted")
        }
        val drop = action.drop
        if (action.pulse.accepted && action.pulse.outputItemId != null && drop == null) {
            check(run, false, "gnasher drop result", "missing for pulse $pulseNumber")
        }
        if (drop != null) verifyGnasherDrop(run, store, drop, holdersBefore)
        if (pulseNumber < GnasherMachine.PULSES_PER_ORE * 2) {
            scheduleAfterTicks(run.world, GnasherMachine.MANUAL_PULSE_INTERVAL_TICKS) {
                pulseGnasher(run, store, state, blockState, pulseNumber + 1)
            }
            return
        }
        verifyGnasherDrops(run, store, state, blockState)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun verifyGnasherDrop(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        drop: GritDropResult,
        before: GnasherSelfTestHolders.Snapshot?,
    ): Boolean {
        val holdersAfter = GnasherSelfTestHolders.snapshot(store, run.sectionRef, GNASHER)
        GnasherSelfTestHolders.log(run, "after drop attempt ${run.spawnedGritDrops + 1}", holdersAfter)
        when (drop) {
            GritDropResult.Parked -> {
                val parkedIncrease = holdersAfter.entitySectionGrit - (before?.entitySectionGrit ?: 0)
                val parked = holdersAfter.nonTicking && parkedIncrease == GnasherMachine.GRIT_OUTPUT_QUANTITY
                if (parked) {
                    run.spawnedGritDrops++
                    run.spawnedGritQuantity += parkedIncrease
                }
                check(
                    run,
                    parked,
                    "gnasher parked-holder drop",
                    "nonTicking=${holdersAfter.nonTicking}, quantityAdded=$parkedIncrease",
                )
                return true
            }

            is GritDropResult.Spawned -> {
                val entityStore = run.world.entityStore.store
                val refValid = drop.ref.isValid
                val item =
                    if (refValid) {
                        entityStore.getComponent(drop.ref, ItemComponent.getComponentType())
                    } else {
                        null
                    }
                val stack = item?.itemStack
                val expectedGrit = GritTable.gritForOre("Ore_Copper")
                val liveQuantity = gritQuantityNear(run.world, GNASHER)
                val verified =
                    refValid &&
                        expectedGrit != null &&
                        stack != null &&
                        stack.itemId == expectedGrit &&
                        stack.quantity == GnasherMachine.GRIT_OUTPUT_QUANTITY &&
                        liveQuantity >=
                        run.gnasherBaselineQuantity +
                        run.spawnedGritQuantity +
                        GnasherMachine.GRIT_OUTPUT_QUANTITY
                if (verified) {
                    run.spawnedGritDrops++
                    run.spawnedGritQuantity += GnasherMachine.GRIT_OUTPUT_QUANTITY
                }
                check(
                    run,
                    verified,
                    "gnasher grit drop same-tick",
                    "drop=${run.spawnedGritDrops}, refValid=$refValid, " +
                        "stack=${stack?.itemId}x${stack?.quantity}, liveNearby=$liveQuantity, " +
                        "cumulative=${run.spawnedGritQuantity}",
                )
            }

            else -> {
                check(run, false, "gnasher grit drop", drop.toString())
            }
        }
        return true
    }

    private fun verifyGnasherDrops(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
    ) {
        scheduleAfterTicks(run.world, 4) {
            if (store.getResource(SelfTestRun.type).active !== run) return@scheduleAfterTicks
            verifyGnasherDropsAfterDelay(run, store, state, blockState)
        }
    }

    private fun verifyGnasherDropsAfterDelay(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
        state: GnasherState,
        blockState: BlockModule.BlockStateInfo,
    ) {
        val holders = GnasherSelfTestHolders.snapshot(store, run.sectionRef, GNASHER)
        val liveQuantity = gritQuantityNear(run.world, GNASHER)
        val parkedQuantity = holders.entityChunkGrit + holders.entitySectionGrit
        val totalQuantity = liveQuantity + parkedQuantity
        val addedQuantity = totalQuantity - run.gnasherBaselineQuantity
        val addedParkedQuantity = parkedQuantity - run.gnasherBaselineParkedQuantity
        GnasherSelfTestHolders.log(run, "after 4 ticks", holders)
        check(
            run,
            run.spawnedGritDrops == 2 && run.spawnedGritQuantity == 4,
            "gnasher two ore to four grit",
            "drops=${run.spawnedGritDrops}, sameTickQuantity=${run.spawnedGritQuantity}",
        )
        check(
            run,
            addedQuantity >= run.spawnedGritQuantity,
            "gnasher live and parked grit accounting",
            "baseline=${run.gnasherBaselineQuantity}, live=$liveQuantity, parked=$parkedQuantity, " +
                "total=$totalQuantity, added=$addedQuantity, manual=${run.spawnedGritQuantity}",
        )
        if (holders.nonTicking) {
            check(
                run,
                addedParkedQuantity >= run.spawnedGritQuantity,
                "gnasher non-ticking parked grit",
                "addedParked=$addedParkedQuantity, EntityChunk=${holders.entityChunkGrit}, " +
                    "EntitySection=${holders.entitySectionGrit}, manual=${run.spawnedGritQuantity}",
            )
            run.context.sendMessage(
                Message.raw("selftest: SKIP live Gnasher drop visibility after N ticks (NonTicking)"),
            )
        } else {
            check(
                run,
                addedParkedQuantity == 0 && addedQuantity >= run.spawnedGritQuantity,
                "gnasher live drops after 4 ticks",
                "baseline=${run.gnasherBaselineQuantity}, live=$liveQuantity, parked=$parkedQuantity, " +
                    "added=$addedQuantity, manual=${run.spawnedGritQuantity}",
            )
        }
        repeat(GnasherMachine.HOPPER_CAPACITY + 1) {
            check(
                run,
                MachineActions.depositGnasher(state, blockState, store, "Ore_Copper"),
                "gnasher accepts ore for jam",
            )
        }
        val rejectedPulse =
            MachineActions.pulseGnasher(
                run.world,
                state,
                blockState,
                store,
                run.world.tick,
                BlockPos(GNASHER.x, GNASHER.y, GNASHER.z),
            )
        check(run, state.jammed && !rejectedPulse.pulse.accepted, "gnasher ninth-ore jam")
        check(run, MachineActions.kickGnasher(state, blockState, store) && !state.jammed, "gnasher kick recovery")
    }

    private fun check(
        run: SelfTestRun.Run,
        passed: Boolean,
        name: String,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
    }

    private fun scheduleAfterTicks(
        world: World,
        ticks: Long,
        action: () -> Unit,
    ) {
        val targetTick = world.tick + ticks

        fun scheduleNext() {
            val remaining = targetTick - world.tick
            if (remaining <= 0) {
                action()
            } else {
                world.scheduleAfter({ scheduleNext() }, remaining * 50, TimeUnit.MILLISECONDS)
            }
        }
        world.scheduleAfter({ scheduleNext() }, ticks * 50, TimeUnit.MILLISECONDS)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun gritQuantityNear(
        world: World,
        position: TestPosition,
    ): Int {
        var quantity = 0
        val entityStore = world.entityStore.store
        entityStore.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val item = chunk.getComponent(index, ItemComponent.getComponentType()) ?: continue
                    val stack = item.itemStack ?: continue
                    val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                    val point = transform.position
                    if (
                        GritTable.containsGrit(stack.itemId) &&
                        kotlin.math.abs(point.x - (position.x + 0.5)) < 2.0 &&
                        kotlin.math.abs(point.z - (position.z + 0.5)) < 2.0
                    ) {
                        quantity += stack.quantity
                    }
                }
            },
        )
        return quantity
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    internal fun testWrench(
        run: SelfTestRun.Run,
        store: Store<ChunkStore>,
    ) {
        val section = store.getComponent(run.sectionRef, BlockComponentSection.getComponentType()) ?: return
        val testDirectory = Files.createTempDirectory("craftory-wrench-selftest-")
        val testFile = testDirectory.resolve("crews.json")
        val crews = CrewService(testFile)
        val playerUuid = UUID.randomUUID()
        val page = NotebookPages.forMachineType(CraftoryIds.BURNER_TYPE) ?: error("burner page is missing")
        try {
            clearWrenchDrops(run.world, WRENCH_TAKE_APART, WRENCH_PACK)
            place(run.world, store, run.sectionRef, section, CraftoryIds.BURNER, WRENCH_TAKE_APART, BurnerState.type)
            var takeApartRef =
                BlockModule.getBlockEntity(
                    run.world,
                    WRENCH_TAKE_APART.x,
                    WRENCH_TAKE_APART.y,
                    WRENCH_TAKE_APART.z,
                ) ?: error("wrench test burner was not placed")
            var machineOwner = store.getComponent(takeApartRef, MachineOwner.type) ?: error("machine owner is missing")
            check(run, machineOwner.placerUuid == null, "programmatic machine placement is unowned")
            val unowned =
                MachineActions.useWrench(
                    run.world,
                    takeApartRef,
                    org.joml.Vector3i(WRENCH_TAKE_APART.x, WRENCH_TAKE_APART.y, WRENCH_TAKE_APART.z),
                    playerUuid,
                    null,
                    crews,
                    store,
                )
            check(
                run,
                unowned == WrenchActionResult.ForeignCrew &&
                    !blockEntityMissing(run.world, WRENCH_TAKE_APART) &&
                    itemQuantityNear(run.world, WRENCH_TAKE_APART, "Ingredient_Scraps") == 0,
                "wrench refuses an unowned machine without permission",
            )
            val burnerType = BlockType.fromString(CraftoryIds.BURNER) ?: error("burner block type is missing")
            val burnerOnType = burnerType.getBlockForState("On") ?: error("burner On state is missing")
            val burnerOnIndex = BlockType.getAssetMap().getIndex(burnerOnType.id)
            check(
                BlockOperations.setBlock(
                    run.world.chunkStore,
                    run.sectionRef,
                    WRENCH_TAKE_APART.x,
                    WRENCH_TAKE_APART.y,
                    WRENCH_TAKE_APART.z,
                    burnerOnIndex,
                    burnerOnType,
                    0,
                    0,
                    SetBlockSettings.NO_UPDATE_STATE,
                ),
            ) { "failed to set burner On state" }
            BlockEntity.ensureBlockEntity(
                store,
                run.sectionRef,
                section,
                WRENCH_TAKE_APART.x and 31,
                WRENCH_TAKE_APART.y and 31,
                WRENCH_TAKE_APART.z and 31,
                burnerOnType,
                0,
            ) ?: error("burner On-state block entity is missing")
            assignMachineOwner(
                run.world,
                org.joml.Vector3i(WRENCH_TAKE_APART.x, WRENCH_TAKE_APART.y, WRENCH_TAKE_APART.z),
                CraftoryIds.BURNER,
                playerUuid,
            )
            takeApartRef =
                BlockModule.getBlockEntity(
                    run.world,
                    WRENCH_TAKE_APART.x,
                    WRENCH_TAKE_APART.y,
                    WRENCH_TAKE_APART.z,
                ) ?: error("wrench test burner On-state block entity was removed")
            machineOwner = store.getComponent(takeApartRef, MachineOwner.type) ?: error("machine owner is missing")
            check(
                run,
                machineOwner.placerUuid == playerUuid,
                "burner On-state placement receives owner",
            )
            machineOwner.placerUuid = UUID.randomUUID()
            val foreign =
                MachineActions.useWrench(
                    run.world,
                    takeApartRef,
                    org.joml.Vector3i(WRENCH_TAKE_APART.x, WRENCH_TAKE_APART.y, WRENCH_TAKE_APART.z),
                    playerUuid,
                    null,
                    crews,
                    store,
                )
            check(
                run,
                foreign == WrenchActionResult.ForeignCrew &&
                    !blockEntityMissing(run.world, WRENCH_TAKE_APART) &&
                    !crews.hasKnowledge(playerUuid, page.id) &&
                    itemQuantityNear(run.world, WRENCH_TAKE_APART, "Ingredient_Scraps") == 0,
                "wrench refuses a foreign crew machine",
            )
            val crewOwner = UUID.randomUUID()
            val crew = crews.create(crewOwner, "Machine Owners")
            check(run, crews.invite(crewOwner, playerUuid), "wrench owner test invite")
            check(run, crews.accept(playerUuid, crew.id) != null, "wrench owner test join")
            machineOwner.placerUuid = crewOwner
            val takeApart =
                MachineActions.useWrench(
                    run.world,
                    takeApartRef,
                    org.joml.Vector3i(WRENCH_TAKE_APART.x, WRENCH_TAKE_APART.y, WRENCH_TAKE_APART.z),
                    playerUuid,
                    null,
                    crews,
                    store,
                )
            check(
                run,
                takeApart is WrenchActionResult.Learned && machineOwner.placerUuid in crews.crewOf(playerUuid).members,
                "wrench permits same-crew machine owner",
            )
            check(
                run,
                takeApart is WrenchActionResult.Learned && takeApart.pageGranted && takeApart.scrapsDropped &&
                    crews.hasKnowledge(playerUuid, page.id),
                "wrench take-apart grants crew page",
            )
            check(
                run,
                blockEntityMissing(run.world, WRENCH_TAKE_APART) &&
                    itemQuantityNear(run.world, WRENCH_TAKE_APART, CraftoryIds.BURNER) == 0,
                "wrench removes machine without machine drop",
            )
            check(
                run,
                itemQuantityNear(run.world, WRENCH_TAKE_APART, "Ingredient_Scraps") >= 3,
                "wrench drops Scraps",
            )

            place(run.world, store, run.sectionRef, section, CraftoryIds.BURNER, WRENCH_PACK, BurnerState.type)
            val packRef =
                BlockModule.getBlockEntity(run.world, WRENCH_PACK.x, WRENCH_PACK.y, WRENCH_PACK.z)
                    ?: error("wrench pack test burner was not placed")
            val packed =
                MachineActions.useWrench(
                    run.world,
                    packRef,
                    org.joml.Vector3i(WRENCH_PACK.x, WRENCH_PACK.y, WRENCH_PACK.z),
                    playerUuid,
                    null,
                    crews,
                    store,
                    bypassOwnership = true,
                )
            check(
                run,
                packed is WrenchActionResult.Packed && packed.itemDropped &&
                    itemQuantityNear(run.world, WRENCH_PACK, CraftoryIds.BURNER) >= 1 &&
                    blockEntityMissing(run.world, WRENCH_PACK),
                "wrench packs known machine intact",
            )
        } finally {
            clearWrenchDrops(run.world, WRENCH_TAKE_APART, WRENCH_PACK)
            try {
                crews.close()
            } finally {
                Files.deleteIfExists(testFile)
                Files.deleteIfExists(testDirectory)
            }
        }
        check(run, !Files.exists(testDirectory), "wrench test crew cleanup")
    }

    private fun blockEntityMissing(
        world: World,
        position: TestPosition,
    ): Boolean = BlockModule.getBlockEntity(world, position.x, position.y, position.z)?.isValid != true

    private fun check(
        run: SelfTestRun.Run,
        passed: Boolean,
        name: String,
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name"))
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun itemQuantityNear(
        world: World,
        position: TestPosition,
        itemId: String,
    ): Int {
        var quantity = 0
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val stack = chunk.getComponent(index, ItemComponent.getComponentType())?.itemStack ?: continue
                    val point = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    if (
                        stack.itemId == itemId &&
                        kotlin.math.abs(point.x - (position.x + 0.5)) < 2.0 &&
                        kotlin.math.abs(point.z - (position.z + 0.5)) < 2.0
                    ) {
                        quantity += stack.quantity
                    }
                }
            },
        )
        return quantity
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun clearWrenchDrops(
        world: World,
        vararg positions: TestPosition,
    ) {
        val centers = positions.map { it.x + 0.5 to it.z + 0.5 }
        world.entityStore.store.forEachChunk(
            Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType()),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                for (index in 0 until chunk.size()) {
                    val itemId =
                        chunk.getComponent(index, ItemComponent.getComponentType())?.itemStack?.itemId ?: continue
                    val point =
                        chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: continue
                    if (
                        itemId in setOf("Ingredient_Scraps", CraftoryIds.BURNER) &&
                        centers.any { (x, z) ->
                            kotlin.math.abs(point.x - x) < 2.0 && kotlin.math.abs(point.z - z) < 2.0
                        }
                    ) {
                        commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                    }
                }
            },
        )
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
    ): SelfTestCleanupResult {
        val store = world.chunkStore.store
        if (
            !sectionRef.isValid ||
            store.getComponent(sectionRef, BlockComponentSection.getComponentType()) == null
        ) {
            return GnasherSelfTestHolders.clearDrops(world, sectionRef, GNASHER)
        }
        BlockModule
            .getBlockEntity(world, GNASHER.x, GNASHER.y, GNASHER.z)
            ?.takeIf { it.isValid }
            ?.let { store.getComponent(it, GnasherState.type) }
            ?.apply {
                oreQueue = ""
                progressPulses = 0
                jammed = false
                lastAutoCrankTick = world.tick
                lastPulseTick = null
            }
        testPositions().forEach { position ->
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
        return GnasherSelfTestHolders.clearDrops(world, sectionRef, GNASHER)
    }

    internal data class TestPosition(
        val x: Int,
        val y: Int,
        val z: Int,
    )

    internal val BURNER = TestPosition(10_018, 148, 10_000)
    internal val PIPE = TestPosition(10_018, 149, 10_000)
    internal val CANISTER = TestPosition(10_018, 150, 10_000)
    internal val CRANK = TestPosition(10_026, 150, 10_006)
    internal val OVEN = TestPosition(10_034, 150, 10_000)
    internal val OVEN_LINK_PIPE = TestPosition(10_034, 149, 10_000)
    internal val GNASHER = TestPosition(10_044, 150, 10_006)
    internal val ANCHOR = TestPosition(10_040, 150, 10_012)
    private val WRENCH_TAKE_APART = TestPosition(10_020, 148, 10_004)
    private val WRENCH_PACK = TestPosition(10_022, 148, 10_004)

    private fun pipePositions(): List<TestPosition> =
        listOf(PIPE) + (10_019..10_034).map { TestPosition(it, 149, 10_000) }

    private fun testPositions(): List<TestPosition> =
        (
            listOf(BURNER) + pipePositions() +
                listOf(CANISTER, CRANK, OVEN, GNASHER, ANCHOR, WRENCH_TAKE_APART, WRENCH_PACK)
        ).distinct()
}
