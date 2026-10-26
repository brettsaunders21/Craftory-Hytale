package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
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
import java.util.function.BiConsumer
import kotlin.math.abs

@Suppress("LongMethod", "MaxLineLength", "TooManyFunctions")
internal class SparkSelfTest(
    private val run: SelfTestRun.Run,
) {
    private val world = run.world
    private val store = world.chunkStore.store
    private val cradle = CraftorySelfTest.TestPosition(10_020, 148, 9_990)
    private val well = BlockPos(cradle.x, cradle.y + 1, cradle.z)
    private val tasteA = CraftorySelfTest.TestPosition(10_020, 148, 9_989)
    private val tasteB = CraftorySelfTest.TestPosition(10_020, 148, 9_991)
    private val coilBurner = CraftorySelfTest.TestPosition(10_028, 148, 9_990)
    private val coil = CraftorySelfTest.TestPosition(10_028, 149, 9_990)
    private val coilStorage = CraftorySelfTest.TestPosition(coil.x, coil.y + 1, coil.z)
    private val resonatorBurner = CraftorySelfTest.TestPosition(10_036, 148, 9_990)
    private val resonator = CraftorySelfTest.TestPosition(10_036, 149, 9_990)
    private val sparkBurner = CraftorySelfTest.TestPosition(10_039, 148, 9_992)
    private val cyanCrystal = CraftorySelfTest.TestPosition(10_040, 148, 9_990)
    private val positions =
        listOf(
            cradle,
            CraftorySelfTest.TestPosition(well.x, well.y, well.z),
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
    private var resonatorDrained = false
    private var firstSparkCrystalAvailable = false
    private var vessaRef: Ref<EntityStore>? = null
    private var vessaTriggerTick: Long? = null
    private var completion: (() -> Unit)? = null

    @Suppress("TooGenericExceptionCaught")
    fun start(onComplete: () -> Unit) {
        completion = onComplete
        run.sparkCleanup = ::cleanup
        run.sparkTest = this
        try {
            checkSparkVanillaIds()
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
        if (!harmonyRechecked) return
        energyTicks++
        resolveVessaRef()
        val energy = CraftoryCore.energyAccess(world)
        val cradleId = BlockPos.pack(cradle.x, cradle.y, cradle.z)
        val coilId = BlockPos.pack(coil.x, coil.y, coil.z)
        val coilStorageId = BlockPos.pack(coilStorage.x, coilStorage.y, coilStorage.z)
        val burnerId = BlockPos.pack(coilBurner.x, coilBurner.y, coilBurner.z)
        val resonatorId = BlockPos.pack(resonator.x, resonator.y, resonator.z)
        val resonatorOutputId = BlockPos.pack(resonator.x, resonator.y, resonator.z) xor CONVERTER_Y_MASK
        val dimRank = requireNotNull(CraftoryCore.gradeRank("dim_glimmer"))
        val fizzRank = requireNotNull(CraftoryCore.gradeRank("fizz"))
        val resonatorRef = entityAt(resonator)?.takeIf { it.isValid }
        val resonatorState = resonatorRef?.let { store.getComponent(it, ResonatorState.type) }
        val resonatorSupply = energy.networkSupply(resonatorOutputId)
        if (energy.converterOutputNetwork(resonatorId) >= 0 &&
            resonatorSupply > 0L &&
            energy.networkGrade(resonatorOutputId) == dimRank
        ) {
            resonatorSawOutput = true
        }
        if (resonatorSawOutput && resonatorState?.clouded == true && resonatorSupply == 0L) {
            resonatorDrained = true
        }
        val cradleNetwork = energy.networkOf(cradleId)
        val cradleGrade = energy.networkGrade(cradleId)
        val cradleSupply = energy.networkSupply(cradleId)
        val cradleReady = cradleNetwork >= 0 && cradleGrade == dimRank && cradleSupply > 0L
        val coilRef = entityAt(coil)?.takeIf { it.isValid }
        val coilOutput = coilRef?.let { store.getComponent(it, EnergyMember.type) }
        val coilStorageRef = entityAt(coilStorage)?.takeIf { it.isValid }
        val coilStorageMember = coilStorageRef?.let { store.getComponent(it, EnergyMember.type) }
        val burnerRef = entityAt(coilBurner)?.takeIf { it.isValid }
        val burnerMember = burnerRef?.let { store.getComponent(it, EnergyMember.type) }
        val coilOutputId = BlockPos.pack(coil.x, coil.y, coil.z) xor CONVERTER_Y_MASK
        val outputGrade = energy.networkGrade(coilOutputId)
        val outputNetwork = energy.converterOutputNetwork(coilId)
        val burnerNetwork = energy.networkOf(burnerId)
        val coilStorageNetwork = energy.networkOf(coilStorageId)
        val burnerSupply = energy.networkSupply(burnerId)
        val burnerMemberOk =
            burnerRef != null && burnerMember != null &&
                burnerMember.productionRank == fizzRank &&
                energy.networkGrade(burnerId) == fizzRank
        val networksSeparate =
            burnerNetwork >= 0 && coilStorageNetwork >= 0 && burnerNetwork != coilStorageNetwork
        val coilReady =
            coilRef != null && coilOutput != null &&
                coilStorageMember?.stored?.let { it > 0L } == true &&
                coilStorageMember.storeRank == dimRank &&
                outputGrade == dimRank && outputNetwork >= 0 &&
                energy.networkSupply(coilOutputId) > 0L &&
                burnerMemberOk && energy.networkMembers(burnerId) > 0 &&
                burnerSupply > 0L && networksSeparate
        val vessaValid = vessaRef?.isValid == true
        val vessaPosition =
            vessaRef
                ?.takeIf { it.isValid }
                ?.let { world.entityStore.store.getComponent(it, TransformComponent.getComponentType()) }
                ?.position
        val vessaMet = crews.hasKnowledge(sparkOwner, CraftoryIds.VESSA_MET_KNOWLEDGE)
        val vessaPositionMatches =
            vessaPosition != null &&
                abs(vessaPosition.x - (sparkBurner.x + 0.5)) <= 1.0 &&
                abs(vessaPosition.y - (sparkBurner.y + 1.0)) <= 1.0 &&
                abs(vessaPosition.z - (sparkBurner.z + 0.5)) <= 1.0
        val vessaReady = vessaValid && vessaMet && vessaPositionMatches
        val allConditionsReady =
            cradleReady && coilReady && resonatorDrained &&
                (!firstSparkCrystalAvailable || vessaReady)
        if (!allConditionsReady && energyTicks < MAX_WAIT_TICKS) return

        checkSparkFixturesIntact("final assertions", includeTickFixtures = true)
        check(
            cradleReady,
            "Cradle network produces dim_glimmer",
            "network=$cradleNetwork, grade=$cradleGrade, supply=$cradleSupply",
        )
        check(
            coilReady,
            "Coil converts Burner fizz without joining its network",
            "coilRef=${coilRef != null}, " +
                "storage=${coilStorageMember?.stored}/${coilStorageMember?.storeRank}, " +
                "outputGrade=$outputGrade, outputNetwork=$outputNetwork, " +
                "burnerMemberOk=$burnerMemberOk, burnerSupply=$burnerSupply, " +
                "networksSeparate=$networksSeparate",
        )
        check(
            resonatorSawOutput && resonatorDrained && resonatorState?.clouded == true &&
                resonatorSupply == 0L,
            "Resonator converts then clouds at configured crystal uses",
            "conversions=${resonatorState?.conversions}, clouded=${resonatorState?.clouded}, " +
                "sawOutput=$resonatorSawOutput, drained=$resonatorDrained, output=$resonatorSupply",
        )
        if (firstSparkCrystalAvailable) {
            check(
                vessaReady,
                "First Spark spawns Vessa and grants meeting knowledge",
                "ref=${if (vessaRef == null) "null" else "set"}, " +
                    "valid=$vessaValid, met=$vessaMet, pos=$vessaPosition, " +
                    "spawn=${VessaSpawner.lastSpawnDiagnostic}",
            )
        }
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
        placePlain(CraftoryIds.SCRAP_PLATING, tasteA)
        placePlain(CraftoryIds.SCRAP_PLATING, tasteB)
        val initialHarmony = cradleState?.harmony
        CradleHarmonyEvents.recheck(world.entityStore.store, Vector3i(tasteA.x, tasteA.y, tasteA.z))
        report("Harmony recheck called directly after world.setBlock; PlaceBlockEvent does not fire for this test")
        world.execute {
            if (cleaned) return@execute
            harmonyRechecked = true
            val updatedRef = entityAt(cradle)?.takeIf { it.isValid }
            if (updatedRef == null) {
                check(false, "Harmony recomputes after taste blocks", "cradle entity missing")
            } else {
                val updated = store.getComponent(updatedRef, CradleState.type)?.harmony
                check(
                    updated != null && updated > (initialHarmony ?: 0),
                    "Harmony recomputes after taste blocks",
                    "harmony=$updated",
                )
            }
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
        checkSparkFixturesIntact("live setup", includeTickFixtures = false)
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
        firstSparkCrystalAvailable = BlockType.fromString(CraftoryIds.CRYSTAL_CYAN_BLOCK) != null
        if (firstSparkCrystalAvailable) {
            placePlain(CraftoryIds.CRYSTAL_CYAN_BLOCK, cyanCrystal)
        } else {
            report("SKIP First Spark trigger check (missing block ${CraftoryIds.CRYSTAL_CYAN_BLOCK})")
        }
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
        if (firstSparkCrystalAvailable) {
            val triggered =
                FirstSpark.tryTrigger(
                    world,
                    store,
                    machineRef,
                    Vector3i(sparkBurner.x, sparkBurner.y, sparkBurner.z),
                    crews,
                    shouldContinue = { !cleaned },
                ) { vessaRef = it }
            if (triggered) vessaTriggerTick = world.tick
            val triggeredAgain =
                FirstSpark.tryTrigger(
                    world,
                    store,
                    machineRef,
                    Vector3i(sparkBurner.x, sparkBurner.y, sparkBurner.z),
                    crews,
                )
            check(
                triggered && !triggeredAgain && crews.hasKnowledge(sparkOwner, CraftoryIds.FIRST_SPARK_KNOWLEDGE),
                "First Spark grants crew knowledge once",
                "first=$triggered, second=$triggeredAgain",
            )
        }
        store.getComponent(machineRef, MachineOwner.type)?.placerUuid = null
        checkSparkFixturesIntact("tick setup", includeTickFixtures = true)
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
        val sectionAnchor = CraftorySelfTest.BURNER
        positions.forEach { position ->
            check(withinSection(position.x, position.y, position.z)) {
                "Spark fixture is outside the selftest section at $position (anchor=$sectionAnchor)"
            }
            check(
                blockSection.get(position.x and 31, position.y and 31, position.z and 31) == BlockType.EMPTY_ID,
            ) { "Spark selftest fixture is not empty at $position" }
        }
        val harmonyRadius = HARMONY_RADIUS
        check(
            withinSection(cradle.x - harmonyRadius, cradle.y - harmonyRadius, cradle.z - harmonyRadius) &&
                withinSection(cradle.x + harmonyRadius, cradle.y + harmonyRadius, cradle.z + harmonyRadius),
        ) { "Spark Harmony radius leaves the selftest section" }
        val scanRadius = FIRST_SPARK_SCAN_RADIUS
        check(
            withinSection(sparkBurner.x - scanRadius, sparkBurner.y - scanRadius, sparkBurner.z - scanRadius) &&
                withinSection(sparkBurner.x + scanRadius, sparkBurner.y + scanRadius, sparkBurner.z + scanRadius),
        ) { "First Spark radius leaves the selftest section" }
        val existingFixtures = CraftorySelfTest.testPositions()
        val harmonyOverlap =
            existingFixtures.firstOrNull { existing ->
                existing.x in (cradle.x - harmonyRadius)..(cradle.x + harmonyRadius) &&
                    existing.y in (cradle.y - harmonyRadius)..(cradle.y + harmonyRadius) &&
                    existing.z in (cradle.z - harmonyRadius)..(cradle.z + harmonyRadius)
            }
        check(harmonyOverlap == null) { "Spark Harmony radius overlaps existing fixture at $harmonyOverlap" }
        val sparkOverlap =
            existingFixtures.firstOrNull { existing ->
                val dx = existing.x.toLong() - sparkBurner.x
                val dy = existing.y.toLong() - sparkBurner.y
                val dz = existing.z.toLong() - sparkBurner.z
                dx * dx + dy * dy + dz * dz <= FIRST_SPARK_SCAN_RADIUS_SQUARED
            }
        check(sparkOverlap == null) { "First Spark radius overlaps existing fixture at $sparkOverlap" }
    }

    private fun checkSparkFixturesIntact(
        phase: String,
        includeTickFixtures: Boolean,
    ) {
        val fixtures =
            mutableListOf(
                FixtureExpectation("Cradle", cradle, "CradleState, EnergyMember") {
                    store.getComponent(it, CradleState.type) != null &&
                        store.getComponent(it, EnergyMember.type) != null
                },
            )
        if (includeTickFixtures) {
            fixtures +=
                listOf(
                    FixtureExpectation("Coil Burner", coilBurner, "BurnerState, EnergyMember") {
                        store.getComponent(it, BurnerState.type) != null &&
                            store.getComponent(it, EnergyMember.type) != null
                    },
                    FixtureExpectation("Coil", coil, "EnergyMember") {
                        store.getComponent(it, EnergyMember.type) != null
                    },
                    FixtureExpectation("Coil storage", coilStorage, "EnergyMember") {
                        store.getComponent(it, EnergyMember.type) != null
                    },
                    FixtureExpectation("Resonator Burner", resonatorBurner, "BurnerState, EnergyMember") {
                        store.getComponent(it, BurnerState.type) != null &&
                            store.getComponent(it, EnergyMember.type) != null
                    },
                    FixtureExpectation("Resonator", resonator, "ResonatorState, EnergyMember") {
                        store.getComponent(it, ResonatorState.type) != null &&
                            store.getComponent(it, EnergyMember.type) != null
                    },
                    FixtureExpectation("First Spark Burner", sparkBurner, "BurnerState, EnergyMember") {
                        store.getComponent(it, BurnerState.type) != null &&
                            store.getComponent(it, EnergyMember.type) != null
                    },
                )
        }
        val missing =
            fixtures.mapNotNull { fixture ->
                val ref = entityAt(fixture.position)
                when {
                    ref == null -> {
                        "${fixture.name} at ${fixture.position}: entity missing"
                    }

                    !ref.isValid -> {
                        "${fixture.name} at ${fixture.position}: entity ref invalid"
                    }

                    !fixture.hasComponent(ref) -> {
                        "${fixture.name} at ${fixture.position}: missing ${fixture.expectedComponents}"
                    }

                    else -> {
                        null
                    }
                }
            }
        check(
            missing.isEmpty(),
            "Spark fixtures intact ($phase)",
            if (missing.isEmpty()) "" else missing.joinToString("; "),
        )
    }

    private fun checkSparkVanillaIds() {
        val missing = mutableListOf<String>()
        val blockAssets = BlockType.getAssetMap()
        CraftoryIds.CRYSTAL_TRIGGER_BLOCKS.forEach { id ->
            if (blockAssets.getAsset(id) == null) missing += "block $id"
        }
        val shardIds = listOf(CraftoryIds.CYAN_CRYSTAL_SHARD, CraftoryIds.VOID_CRYSTAL_SHARD)
        val itemAssets = Item.getAssetMap()
        shardIds.forEach { id ->
            if (itemAssets.getAsset(id) == null) missing += "item $id"
        }
        if (NPCPlugin.get().getIndex(CraftoryIds.VESSA_ROLE) == ASSET_INDEX_NOT_FOUND) {
            missing += "NPC role ${CraftoryIds.VESSA_ROLE}"
        }
        if (ModelAsset.getAssetMap().getAsset(CraftoryIds.VESSA_ROLE) == null) {
            missing += "model ${CraftoryIds.VESSA_ROLE}"
        }
        if (ParticleSystem.getAssetMap().getAsset(CraftoryIds.SPARK_PARTICLE) == null) {
            missing += "particle ${CraftoryIds.SPARK_PARTICLE}"
        }
        if (SoundEvent.getAssetMap().getIndex(CraftoryIds.SPARK_SOUND) == ASSET_INDEX_NOT_FOUND) {
            missing += "sound ${CraftoryIds.SPARK_SOUND}"
        }
        val passed = missing.isEmpty()
        check(passed, "Spark vanilla ids resolve", if (passed) "" else "missing=${missing.joinToString()}")
    }

    private fun withinSection(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val anchor = CraftorySelfTest.BURNER
        return (x shr 5) == (anchor.x shr 5) &&
            (y shr 5) == (anchor.y shr 5) &&
            (z shr 5) == (anchor.z shr 5)
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
        val entityStore = world.entityStore.store
        vessaNpcsWithin(VESSA_CLEANUP_RADIUS).forEach { ref ->
            if (ref.isValid) runCatching { entityStore.removeEntity(ref, RemoveReason.REMOVE) }
        }
        vessaRef = null
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
        val remainingVessa = vessaNpcsWithin(VESSA_CLEANUP_RADIUS).count { it.isValid }
        val blockSection = store.getComponent(run.sectionRef, BlockSection.getComponentType())
        val remainingBlocks =
            positions.count { position ->
                blockSection?.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
            }
        report(
            "spark cleanup (wells=$remainingWells, vessa=$remainingVessa, blocks=$remainingBlocks, config=${ResonatorRuntimeConfig.crystalUses == originalCrystalUses})",
        )
    }

    private fun resolveVessaRef() {
        if (vessaRef?.isValid == true) return
        val triggeredAt = vessaTriggerTick ?: return
        if (world.tick - triggeredAt > VESSA_SPAWN_TIMEOUT_TICKS) return
        vessaRef = vessaNpcsWithin(VESSA_POLL_RADIUS).firstOrNull()
    }

    private fun vessaNpcsWithin(radius: Int): List<Ref<EntityStore>> {
        val npcType = NPCEntity.getComponentType() ?: return emptyList()
        val entityStore = world.entityStore.store
        val refs = mutableListOf<Ref<EntityStore>>()
        val radiusSquared = radius.toLong() * radius
        val centerX = sparkBurner.x + 0.5
        val centerY = sparkBurner.y + 0.5
        val centerZ = sparkBurner.z + 0.5
        entityStore.forEachChunk(
            Query.and(npcType),
            BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                for (index in 0 until chunk.size()) {
                    val ref = chunk.getReferenceTo(index)
                    val npc = chunk.getComponent(index, npcType)
                    if (npc?.roleName == CraftoryIds.VESSA_ROLE && ref.isValid) {
                        val position =
                            entityStore.getComponent(ref, TransformComponent.getComponentType())?.position
                        if (position != null) {
                            val dx = position.x - centerX
                            val dy = position.y - centerY
                            val dz = position.z - centerZ
                            if (dx * dx + dy * dy + dz * dz <= radiusSquared) refs += ref
                        }
                    }
                }
            },
        )
        return refs
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

    private data class FixtureExpectation(
        val name: String,
        val position: CraftorySelfTest.TestPosition,
        val expectedComponents: String,
        val hasComponent: (Ref<ChunkStore>) -> Boolean,
    )

    private companion object {
        const val HARMONY_RADIUS = 4
        const val FIRST_SPARK_SCAN_RADIUS = 8
        const val FIRST_SPARK_SCAN_RADIUS_SQUARED = 64L
        const val VESSA_POLL_RADIUS = 4
        const val VESSA_CLEANUP_RADIUS = 16
        const val VESSA_SPAWN_TIMEOUT_TICKS = 100L
        const val ASSET_INDEX_NOT_FOUND = Int.MIN_VALUE
        const val CONVERTER_Y_MASK = 0x800L shl 26
        const val MAX_WAIT_TICKS = 200
    }
}
