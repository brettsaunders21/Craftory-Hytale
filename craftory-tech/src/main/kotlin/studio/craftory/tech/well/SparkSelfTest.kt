package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.BurnerMachine
import studio.craftory.tech.machine.BurnerState
import studio.craftory.tech.machine.CradleState
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.machine.MachineOwner
import studio.craftory.tech.machine.ResonatorRuntimeConfig
import studio.craftory.tech.machine.ResonatorState
import studio.craftory.tech.machine.SelfTestRun
import studio.craftory.tech.notebook.NotebookTierProgression
import java.nio.file.Files
import java.util.UUID

@Suppress("LongMethod", "MaxLineLength", "TooManyFunctions")
internal class SparkSelfTest(
    private val run: SelfTestRun.Run,
) {
    private val world = run.world
    private val store = world.chunkStore.store
    private val cradle = CraftorySelfTest.TestPosition(10_020, 148, 10_020)
    private val well = BlockPos(cradle.x, cradle.y + 1, cradle.z)
    private val storage = CraftorySelfTest.TestPosition(10_021, 148, 10_020)
    private val consumer = CraftorySelfTest.TestPosition(10_022, 148, 10_020)
    private val tasteA = CraftorySelfTest.TestPosition(10_020, 148, 10_019)
    private val tasteB = CraftorySelfTest.TestPosition(10_020, 148, 10_021)
    private val coilBurner = CraftorySelfTest.TestPosition(10_028, 148, 10_020)
    private val coil = CraftorySelfTest.TestPosition(10_028, 149, 10_020)
    private val coilStorage = CraftorySelfTest.TestPosition(10_029, 149, 10_020)
    private val resonatorBurner = CraftorySelfTest.TestPosition(10_036, 148, 10_020)
    private val resonator = CraftorySelfTest.TestPosition(10_036, 149, 10_020)
    private val sparkBurner = CraftorySelfTest.TestPosition(10_042, 148, 10_020)
    private val cyanCrystal = CraftorySelfTest.TestPosition(10_040, 148, 10_020)
    private val positions =
        listOf(
            cradle,
            CraftorySelfTest.TestPosition(well.x, well.y, well.z),
            storage,
            consumer,
            tasteA,
            tasteB,
            coilBurner,
            coil,
            coilStorage,
            resonatorBurner,
            resonator,
            sparkBurner,
            cyanCrystal,
        )
    private val player = UUID.randomUUID()
    private val sparkOwner = UUID.randomUUID()
    private val tierOwner = UUID.randomUUID()
    private val testDirectory = Files.createTempDirectory("craftory-spark-selftest")
    private val crews = CrewService(testDirectory.resolve("crews.json"))
    private val originalCrystalUses = ResonatorRuntimeConfig.crystalUses
    private var completed = false
    private var cleaned = false
    private var fixturesStarted = false
    private var energyTicks = 0
    private var harmonyRechecked = false
    private var resonatorSawOutput = false
    private var vessaRef: Ref<EntityStore>? = null
    private var completion: (() -> Unit)? = null

    @Suppress("TooGenericExceptionCaught")
    fun start(onComplete: () -> Unit) {
        completion = onComplete
        run.sparkCleanup = ::cleanup
        run.sparkTest = this
        try {
            ensureEmpty()
            fixturesStarted = true
            runLiveChecks()
            if (run.sectionEntityTicking) {
                prepareTickChecks()
            } else {
                report("SKIP Spark energy, Coil, Resonator and First Spark checks (section is not ticking)")
                world.execute { finish() }
            }
        } catch (exception: Exception) {
            check(false, "Spark setup", exception.message ?: exception.javaClass.simpleName)
            finish()
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun tick() {
        if (completed) return
        if (!run.sectionEntityTicking) {
            if (!harmonyRechecked) return
            finish()
            return
        }
        val energy = CraftoryCore.energyAccess(world)
        val resonatorOutputId = BlockPos.pack(resonator.x, resonator.y, resonator.z) xor CONVERTER_Y_MASK
        val dimRank = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))
        if (energy.converterOutputNetwork(BlockPos.pack(resonator.x, resonator.y, resonator.z)) >= 0 &&
            energy.networkSupply(resonatorOutputId) > 0L &&
            energy.networkGrade(resonatorOutputId) == dimRank
        ) {
            resonatorSawOutput = true
        }
        if (!harmonyRechecked || ++energyTicks < 4) return
        val fizzRank = requireNotNull(CraftoryCore.gradeRank("fizz"))
        val cradleId = BlockPos.pack(cradle.x, cradle.y, cradle.z)
        check(
            energy.networkOf(cradleId) >= 0 &&
                energy.networkGrade(cradleId) == dimRank &&
                energy.networkSupply(cradleId) > 0L,
            "Cradle network produces dim_glimmer",
            "grade=${energy.networkGrade(cradleId)}, supply=${energy.networkSupply(cradleId)}",
        )
        val coilRef = entityAt(coil)
        val coilOutput = coilRef?.let { store.getComponent(it, EnergyMember.type) }
        val coilStorageMember = entityAt(coilStorage)?.let { store.getComponent(it, EnergyMember.type) }
        val burnerRef = entityAt(coilBurner)
        val burnerId = BlockPos.pack(coilBurner.x, coilBurner.y, coilBurner.z)
        val coilStorageId = BlockPos.pack(coilStorage.x, coilStorage.y, coilStorage.z)
        val coilOutputId = BlockPos.pack(coil.x, coil.y, coil.z) xor CONVERTER_Y_MASK
        check(
            coilStorageMember?.stored?.let { it > 0L } == true &&
                coilStorageMember.storeRank == dimRank &&
                energy.networkGrade(coilOutputId) == dimRank &&
                energy.converterOutputNetwork(BlockPos.pack(coil.x, coil.y, coil.z)) >= 0 &&
                energy.networkSupply(coilOutputId) > 0L &&
                burnerRef?.let {
                    store.getComponent(it, EnergyMember.type)?.let { member ->
                        member.productionRank == fizzRank && energy.networkGrade(burnerId) == fizzRank
                    }
                } == true &&
                energy.networkMembers(burnerId) > 0 &&
                energy.networkSupply(burnerId) > 0L &&
                energy.networkOf(burnerId) != energy.networkOf(coilStorageId),
            "Coil converts Burner fizz without joining its network",
            "stored=${coilStorageMember?.stored}/${coilStorageMember?.storeRank}, " +
                "coil=${coilOutput?.deliveredRank}, burnerGrade=${energy.networkGrade(burnerId)}",
        )
        val resonatorRef = entityAt(resonator)
        val resonatorState = resonatorRef?.let { store.getComponent(it, ResonatorState.type) }
        check(
            resonatorSawOutput && resonatorState?.clouded == true &&
                energy.networkSupply(resonatorOutputId) == 0L,
            "Resonator converts then clouds at configured crystal uses",
            "conversions=${resonatorState?.conversions}, clouded=${resonatorState?.clouded}, " +
                "output=${energy.networkSupply(resonatorOutputId)}",
        )
        check(
            vessaRef?.isValid == true &&
                crews.hasKnowledge(sparkOwner, CraftoryIds.VESSA_MET_KNOWLEDGE) &&
                vessaRef
                    ?.let { world.entityStore.store.getComponent(it, TransformComponent.getComponentType()) }
                    ?.position
                    ?.y == sparkBurner.y + 1.5,
            "First Spark spawns Vessa and grants meeting knowledge",
        )
        finish()
    }

    private fun runLiveChecks() {
        val section = section()
        CraftorySelfTest.place(world, store, run.sectionRef, section, CraftoryIds.WELL_CRADLE, cradle, CradleState.type)
        val cradleRef = entityAt(cradle) ?: error("Cradle block entity missing")
        val cradleCrew = crews.crewOf(player)
        crews.setTier(cradleCrew.id, 2)
        val spawnedWell =
            CraftoryCore.wells().spawn(
                world,
                well,
                Temperament.SPARK,
                WellStrength.FLICKER,
                WellState.WILD,
            ) ?: error("Flicker well spawn failed")
        val caught = JarActions.checkCatch(spawnedWell, player, crews, store)
        check(caught is JarCatchResult.Caught, "Flicker can be caught into a filled jar")
        val captured = caught as? JarCatchResult.Caught
        val caughtAndRemoved =
            captured != null && JarActions.commitCatch(world, spawnedWell, well, player, captured, crews)
        val seated =
            captured?.let {
                CradleActions.seat(
                    world,
                    BlockPos(cradle.x, cradle.y, cradle.z),
                    player,
                    JarActions.filledJar(it),
                    crews,
                )
            }
        val seatedWell = seated?.let { store.getComponent(it, studio.craftory.core.api.well.Glimmerwell.type) }
        val cradleState = store.getComponent(cradleRef, CradleState.type)
        check(
            caughtAndRemoved && seatedWell?.state == WellState.SEATED &&
                seatedWell.claimCrewId == cradleCrew.id &&
                cradleState?.seatedStrength == WellStrength.FLICKER.name,
            "Cradle seats the caught Flicker and claims its crew",
            "caught=$caughtAndRemoved, state=${seatedWell?.state}, claim=${seatedWell?.claimCrewId}",
        )
        CraftorySelfTest.place(world, store, run.sectionRef, section, CraftoryIds.CANISTER, storage, EnergyMember.type)
        CraftorySelfTest.place(
            world,
            store,
            run.sectionRef,
            section,
            CraftoryIds.SCORCH_OVEN,
            consumer,
            EnergyMember.type,
        )
        CraftoryCore.energyAccess(world).setDemand(BlockPos.pack(consumer.x, consumer.y, consumer.z), 1)
        placePlain(CraftoryIds.SCRAP_PLATING, tasteA)
        placePlain(CraftoryIds.SCRAP_PLATING, tasteB)
        val initialHarmony = cradleState?.harmony
        CradleHarmonyEvents.recheck(world.entityStore.store, Vector3i(tasteA.x, tasteA.y, tasteA.z))
        report("Harmony recheck called directly after world.setBlock; PlaceBlockEvent does not fire for this test")
        world.execute {
            if (cleaned) return@execute
            harmonyRechecked = true
            val updated = store.getComponent(cradleRef, CradleState.type)?.harmony
            check(
                updated != null && updated > (initialHarmony ?: 0),
                "Harmony recomputes after taste blocks",
                "harmony=$updated",
            )
        }
        val tierCrew = crews.crewOf(tierOwner)
        crews.setTier(tierCrew.id, 2)
        crews.grantKnowledge(tierOwner, NotebookTierProgression.FIRST_SPARK_KNOWLEDGE)
        val withoutSeat = NotebookTierProgression.evaluateTier(crews.crewOf(tierOwner), crews)
        crews.grantKnowledge(tierOwner, NotebookTierProgression.SEATED_FLICKER_KNOWLEDGE)
        val withSeat = NotebookTierProgression.evaluateTier(crews.crewOf(tierOwner), crews)
        check(
            withoutSeat == null && withSeat?.tier == 3,
            "Tier 3 requires seated_flicker in addition to First Spark",
            "without=$withoutSeat, with=${withSeat?.tier}",
        )
    }

    private fun prepareTickChecks() {
        val section = section()
        CraftorySelfTest.place(world, store, run.sectionRef, section, CraftoryIds.BURNER, coilBurner, BurnerState.type)
        CraftorySelfTest.place(world, store, run.sectionRef, section, CraftoryIds.CRACKLE_COIL, coil, EnergyMember.type)
        CraftorySelfTest.place(
            world,
            store,
            run.sectionRef,
            section,
            CraftoryIds.CANISTER,
            coilStorage,
            EnergyMember.type,
        )
        CraftorySelfTest.place(
            world,
            store,
            run.sectionRef,
            section,
            CraftoryIds.BURNER,
            resonatorBurner,
            BurnerState.type,
        )
        CraftorySelfTest.place(
            world,
            store,
            run.sectionRef,
            section,
            CraftoryIds.RESONATOR,
            resonator,
            ResonatorState.type,
        )
        CraftorySelfTest.place(world, store, run.sectionRef, section, CraftoryIds.BURNER, sparkBurner, BurnerState.type)
        placePlain(CraftoryIds.CYAN_CRYSTAL_BLOCK, cyanCrystal)
        prepareBurner(coilBurner)
        prepareBurner(resonatorBurner)
        prepareBurner(sparkBurner, sparkOwner)
        val resonatorRef = entityAt(resonator) ?: error("Resonator block entity missing")
        val state = store.getComponent(resonatorRef, ResonatorState.type) ?: error("Resonator state missing")
        state.crystalItemId = CraftoryIds.CYAN_CRYSTAL_SHARD
        state.tuning = 30
        state.clouded = false
        state.conversions = 0
        ResonatorRuntimeConfig.crystalUses = 3
        val sparkCrew = crews.crewOf(sparkOwner)
        crews.setTier(sparkCrew.id, 2)
        val machineRef = entityAt(sparkBurner) ?: error("First Spark Burner missing")
        val triggered =
            FirstSpark.tryTrigger(
                world,
                store,
                machineRef,
                Vector3i(sparkBurner.x, sparkBurner.y, sparkBurner.z),
                crews,
                shouldContinue = { !cleaned },
            ) { vessaRef = it }
        val triggeredAgain =
            FirstSpark.tryTrigger(
                world,
                store,
                machineRef,
                Vector3i(sparkBurner.x, sparkBurner.y, sparkBurner.z),
                crews,
            )
        store.getComponent(machineRef, MachineOwner.type)?.placerUuid = null
        check(
            triggered && !triggeredAgain && crews.hasKnowledge(sparkOwner, CraftoryIds.FIRST_SPARK_KNOWLEDGE),
            "First Spark grants crew knowledge once",
            "first=$triggered, second=$triggeredAgain",
        )
    }

    private fun prepareBurner(
        position: CraftorySelfTest.TestPosition,
        owner: UUID? = null,
    ) {
        val ref = entityAt(position) ?: error("Burner block entity missing at $position")
        val burner = store.getComponent(ref, BurnerState.type) ?: error("Burner state missing at $position")
        val member = store.getComponent(ref, EnergyMember.type) ?: error("Burner energy member missing at $position")
        val fizzRank = requireNotNull(CraftoryCore.gradeRank("fizz"))
        burner.burning = true
        burner.burnTicks = 10_000
        member.production = BurnerMachine.FIZZ_PER_SOLVE_TICK
        member.productionRank = fizzRank
        if (owner != null) store.putComponent(ref, MachineOwner.type, MachineOwner(owner))
    }

    private fun ensureEmpty() {
        val blockSection =
            store.getComponent(run.sectionRef, BlockSection.getComponentType()) ?: error("block section missing")
        positions.forEach { position ->
            check(
                blockSection.get(position.x and 31, position.y and 31, position.z and 31) == BlockType.EMPTY_ID,
            ) { "Spark selftest fixture is not empty at $position" }
        }
    }

    private fun placePlain(
        blockId: String,
        position: CraftorySelfTest.TestPosition,
    ) {
        val blockType = BlockType.fromString(blockId) ?: error("missing block type $blockId")
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                run.sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.getAssetMap().getIndex(blockType.id),
                blockType,
                0,
                0,
                com.hypixel.hytale.server.core.universe.world.SetBlockSettings.NO_UPDATE_STATE,
            ),
        ) { "failed to place $blockId" }
    }

    private fun section(): BlockComponentSection =
        store.getComponent(run.sectionRef, BlockComponentSection.getComponentType())
            ?: error("block component section missing")

    private fun entityAt(position: CraftorySelfTest.TestPosition): Ref<ChunkStore>? =
        BlockModule.getBlockEntity(world, position.x, position.y, position.z)

    private fun cleanup() {
        if (cleaned) return
        cleaned = true
        if (fixturesStarted) CraftoryCore.wells().remove(world, well)
        vessaRef?.takeIf { it.isValid }?.let { world.entityStore.store.removeEntity(it, RemoveReason.REMOVE) }
        if (fixturesStarted) runCatching { CraftorySelfTest.clear(world, run.sectionRef, positions) }
        ResonatorRuntimeConfig.crystalUses = originalCrystalUses
        runCatching { crews.close() }
        runCatching { Files.deleteIfExists(testDirectory.resolve("crews.json")) }
        runCatching { Files.deleteIfExists(testDirectory) }
        val remainingWells =
            BlockModule
                .getBlockEntity(world, well.x, well.y, well.z)
                ?.takeIf { store.getComponent(it, studio.craftory.core.api.well.Glimmerwell.type) != null }
                ?.let { 1 } ?: 0
        val remainingVessa = if (vessaRef?.isValid == true) 1 else 0
        val blockSection = store.getComponent(run.sectionRef, BlockSection.getComponentType())
        val remainingBlocks =
            positions.count { position ->
                blockSection?.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
            }
        report(
            "spark cleanup (wells=$remainingWells, vessa=$remainingVessa, blocks=$remainingBlocks, config=${ResonatorRuntimeConfig.crystalUses == originalCrystalUses})",
        )
    }

    private fun finish() {
        if (completed) return
        completed = true
        cleanup()
        run.sparkCleanup = null
        completion?.invoke()
        completion = null
    }

    private fun check(
        passed: Boolean,
        name: String,
        detail: String = "",
    ) {
        if (passed) run.passed++ else run.failed++
        report("${if (passed) "PASS" else "FAIL"} $name${if (detail.isNotEmpty()) " ($detail)" else ""}")
    }

    private fun report(message: String) {
        run.context.sendMessage(Message.raw("selftest: $message"))
    }

    private companion object {
        const val CONVERTER_Y_MASK = 0x800L shl 26
    }
}
