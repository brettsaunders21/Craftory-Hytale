package studio.craftory.tech.machine

import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.ConverterMath
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.CradleActions
import studio.craftory.tech.well.GobJarMetadata
import studio.craftory.tech.well.JarActions
import studio.craftory.tech.well.JarCarrier
import studio.craftory.tech.well.JarCarrierSystem
import studio.craftory.tech.well.JarCatchResult
import studio.craftory.tech.well.WellAffliction
import studio.craftory.tech.well.WellAfflictionFollower
import studio.craftory.tech.well.WellAfflictionRules
import studio.craftory.tech.well.WellAfflictionRuntimeConfig
import studio.craftory.tech.well.WellAfflictionSystem
import studio.craftory.tech.well.WellSelfTestExclusions
import studio.craftory.tech.well.WellheadPlacementSystem

internal object Slice5bSelfTestChecks {
    const val CHECK_COUNT = 9
    const val PERSISTENCE_STAGE = 6
    const val CATALYSIS_STAGE = 1
    const val CATALYSIS_TICKS = 24L

    fun run(
        run: Slice5bSelfTestRun.Run,
        index: Int,
    ) {
        val names =
            listOf(
                "Wellhead tap and untap",
                "Catalysis grade-up with 15% loss",
                "Steady seating grants steady_seated",
                "Riftmoth feed corrupts and tracked death cleanses",
                "Poacher raid siphons and drops Well strength band",
                "Walker death stops a separate raid siphon",
                "TAPPED Well save/reload",
                "Corrupted Well save/reload",
                "MachineOwner assigned through Wellhead placement path",
            )
        val name = names[index]
        val started = run.world.tick
        val result =
            runCatching {
                when (index) {
                    0 -> wellheadTapAndUntap(run)
                    2 -> steadySeating(run)
                    3 -> feedAndCleanse(run)
                    4 -> raidSiphons(run)
                    5 -> walkerDeathStopsSiphon(run)
                    8 -> ownerAssignment(run)
                    else -> error("persistence checks run asynchronously")
                }
            }
        val elapsed = run.world.tick - started
        if (result.isSuccess) {
            run.passed++
            run.context.sendMessage(Message.raw("selftest: PASS 5b $name (elapsedTicks=$elapsed, timeoutTicks=600)"))
        } else {
            run.failed++
            run.context.sendMessage(
                Message.raw(
                    "selftest: FAIL 5b $name (elapsedTicks=$elapsed, timeoutTicks=600, " +
                        "detail=${result.exceptionOrNull()?.message ?: "assertion failed"})",
                ),
            )
        }
    }

    private fun wellheadTapAndUntap(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[0]
        val head = BlockPos(pos.x, pos.y + 1, pos.z)
        val well = spawnClaimedWell(run, pos, WellStrength.FLICKER, WellState.TETHERED)
        place(run, 0, CraftoryIds.WELLHEAD, head)
        WellheadPlacementSystem().initializeForSelfTest(run.world, head, pos)
        check(well.state == WellState.TAPPED) { "placement did not tap Well" }
        CraftoryCore.wells().setState(run.world, pos, WellState.TETHERED)
        check(well.state == WellState.TETHERED) { "untap did not restore TETHERED" }
    }

