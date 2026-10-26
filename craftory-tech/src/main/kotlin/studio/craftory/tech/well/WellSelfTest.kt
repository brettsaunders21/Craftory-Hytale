package studio.craftory.tech.well

import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.transport.HurlPoint
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.transport.HurlRides
import studio.craftory.tech.transport.HurlStartFailure
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random

private typealias BlockEntityRef = Ref<ChunkStore>

private typealias WellSelfTestReport = (String) -> Unit

internal object WellSelfTest {
    internal val CAUGHT_POS = BlockPos(10_030, 148, 10_010)
    internal val FORCED_POS = BlockPos(10_029, 148, 10_010)
    internal val BLOCK_REMOVED_POS = BlockPos(10_028, 148, 10_010)
    internal val VANE_POS = BlockPos(10_020, 148, 10_015)
    internal val STAKED_POS = BlockPos(10_022, 148, 10_020)
    internal val SMOTHERED_POS = BlockPos(10_010, 148, 10_020)
    internal val CALMED_POS = BlockPos(10_010, 148, 10_027)

    fun run(
        world: World,
        report: WellSelfTestReport,
    ) {
        val tempDir = Files.createTempDirectory("craftory-well-selftest")
        listOf(CAUGHT_POS, FORCED_POS, BLOCK_REMOVED_POS, STAKED_POS, SMOTHERED_POS, CALMED_POS)
            .forEach(WellSelfTestExclusions::registerWell)
        try {
            testJarAndScan(world, tempDir, report)
            testEmptyBlockRemoval(world, report)
            testStormcall(world, report)
            WellStormVaneSelfTest.run(world, report)
            testRuntimeAssets(report)
            testChunkReload(world, report)
            Helpers.testCarrierReload(world, report)
            testDamperSmothering(world, report)
            Helpers.testGuardianDeath(world, report)
            Helpers.testStakeLifecycle(world, tempDir, report)
            testHurlRefusal(world, report)
            val remaining = CraftoryCore.wells().wellsNear(world.chunkStore.store, CAUGHT_POS, 64).size
            check(remaining == 0) { "well cleanup left $remaining indexed wells within radius 64" }
            report("selftest: cleanup (wells=$remaining)")
        } finally {
            CraftoryCore.wells().remove(world, CAUGHT_POS)
            CraftoryCore.wells().remove(world, FORCED_POS)
            world.chunkStore.store
                .getResource(WellBirthState.type)
                .stormCrackles
                .remove(FORCED_POS.pack())
            CraftoryCore.wells().remove(world, BLOCK_REMOVED_POS)
            CraftoryCore.wells().remove(world, STAKED_POS)
            CraftoryCore.wells().remove(world, SMOTHERED_POS)
            CraftoryCore.wells().remove(world, CALMED_POS)
            Files.deleteIfExists(tempDir.resolve("crews.json"))
            Files.deleteIfExists(tempDir)
            listOf(CAUGHT_POS, FORCED_POS, BLOCK_REMOVED_POS, STAKED_POS, SMOTHERED_POS, CALMED_POS)
                .forEach(WellSelfTestExclusions::releaseWell)
            val exclusions = WellSelfTestExclusions.cleanupCounts()
            val guardians =
                world.entityStore.store
                    .getResource(WellGuardianIndex.type)
                    .refsByWellId.size
            report(
                "selftest: cleanup (wellBlocks=${exclusions.first}, wellEntities=${exclusions.second}, " +
                    "wellPositions=${exclusions.third}, guardians=$guardians)",
            )
        }
    }