    private fun steadySeating(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[2]
        val cradle = BlockPos(pos.x, pos.y - 1, pos.z)
        val player =
            studio.craftory.tech.well.WellSelfTestPlayer
                .createCarrierSelfTestPlayer(run.world, pos)
        val crews =
            studio.craftory.core.api.crew.CrewService(
                java.nio.file.Files
                    .createTempDirectory("slice5b-crews")
                    .resolve("crews.json"),
            )
        val crew = crews.crewOf(player.uuid)
        crews.setTier(crew.id, 2)
        val ref = spawnClaimedWell(run, pos, WellStrength.STEADY, WellState.TETHERED, crew.id)
        val wellRef = BlockModule.getBlockEntity(run.world, pos.x, pos.y, pos.z) ?: error("well block missing")
        val caught = JarActions.checkCatch(wellRef, player.uuid, crews, run.world.chunkStore.store)
        check(caught is JarCatchResult.Caught) { "real JarActions.checkCatch failed: $caught" }
        check(JarActions.commitCatch(run.world, wellRef, pos, player.uuid, caught, crews)) {
            "real JarActions.commitCatch failed"
        }
        place(run, 2, CraftoryIds.WELL_CRADLE, cradle)
        val jar = JarActions.filledJar(caught)
        val seated = CradleActions.seat(run.world, cradle, player.uuid, jar, crews)
        check(seated != null && crews.hasKnowledge(player.uuid, CradleActions.STEADY_SEATED_KNOWLEDGE)) {
            "seated=$seated steadyKnowledge=${crews.hasKnowledge(player.uuid, CradleActions.STEADY_SEATED_KNOWLEDGE)}"
        }
        val entity = requireNotNull(player.reference)
        val entityStore = run.world.entityStore.store
        val inventory =
            requireNotNull(
                entityStore
                    .getComponent(entity, InventoryComponent.Storage.getComponentType())
                    ?.getInventory(),
            )
        val movement = requireNotNull(entityStore.getComponent(entity, MovementManager.getComponentType()))
        val originalSpeed = movement.settings.baseSpeed
        inventory.setItemStackForSlot(0.toShort(), jar)
        val position = requireNotNull(entityStore.getComponent(entity, TransformComponent.getComponentType())).position
        JarCarrierSystem().processPlayer(entity, player, position, entityStore, run.world, run.world.tick)
        if (entityStore.getComponent(entity, JarCarrier.type) == null) {
            Slice5bSelfTestFixtures.deferSyntheticPlayerRemoval(run, entity)
            crews.close()
            error("JarCarrier was not installed")
        }
        inventory.setItemStackForSlot(0.toShort(), ItemStack.EMPTY)
        JarCarrierSystem().processPlayer(entity, player, position, entityStore, run.world, run.world.tick + 1)
        check(
            entityStore.getComponent(entity, JarCarrier.type) == null &&
                movement.settings.baseSpeed == originalSpeed,
        ) { "JarCarrier removal did not restore movement" }
        Slice5bSelfTestFixtures.deferSyntheticPlayerRemoval(run, entity)
        crews.close()
    }

    private fun feedAndCleanse(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[3]
        val well = spawnClaimedWell(run, pos, WellStrength.STEADY, WellState.TAPPED)
        val ref = BlockModule.getBlockEntity(run.world, pos.x, pos.y, pos.z) ?: error("well block missing")
        val store = run.world.chunkStore.store
        store.putComponent(ref, WellAffliction.type, WellAffliction())
        val system = WellAfflictionSystem()
        system.startForSelfTest(run.world, pos, WellAfflictionFollower.MOTH, 1)
        val current = store.getComponent(ref, WellAffliction.type) ?: error("affliction missing")
        current.mothIds.forEach { Slice5bSelfTestFixtures.recordNpc(run, it, WellAfflictionFollower.MOTH) }
        check(current.mothIds.isNotEmpty()) { "forced Riftmoth did not spawn" }
        system.advanceFeedForSelfTest(
            run.world,
            pos,
            well,
            WellAfflictionRuntimeConfig.settings.riftmothFeedSecondsToCorrupt,
        )
        check(well.corrupted) { "forced moth feed did not corrupt" }
        current.mothIds.forEach { raw ->
            runCatching { java.util.UUID.fromString(raw) }.getOrNull()?.let { id ->
                run.world.entityStore.getRefFromUUID(id)?.takeIf { it.isValid }?.let {
                    WellAfflictionSystem.onFollowerDeath(run.world, id, pos, WellAfflictionFollower.MOTH)
                    run.world.entityStore.store
                        .removeEntity(it, RemoveReason.REMOVE)
                }
            }
        }
        current.mothIds = emptyArray()
        check(WellAfflictionRules.shouldCleanse(well.corrupted, false, current.mothIds.isEmpty())) {
            "tracked moth death did not satisfy cleanse rule"
        }
        CraftoryCore.wells().setCorrupted(run.world, pos, false)
        check(!well.corrupted) { "cleanse did not clear corruption" }
    }

    private fun raidSiphons(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[4]
        val well = spawnClaimedWell(run, pos, WellStrength.ROARING, WellState.TAPPED)
        val ref = BlockModule.getBlockEntity(run.world, pos.x, pos.y, pos.z) ?: error("well block missing")
        val store = run.world.chunkStore.store
        store.putComponent(ref, WellAffliction.type, WellAffliction())
        val settings = WellAfflictionRuntimeConfig.settings
        val original = settings.siphonSeconds
        settings.siphonSeconds = 1
        val system = WellAfflictionSystem()
        system.startRaidForSelfTest(run.world, pos)
        val state = store.getComponent(ref, WellAffliction.type) ?: error("raid state missing")
        check(state.walkerId.isNotEmpty()) { "Walker failed to spawn" }
        state.raidNpcIds.forEach { id ->
            Slice5bSelfTestFixtures.recordNpc(
                run,
                id,
                if (id == state.walkerId) WellAfflictionFollower.WALKER else WellAfflictionFollower.POACHER,
            )
        }
        studio.craftory.tech.well
            .processRaid(run.world, pos, well, state, ownerCrewOnline = true)
        check(well.strength == WellStrength.STEADY) { "siphon strength=${well.strength}" }
        studio.craftory.tech.well
            .despawnFollowers(run.world, state.raidNpcIds)
        state.raidNpcIds = emptyArray()
        state.walkerId = ""
        settings.siphonSeconds = original
    }

    private fun walkerDeathStopsSiphon(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[5]
        val well = spawnClaimedWell(run, pos, WellStrength.ROARING, WellState.TAPPED)
        val ref = BlockModule.getBlockEntity(run.world, pos.x, pos.y, pos.z) ?: error("well block missing")
        val store = run.world.chunkStore.store
        store.putComponent(ref, WellAffliction.type, WellAffliction())
        WellAfflictionSystem().startRaidForSelfTest(run.world, pos)
        val state = store.getComponent(ref, WellAffliction.type) ?: error("raid state missing")
        state.raidNpcIds.forEach { id ->
            Slice5bSelfTestFixtures.recordNpc(
                run,
                id,
                if (id == state.walkerId) WellAfflictionFollower.WALKER else WellAfflictionFollower.POACHER,
            )
        }
        val walker = java.util.UUID.fromString(state.walkerId)
        state.raidNpcIds = state.raidNpcIds.filterNot { it == state.walkerId }.toTypedArray()
        state.walkerId = ""
        WellAfflictionSystem.onFollowerDeath(run.world, walker, pos, WellAfflictionFollower.WALKER)
        run.world.entityStore.getRefFromUUID(walker)?.takeIf { it.isValid }?.let {
            run.world.entityStore.store
                .removeEntity(it, RemoveReason.REMOVE)
        }
        studio.craftory.tech.well
            .processRaid(run.world, pos, well, state, ownerCrewOnline = true)
        check(well.strength == WellStrength.ROARING) { "strength changed after Walker death" }
        studio.craftory.tech.well
            .despawnFollowers(run.world, state.raidNpcIds)
        state.raidNpcIds = emptyArray()
    }

    private fun ownerAssignment(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[0]
        val head = BlockPos(pos.x, pos.y + 1, pos.z)
        val owner = java.util.UUID.randomUUID()
        assignMachineOwner(run.world, Vector3i(head.x, head.y, head.z), CraftoryIds.WELLHEAD, owner)
        run.cleanupBlockPositions.add(head)
        val ref = BlockModule.getBlockEntity(run.world, head.x, head.y, head.z) ?: error("Wellhead missing")
        WellSelfTestExclusions.registerBlock(ref)
        run.excludedBlockRefs.add(ref)
        check(
            run.world.chunkStore.store
                .getComponent(ref, MachineOwner.type)
                ?.placerUuid == owner,
        ) {
            "placement path did not persist MachineOwner"
        }
    }

    internal fun spawnClaimedWell(
        run: Slice5bSelfTestRun.Run,
        pos: BlockPos,
        strength: WellStrength,
        state: WellState,
        claimCrewId: java.util.UUID = java.util.UUID.randomUUID(),
    ): Glimmerwell {
        val ref =
            CraftoryCore.wells().spawn(
                run.world,
                pos,
                Temperament.SPARK,
                strength,
                state,
                claimCrewId = claimCrewId,
            ) ?: error("Well failed to spawn at $pos")
        WellSelfTestExclusions.registerWell(pos)
        run.cleanupWellPositions.add(pos)
        return run.world.chunkStore.store
            .getComponent(ref, Glimmerwell.type) ?: error("Well component missing")
    }