    private fun testJarAndScan(
        world: World,
        tempDir: Path,
        report: (String) -> Unit,
    ) {
        val store = world.chunkStore.store
        val spawned =
            CraftoryCore.wells().spawn(
                world,
                CAUGHT_POS,
                Temperament.SPARK,
                WellStrength.FLICKER,
                WellState.WILD,
            ) ?: error("well spawn failed")
        check(CraftoryCore.wells().count(store) > 0) { "well index did not register spawn" }
        check(ScanScoring.score(100, listOf(100, 100, 100), 40, 8).perfect) { "perfect score failed" }
        check(!ScanScoring.score(100, listOf(110, 125, 135), 40, 8).perfect) { "sloppy score failed" }
        report("selftest: PASS well spawn/index and scan scores")

        val player = UUID.randomUUID()
        val crews = CrewService(tempDir.resolve("crews.json"))
        try {
            val crew = crews.crewOf(player)
            crews.setTier(crew.id, 2)
            val well = store.getComponent(spawned, Glimmerwell.type) ?: error("spawned well is missing")
            well.state = WellState.TETHERED
            well.claimCrewId = crew.id
            val result = JarActions.checkCatch(spawned, player, crews, store)
            check(result is JarCatchResult.Caught) { "Flicker catch failed: $result" }
            val captureState = WellCaptureState()
            val missedPlayer = UUID.randomUUID()
            check(recordCapturePress(captureState, player, result.wellId, 100) == CapturePressResult.Progress(1))
            check(recordCapturePress(captureState, player, result.wellId, 110) == CapturePressResult.Progress(2))
            check(recordCapturePress(captureState, player, result.wellId, 120) == CapturePressResult.Complete)
            check(recordCapturePress(captureState, missedPlayer, result.wellId, 100) == CapturePressResult.Progress(1))
            check(recordCapturePress(captureState, missedPlayer, result.wellId, 141) == CapturePressResult.Miss)
            report("selftest: PASS three-press capture success and miss timing")
            check(JarActions.commitCatch(world, spawned, CAUGHT_POS, player, result, crews)) {
                "Flicker catch commit failed"
            }
            check(crews.insight(crew.id)[Temperament.SPARK] == 15L) { "Flicker Insight missing" }
            check(crews.hasKnowledge(player, JarActions.FIRST_FLICKER_ID)) { "first Flicker knowledge missing" }
            val filled = JarActions.filledJar(result)
            check(filled.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) == "spark") {
                "filled jar metadata missing"
            }
            check(filled.getFromMetadataOrNull(GobJarMetadata.STRENGTH) == WellStrength.FLICKER.name) {
                "filled jar strength metadata missing"
            }
            check(filled.getFromMetadataOrNull(GobJarMetadata.TASTE) == "Ingredient_Scraps") {
                "filled jar taste metadata missing"
            }
            check(CraftoryCore.wells().count(store) == 0) { "jarred well remained indexed" }
            report("selftest: PASS Flicker jar metadata, knowledge, and Insight")
        } finally {
            crews.close()
        }
    }

    private fun testEmptyBlockRemoval(
        world: World,
        report: (String) -> Unit,
    ) {
        val store = world.chunkStore.store
        CraftoryCore.wells().spawn(
            world,
            BLOCK_REMOVED_POS,
            Temperament.GROWTH,
            WellStrength.STEADY,
            WellState.WILD,
        ) ?: error("block-removal well spawn failed")
        val before = CraftoryCore.wells().wellsNear(store, BLOCK_REMOVED_POS, 64).size
        check(before > 0) { "block-removal well was not indexed" }
        clearBlock(world, BLOCK_REMOVED_POS)
        val after = CraftoryCore.wells().wellsNear(store, BLOCK_REMOVED_POS, 64).size
        check(after == before - 1) { "empty-block removal did not drop the well index count: $before -> $after" }
        report("selftest: PASS empty-block removal clears well index")
    }

    private fun testStormcall(
        world: World,
        report: (String) -> Unit,
    ) {
        val ref = WellBirths.forceStormcall(world, FORCED_POS, Random(7)) ?: error("forced Stormcall failed")
        val well = world.chunkStore.store.getComponent(ref, Glimmerwell.type) ?: error("forced well state is missing")
        check(
            well.temperament == Temperament.SPARK && well.state == WellState.WILD,
        ) { "forced Stormcall did not create a Wild Spark Well" }
        val expectedExpiry = world.tick + 30L * 20L
        check(
            world.chunkStore.store
                .getResource(WellBirthState.type)
                .stormCrackles[FORCED_POS.pack()] == expectedExpiry,
        ) { "forced Stormcall did not start 30-second crackle tracking" }
        report("selftest: PASS forced Stormcall birth, fake strike, and crackle")
        CraftoryCore.wells().remove(world, FORCED_POS)
    }

    private fun testRuntimeAssets(report: WellSelfTestReport) {
        val missingParticles =
            listOf("Lightning", "Magic_Sparks_GS", "Dungeon_Goblin_Toxic_Water")
                .filter { ParticleSystem.getAssetMap().getAsset(it) == null }
        val missingSounds =
            listOf("SFX_Global_Weather_Thunder", "SFX_Crystal_Hit")
                .filter { SoundEvent.getAssetMap().getIndex(it) < 0 }
        val chainBeam = Beam.getAssetMap().getIndex("Chain")
        report(
            "selftest: asset check " +
                "(missingParticles=$missingParticles missingSounds=$missingSounds chainBeam=$chainBeam)",
        )
    }

    private fun testChunkReload(
        world: World,
        report: (String) -> Unit,
    ) {
        val vane = WellSelfTestFixtures.vanePosition(world)
        val position = CraftorySelfTest.TestPosition(vane.x, vane.y, vane.z)
        val store = world.chunkStore.store
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                ?: error("Vane persistence section is not loaded")
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("Vane persistence section is missing")
        CraftorySelfTest.place(
            world,
            store,
            sectionRef,
            section,
            CraftoryIds.STORM_VANE,
            position,
            StormVaneState.type,
        )
        var ref =
            BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                ?: error("Vane persistence block entity is missing")
        WellSelfTestExclusions.registerBlock(ref)
        try {
            val state = store.getComponent(ref, StormVaneState.type) ?: error("Vane state is missing")
            state.visualOn = true
            state.lastUpdateTick = world.tick
            store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType())?.markNeedsSaving(store)
            val unloaded = store.removeEntity(ref, RemoveReason.UNLOAD)
            WellSelfTestExclusions.releaseBlock(ref)
            ref = store.addEntity(unloaded, AddReason.LOAD) ?: error("Vane reload failed")
            WellSelfTestExclusions.registerBlock(ref)
            val restored = store.getComponent(ref, StormVaneState.type)
            check(
                restored?.visualOn == true && restored.lastUpdateTick == world.tick,
            ) { "Storm Vane chunk unload/reload lost component state" }
            report("selftest: PASS Storm Vane chunk unload/reload persists state")
        } finally {
            WellSelfTestExclusions.releaseBlock(ref)
            CraftorySelfTest.clear(world, sectionRef, listOf(position))
        }
    }

    private fun testDamperSmothering(
        world: World,
        report: (String) -> Unit,
    ) {
        val wellRef =
            CraftoryCore.wells().spawn(
                world,
                SMOTHERED_POS,
                Temperament.SPARK,
                WellStrength.STEADY,
                WellState.WILD,
            ) ?: error("Damper test Well spawn failed")
        val store = world.chunkStore.store
        val positions =
            listOf(
                CraftorySelfTest.TestPosition(SMOTHERED_POS.x + 2, SMOTHERED_POS.y, SMOTHERED_POS.z),
                CraftorySelfTest.TestPosition(SMOTHERED_POS.x - 2, SMOTHERED_POS.y, SMOTHERED_POS.z),
                CraftorySelfTest.TestPosition(SMOTHERED_POS.x, SMOTHERED_POS.y, SMOTHERED_POS.z + 2),
            )
        val sectionRef =
            world.chunkStore.getChunkSectionReferenceAtBlock(SMOTHERED_POS.x, SMOTHERED_POS.y, SMOTHERED_POS.z)
                ?: error("Damper test section is not loaded")
        val section =
            store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                ?: error("Damper test section is missing")
        val refs = mutableListOf<BlockEntityRef>()
        try {
            Helpers.placeTestDampers(world, store, sectionRef, section, positions, refs)
            val well = store.getComponent(wellRef, Glimmerwell.type) ?: error("Damper test Well is missing")
            Helpers.assertDamperSmothering(store, well, refs)
            report("selftest: PASS three powered Fizz Dampers smother and reduce strength")
        } finally {
            refs.forEach(WellSelfTestExclusions::releaseBlock)
            CraftorySelfTest.clear(world, sectionRef, positions)
            CraftoryCore.wells().remove(world, SMOTHERED_POS)
        }
    }

    private object Helpers {
        fun placeTestDampers(
            world: World,
            store: Store<ChunkStore>,
            sectionRef: Ref<ChunkStore>,
            section: BlockComponentSection,
            positions: List<CraftorySelfTest.TestPosition>,
            refs: MutableList<BlockEntityRef>,
        ) {
            positions.forEach { position ->
                CraftorySelfTest.place(
                    world,
                    store,
                    sectionRef,
                    section,
                    CraftoryIds.FIZZ_DAMPER,
                    position,
                    FizzDamperState.type,
                )
                val ref =
                    BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                        ?: error("Damper block entity is missing")
                refs.add(ref)
                WellSelfTestExclusions.registerBlock(ref)
                store.getComponent(ref, FizzDamperState.type)?.visualOn = true
                val energy = store.getComponent(ref, EnergyMember.type) ?: EnergyMember()
                energy.delivered = 2
                energy.demand = 2
                store.putComponent(ref, EnergyMember.type, energy)
            }
        }

        fun assertDamperSmothering(
            store: Store<ChunkStore>,
            well: Glimmerwell,
            refs: List<BlockEntityRef>,
        ) {
            val dampers = FizzDamperSystem()
            check(dampers.countPoweredDampers(store, WellSelfTest.SMOTHERED_POS) == 3) {
                "three powered Dampers were not counted"
            }
            store.getComponent(refs.first(), EnergyMember.type)?.delivered = 1
            check(dampers.countPoweredDampers(store, WellSelfTest.SMOTHERED_POS) == 2) {
                "unpowered Damper counted toward smothering"
            }
            dampers.smotherIfReady(store, WellSelfTest.SMOTHERED_POS, well)
            check(well.state == WellState.WILD && well.strength == WellStrength.STEADY) {
                "fewer than three powered Dampers smothered the Well"
            }
            store.getComponent(refs.first(), EnergyMember.type)?.delivered = 2
            dampers.smotherIfReady(store, WellSelfTest.SMOTHERED_POS, well)
            check(well.state == WellState.DORMANT && well.strength == WellStrength.FLICKER) {
                "three powered Dampers did not smother and reduce strength"
            }
        }

        fun testCarrierReload(
            world: World,
            report: (String) -> Unit,
        ) {
            val store = world.entityStore.store
            val carrier =
                JarCarrier(
                    stability = 73.5,
                    temperamentId = Temperament.EMBER.id,
                    strengthId = WellStrength.STEADY.name,
                    taste = "Ingredient_Scraps",
                    wellId = WellSelfTest.STAKED_POS.pack().toString(),
                    lastTick = 17,
                    nextAmbushTick = 1217,
                    lastReadoutTick = 100,
                    originalBaseSpeed = 1.25f,
                )
            val filledJar =
                JarActions.filledJar(
                    JarCatchResult.Caught(
                        Temperament.EMBER,
                        WellStrength.STEADY,
                        "Ingredient_Scraps",
                        WellSelfTest.STAKED_POS.pack().toString(),
                    ),
                )
            val updatedJar = metadataStack(filledJar, carrier)
            val holder = EntityStore.REGISTRY.newHolder()
            holder.addComponent(JarCarrier.type, carrier)
            holder.ensureComponent(EntityStore.REGISTRY.nonSerializedComponentType)
            holder.ensureComponent(UUIDComponent.getComponentType())
            holder.addComponent(
                TransformComponent.getComponentType(),
                TransformComponent(Vector3d(10_020.5, 148.0, 10_015.5), Rotation3f.IDENTITY),
            )
            var ref = store.addEntity(holder, AddReason.SPAWN) ?: error("JarCarrier persistence entity did not spawn")
            WellSelfTestExclusions.registerEntity(ref)
            try {
                val unloaded = store.removeEntity(ref, RemoveReason.UNLOAD)
                WellSelfTestExclusions.releaseEntity(ref)
                ref = store.addEntity(unloaded, AddReason.LOAD) ?: error("JarCarrier reload failed")
                WellSelfTestExclusions.registerEntity(ref)
                val restored = store.getComponent(ref, JarCarrier.type) ?: error("JarCarrier state was not restored")
                assertCarrierState(updatedJar, restored)
                report("selftest: PASS JarCarrier metadata flush preserves stability and identity")
                report("selftest: PASS JarCarrier unload/reload preserves escort state")
            } finally {
                WellSelfTestExclusions.releaseEntity(ref)
                if (ref.isValid) store.removeEntity(ref, RemoveReason.REMOVE)
            }
        }

        private fun assertCarrierState(
            updatedJar: ItemStack,
            restored: JarCarrier,
        ) {
            check(updatedJar.getFromMetadataOrNull(GobJarMetadata.STABILITY) == 73.5)
            check(
                updatedJar.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) == Temperament.EMBER.id &&
                    updatedJar.getFromMetadataOrNull(GobJarMetadata.STRENGTH) == WellStrength.STEADY.name,
            )
            check(
                restored.stability == 73.5 &&
                    restored.temperamentId == Temperament.EMBER.id &&
                    restored.strengthId == WellStrength.STEADY.name &&
                    restored.taste == "Ingredient_Scraps" &&
                    restored.wellId == WellSelfTest.STAKED_POS.pack().toString() &&
                    restored.lastTick == 17L &&
                    restored.nextAmbushTick == 1217L &&
                    restored.lastReadoutTick == 100L &&
                    restored.originalBaseSpeed == 1.25f,
            ) { "JarCarrier unload/reload did not preserve its escort state" }
        }

        fun testGuardianDeath(
            world: World,
            report: (String) -> Unit,
        ) {
            val ref =
                CraftoryCore.wells().spawn(
                    world,
                    WellSelfTest.CALMED_POS,
                    Temperament.SPARK,
                    WellStrength.STEADY,
                    WellState.WILD,
                ) ?: error("Guardian-death test Well spawn failed")
            val well =
                world.chunkStore.store.getComponent(ref, Glimmerwell.type) ?: error("Guardian test Well is missing")
            val chunkStore = world.chunkStore.store
            val entityStore = world.entityStore.store
            val guardians = entityStore.getResource(WellGuardianIndex.type)
            val guardianId = WellSelfTest.CALMED_POS.pack().toString()
            var guardian: Ref<EntityStore>? = null
            WellSelfTestExclusions.releaseWell(WellSelfTest.CALMED_POS)
            try {
                WellGuardianCalmSystem().tryCalmNear(
                    world,
                    chunkStore,
                    Vector3d(
                        WellSelfTest.CALMED_POS.x.toDouble(),
                        WellSelfTest.CALMED_POS.y.toDouble(),
                        WellSelfTest.CALMED_POS.z.toDouble(),
                    ),
                    guardians,
                )
                val spawned = guardians.refsByWellId[guardianId] ?: error("Sparkjaw guardian did not spawn")
                guardian = spawned
                check(entityStore.getComponent(spawned, SparkjawGuardian.type)?.wellId == guardianId) {
                    "Sparkjaw did not retain its Well identity"
                }
                calmWellAfterGuardianDeath(well)
                check(well.state == WellState.DORMANT && well.strength == WellStrength.STEADY) {
                    "guardian death did not calm the Well without reducing strength"
                }
                report("selftest: PASS Sparkjaw spawn and guardian death calm")
            } finally {
                WellSelfTestExclusions.registerWell(WellSelfTest.CALMED_POS)
                guardian?.takeIf { it.isValid }?.let { entityStore.removeEntity(it, RemoveReason.REMOVE) }
                CraftoryCore.wells().remove(world, WellSelfTest.CALMED_POS)
            }
        }

        fun testStakeLifecycle(
            world: World,
            tempDir: Path,
            report: WellSelfTestReport,
        ) {
            val wellRef =
                CraftoryCore.wells().spawn(
                    world,
                    WellSelfTest.STAKED_POS,
                    Temperament.SPARK,
                    WellStrength.FLICKER,
                    WellState.DORMANT,
                ) ?: error("Stake test Well spawn failed")
            val store = world.chunkStore.store
            val well = store.getComponent(wellRef, Glimmerwell.type) ?: error("Stake test Well is missing")
            val playerUuid = UUID.randomUUID()
            val positions =
                listOf(
                    CraftorySelfTest.TestPosition(
                        WellSelfTest.STAKED_POS.x + 4,
                        WellSelfTest.STAKED_POS.y,
                        WellSelfTest.STAKED_POS.z,
                    ),
                    CraftorySelfTest.TestPosition(
                        WellSelfTest.STAKED_POS.x + 4,
                        WellSelfTest.STAKED_POS.y,
                        WellSelfTest.STAKED_POS.z + 3,
                    ),
                    CraftorySelfTest.TestPosition(
                        WellSelfTest.STAKED_POS.x - 4,
                        WellSelfTest.STAKED_POS.y,
                        WellSelfTest.STAKED_POS.z,
                    ),
                )
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(
                    WellSelfTest.STAKED_POS.x,
                    WellSelfTest.STAKED_POS.y,
                    WellSelfTest.STAKED_POS.z,
                )
                    ?: error("Stake test section is not loaded")
            val section =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType())
                    ?: error("Stake test section is missing")
            val crews = CrewService(tempDir.resolve("stake-crews.json"))
            val refs = mutableListOf<BlockEntityRef>()
            try {
                val ownerCrew = createStakeOwnerCrew(crews, playerUuid)
                placeTestStakes(world, store, sectionRef, section, positions, playerUuid, refs)
                recomputeStakeRing(world, WellSelfTest.STAKED_POS, well, playerUuid, crews)
                checkStakeLeaning(store, well, refs)
                straightenTestStakes(world, positions, playerUuid, crews)
                checkStakeClaim(well, ownerCrew.id, wellRef, playerUuid, crews, store)
                verifyStakeBeam(world, positions.first(), report)
                assertStakeBreak(world, positions.first(), well)
                report("selftest: PASS Tinker's Hammer claim, foreign-crew denial, and stake break")
            } finally {
                refs.forEach(WellSelfTestExclusions::releaseBlock)
                CraftorySelfTest.clear(world, sectionRef, positions)
                CraftoryCore.wells().remove(world, WellSelfTest.STAKED_POS)
                crews.close()
                Files.deleteIfExists(tempDir.resolve("stake-crews.json"))
            }
        }

        private fun verifyStakeBeam(
            world: World,
            stake: CraftorySelfTest.TestPosition,
            report: WellSelfTestReport,
        ) {
            val beam =
                spawnTetherBeam(
                    world,
                    WellSelfTest.STAKED_POS,
                    BlockPos(stake.x, stake.y, stake.z),
                ) ?: error("verified tether beam asset failed to spawn")
            check(beam.isValid) { "tether beam reference was invalid" }
            world.entityStore.store.removeEntity(beam, RemoveReason.REMOVE)
            report("selftest: PASS tether stake beam spawn and cleanup")
        }

        private fun placeTestStakes(
            world: World,
            store: Store<ChunkStore>,
            sectionRef: Ref<ChunkStore>,
            section: BlockComponentSection,
            positions: List<CraftorySelfTest.TestPosition>,
            playerUuid: UUID,
            refs: MutableList<BlockEntityRef>,
        ) {
            positions.forEach { position ->
                CraftorySelfTest.place(
                    world,
                    store,
                    sectionRef,
                    section,
                    CraftoryIds.TETHER_STAKE,
                    position,
                    TetherStakeState.type,
                )
                val ref =
                    BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                        ?: error("Stake block entity is missing")
                refs.add(ref)
                WellSelfTestExclusions.registerBlock(ref)
                val stake = store.getComponent(ref, TetherStakeState.type) ?: error("Stake state is missing")
                stake.wellId = WellSelfTest.STAKED_POS.pack().toString()
                stake.placerUuid = playerUuid.toString()
            }
        }

        private fun checkStakeLeaning(
            store: Store<ChunkStore>,
            well: Glimmerwell,
            refs: List<BlockEntityRef>,
        ) {
            check(refs.all { store.getComponent(it, TetherStakeState.type)?.leaning == true }) {
                "uneven stake gaps did not mark all stakes leaning"
            }
            check(well.state == WellState.DORMANT) { "leaning stakes tethered the Well" }
        }

        private fun straightenTestStakes(
            world: World,
            positions: List<CraftorySelfTest.TestPosition>,
            playerUuid: UUID,
            crews: CrewService,
        ) {
            positions.forEach { position ->
                check(
                    straightenTetherStake(
                        world,
                        Vector3i(position.x, position.y, position.z),
                        playerUuid,
                        crews,
                    ),
                ) { "Tinker's Hammer path did not clear a leaning stake" }
            }
        }

        private fun checkStakeClaim(
            well: Glimmerwell,
            ownerCrewId: UUID,
            wellRef: Ref<ChunkStore>,
            playerUuid: UUID,
            crews: CrewService,
            store: Store<ChunkStore>,
        ) {
            check(well.state == WellState.TETHERED && well.claimCrewId == ownerCrewId) {
                "straightened stakes did not tether to the placer crew"
            }
            val foreignUuid = UUID.randomUUID()
            val foreignCrew = crews.crewOf(foreignUuid)
            crews.setTier(foreignCrew.id, 2)
            check(JarActions.checkCatch(wellRef, playerUuid, crews, store) is JarCatchResult.Caught) {
                "placer crew could not capture the tethered Well"
            }
            check(JarActions.checkCatch(wellRef, foreignUuid, crews, store) == JarCatchResult.ForeignCrew) {
                "foreign crew was allowed to capture the tethered Well"
            }
        }
    }

    private fun assertStakeBreak(
        world: World,
        stake: CraftorySelfTest.TestPosition,
        well: Glimmerwell,
    ) {
        clearBlock(world, BlockPos(stake.x, stake.y, stake.z))
        check(well.state == WellState.DORMANT) {
            "breaking a tether stake did not return the Well to DORMANT"
        }
    }

    private fun createStakeOwnerCrew(
        crews: CrewService,
        playerUuid: UUID,
    ) = crews.crewOf(playerUuid).also { crews.setTier(it.id, 2) }

    private fun testHurlRefusal(
        world: World,
        report: (String) -> Unit,
    ) {
        val store = world.entityStore.store
        val holder = EntityStore.REGISTRY.newHolder()
        holder.addComponent(JarCarrier.type, JarCarrier())
        holder.ensureComponent(EntityStore.REGISTRY.nonSerializedComponentType)
        holder.ensureComponent(UUIDComponent.getComponentType())
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(Vector3d(0.0, 100.0, 0.0), Rotation3f.IDENTITY),
        )
        val ref = store.addEntity(holder, AddReason.SPAWN) ?: error("Carrier Hurl test entity did not spawn")
        try {
            val result = HurlRides.startArc(world, HurlPoint(0.0, 0.0, 0.0), HurlPoint(1.0, 2.0, 3.0), ref)
            check(result.failure == HurlStartFailure.FORBIDDEN) {
                "Hurl boarding did not refuse a Gob-Jar carrier: $result"
            }
            report("selftest: PASS Hurl refuses Gob-Jar carriers")
        } finally {
            if (ref.isValid) store.removeEntity(ref, RemoveReason.REMOVE)
        }
    }

    private fun clearBlock(
        world: World,
        pos: BlockPos,
    ) {
        val section =
            world.chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z)
                ?: error("test block section is not loaded at $pos")
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                section,
                pos.x,
                pos.y,
                pos.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            ),
        ) { "could not clear test block at $pos" }
    }
}