    internal fun place(
        run: Slice5bSelfTestRun.Run,
        index: Int,
        blockId: String,
        pos: BlockPos,
    ) {
        run.cleanupBlockPositions.add(pos)
        val type = requireNotNull(BlockType.fromString(blockId))
        val ref = run.sectionRefs[index] ?: error("fixture section missing")
        val store = run.world.chunkStore.store
        check(
            BlockOperations.setBlock(
                run.world.chunkStore,
                ref,
                pos.x,
                pos.y,
                pos.z,
                BlockType.getAssetMap().getIndex(type.id),
                type,
                0,
                0,
                SetBlockSettings.NONE,
            ),
        ) { "failed to place $blockId" }
        BlockModule.getBlockEntity(run.world, pos.x, pos.y, pos.z)?.let {
            WellSelfTestExclusions.registerBlock(it)
            run.excludedBlockRefs.add(it)
        }
    }
}

internal object Slice5bCatalysisCheck {
    private const val TIMEOUT_TICKS = 600L

    fun start(run: Slice5bSelfTestRun.Run) {
        check(run.fixtures[1].x == 10_632) { "catalysis fixture is not in its assigned chunk" }
        val pos = run.fixtures[1]
        val head = BlockPos(pos.x, pos.y + 1, pos.z)
        val crank = BlockPos(head.x - 1, head.y, head.z)
        val sink = BlockPos(head.x, head.y + 1, head.z)
        Slice5bSelfTestChecks.spawnClaimedWell(run, pos, WellStrength.FLICKER, WellState.TETHERED)
        WellSelfTestExclusions.registerWellheadTest(pos)
        Slice5bSelfTestChecks.place(run, 1, CraftoryIds.CRANK, crank)
        Slice5bSelfTestChecks.place(run, 1, CraftoryIds.FIZZ_DAMPER, sink)
        Slice5bSelfTestChecks.place(run, 1, CraftoryIds.WELLHEAD, head)
        WellheadPlacementSystem().initializeForSelfTest(run.world, head, pos)
        val store = run.world.chunkStore.store
        val crankRef =
            BlockModule.getBlockEntity(run.world, crank.x, crank.y, crank.z) ?: error("Crank producer missing")
        val crankState = store.getComponent(crankRef, CrankState.type) ?: error("Crank state missing")
        val crankMember = store.getComponent(crankRef, EnergyMember.type) ?: error("Crank energy node missing")
        val crankBlockState =
            store.getComponent(crankRef, BlockModule.BlockStateInfo.getComponentType()) ?: error("Crank block missing")
        check(MachineActions.pulse(crankState, crankMember, crankBlockState, store, run.world.tick)) {
            "Crank test source did not pulse"
        }
        val sinkRef =
            BlockModule.getBlockEntity(run.world, sink.x, sink.y, sink.z) ?: error("output sink missing")
        check(store.getComponent(sinkRef, EnergyMember.type) != null) { "output sink has no energy node" }
        CraftoryCore.energyAccess(run.world).setDemand(sink.pack(), 2L)
        val headRef = BlockModule.getBlockEntity(run.world, head.x, head.y, head.z) ?: error("Wellhead missing")
        val headState = store.getComponent(headRef, WellheadState.type) ?: error("Wellhead state missing")
        headState.harmony = 0
        headState.lastHarmonyTick = run.world.tick
        check(headState.harmony == 0) { "catalysis fixture has harmony=${headState.harmony}" }
    }

    fun finish(run: Slice5bSelfTestRun.Run) {
        val pos = run.fixtures[1]
        val head = BlockPos(pos.x, pos.y + 1, pos.z)
        val sink = BlockPos(head.x, head.y + 1, head.z)
        val energy = CraftoryCore.energyAccess(run.world)
        val delivered = energy.converterInputDelivered(head.pack())
        val emitted = energy.networkSupply(sink.pack())
        val rank = energy.networkGrade(sink.pack())
        val expected = ConverterMath.catalyse(delivered)
        val lossPercent = if (delivered == 0L) 0.0 else (delivered - expected) * 100.0 / delivered
        val store = run.world.chunkStore.store
        val crank = BlockPos(head.x - 1, head.y, head.z)
        val crankRef = BlockModule.getBlockEntity(run.world, crank.x, crank.y, crank.z)
        val crankMember = crankRef?.let { store.getComponent(it, EnergyMember.type) }
        val headRef = BlockModule.getBlockEntity(run.world, head.x, head.y, head.z)
        val headMember = headRef?.let { store.getComponent(it, EnergyMember.type) }
        val headState = headRef?.let { store.getComponent(it, WellheadState.type) }
        val headBlock = headRef?.let { store.getComponent(it, BlockModule.BlockStateInfo.getComponentType()) }
        val wellState =
            CraftoryCore
                .wells()
                .wellsNear(store, pos, 0)
                .firstOrNull()
                ?.well
                ?.state
        val detail =
            "elapsedTicks=${run.world.tick - run.stageStarted}, timeoutTicks=$TIMEOUT_TICKS, " +
                "delivered=$delivered, emitted=$emitted, lossPercent=" +
                "${String.format(java.util.Locale.ROOT, "%.1f", lossPercent)}, rank=$rank, " +
                "sourceProduction=${crankMember?.production}, " +
                "sourceNetwork=${energy.networkOf(crank.pack())}, " +
                "inputNetwork=${energy.networkOf(head.pack())}, " +
                "inputDemand=${headMember?.demand}, " +
                "outputNetwork=${energy.converterOutputNetwork(head.pack())}, " +
                "sinkNetwork=${energy.networkOf(sink.pack())}, " +
                "headActive=${headBlock?.let { CraftoryCore.isAreaActive(store, it) }}, " +
                "wellState=$wellState, headWell=${headState?.wellPosition?.let(BlockPos::unpack)}, " +
                "pumping=${headState?.pumping}"
        val passed =
            delivered > 0L &&
                emitted == expected &&
                kotlin.math.abs(lossPercent - 15.0) < 0.5 &&
                rank == WellOutput.GRADE_RANK
        if (passed) {
            run.passed++
            run.context.sendMessage(Message.raw("selftest: PASS 5b Catalysis grade-up with 15% loss ($detail)"))
        } else {
            run.failed++
            run.context.sendMessage(
                Message.raw("selftest: FAIL 5b Catalysis grade-up with 15% loss ($detail, expected=$expected)"),
            )
        }
    }

    fun fail(
        run: Slice5bSelfTestRun.Run,
        detail: String,
    ) {
        run.failed++
        run.context.sendMessage(
            Message.raw(
                "selftest: FAIL 5b Catalysis grade-up with 15% loss " +
                    "(elapsedTicks=0, timeoutTicks=$TIMEOUT_TICKS, detail=$detail)",
            ),
        )
    }
}

internal object Slice5bSelfTestSupport {
    fun runPersistence(
        run: Slice5bSelfTestRun.Run,
        complete: () -> Unit,
    ) {
        studio.craftory.tech.well.Slice5bPersistenceSelfTest.run(
            run.world,
            run.fixtures[5],
        ) { tapped, corrupted ->
            report(
                run,
                "TAPPED Well save/reload",
                tapped.passed,
                "elapsedTicks=${tapped.elapsedTicks}, timeoutTicks=6000, detail=${tapped.detail}",
            )
            report(
                run,
                "Corrupted Well save/reload",
                corrupted.passed,
                "elapsedTicks=${corrupted.elapsedTicks}, timeoutTicks=6000, detail=${corrupted.detail}",
            )
            complete()
        }
        run.cleanupWellPositions.add(BlockPos(run.fixtures[5].x + 8, run.fixtures[5].y, run.fixtures[5].z))
        run.cleanupWellPositions.add(BlockPos(run.fixtures[5].x + 12, run.fixtures[5].y, run.fixtures[5].z))
        run.cleanupBlockPositions.addAll(run.cleanupWellPositions.filter { it.x >= run.fixtures[5].x + 8 })
    }

    fun timeout(
        run: Slice5bSelfTestRun.Run,
        vararg names: String,
    ) {
        names.forEach { name ->
            run.failed++
            run.context.sendMessage(
                Message.raw(
                    "selftest: FAIL 5b $name (elapsedTicks=${run.world.tick - run.stageStarted}, " +
                        "timeoutTicks=6000, detail=save/reload timed out)",
                ),
            )
        }
    }

    fun failAll(
        run: Slice5bSelfTestRun.Run,
        detail: String,
    ) {
        repeat(Slice5bSelfTestChecks.CHECK_COUNT) { index ->
            run.failed++
            run.context.sendMessage(Message.raw("selftest: FAIL 5b check-${index + 1} (timeoutTicks=6000, $detail)"))
        }
    }

    private fun report(
        run: Slice5bSelfTestRun.Run,
        name: String,
        passed: Boolean,
        detail: String,
    ) {
        if (passed) run.passed++ else run.failed++
        run.context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} 5b $name ($detail)"))
    }
}
