package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.inventory.container.ItemContainer
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor

internal data class CarrierFixtureArea(
    val chunkRef: Ref<ChunkStore>,
    val sectionRef: Ref<ChunkStore>,
    val origin: BlockPos,
)

private class CarrierAcceptanceReporter(
    private val send: (String) -> Unit,
) : (String) -> Unit {
    private var passed = 0
    private var failed = 0
    private var summarySent = false

    override fun invoke(message: String) {
        when {
            message.startsWith("selftest: PASS") -> passed++
            message.startsWith("selftest: FAIL") -> failed++
        }
        send(message)
    }

    fun summary() {
        if (summarySent) return
        summarySent = true
        send("selftest: carrier $passed passed, $failed failed")
    }
}

private fun loadCarrierTestSection(
    world: World,
    complete: (CarrierFixtureArea?, String?) -> Unit,
) {
    val grid = world.chunkStore
    val chunkIndex = ChunkUtil.indexChunk(CARRIER_TEST_X shr 5, CARRIER_TEST_Z shr 5)
    val sectionCoordinates =
        Triple(CARRIER_TEST_X shr 5, WellSelfTest.CAUGHT_POS.y shr 5, CARRIER_TEST_Z shr 5)
    val chunkLoad = grid.getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING)
    val sectionLoad =
        grid.getChunkSectionReferenceAsync(
            sectionCoordinates.first,
            sectionCoordinates.second,
            sectionCoordinates.third,
            GetChunkFlags.SET_TICKING,
        )
    CompletableFuture
        .allOf(chunkLoad, sectionLoad)
        .orTimeout(10, TimeUnit.SECONDS)
        .whenComplete { _, failure ->
            world.execute {
                val chunkRef = chunkLoad.getNow(null)
                when {
                    failure != null -> {
                        complete(null, "chunk section load failed: ${failure.cause?.message ?: failure.message}")
                    }

                    chunkRef == null || !chunkRef.isValid -> {
                        complete(null, "chunk reference is unavailable")
                    }

                    grid.store.getComponent(chunkRef, WorldChunk.getComponentType()) == null -> {
                        complete(null, "world chunk is unavailable")
                    }

                    else -> {
                        val origin =
                            WellBirths.findSurface(world, CARRIER_TEST_X, CARRIER_TEST_Z, WellSelfTest.CAUGHT_POS.y)
                        if (origin == null) {
                            complete(null, "fixture has no surface")
                        } else {
                            activateCarrierSurfaceSection(world, chunkRef, origin, complete)
                        }
                    }
                }
            }
        }
}

private fun activateCarrierSurfaceSection(
    world: World,
    chunkRef: Ref<ChunkStore>,
    origin: BlockPos,
    complete: (CarrierFixtureArea?, String?) -> Unit,
) {
    val grid = world.chunkStore
    val coordinates = Triple(origin.x shr 5, origin.y shr 5, origin.z shr 5)
    grid
        .getChunkSectionReferenceAsync(
            coordinates.first,
            coordinates.second,
            coordinates.third,
            GetChunkFlags.SET_TICKING,
        ).orTimeout(10, TimeUnit.SECONDS)
        .whenComplete { sectionRef, failure ->
            world.execute {
                val nonTicking = ChunkStore.REGISTRY.getNonTickingComponentType()
                val error =
                    when {
                        failure != null -> {
                            "surface section load failed: ${failure.cause?.message ?: failure.message}"
                        }

                        sectionRef == null || !sectionRef.isValid -> {
                            "surface section reference is unavailable"
                        }

                        grid.store.getComponent(sectionRef, nonTicking) != null -> {
                            "surface section is not ticking"
                        }

                        else -> {
                            null
                        }
                    }
                val area =
                    if (error == null && sectionRef != null) {
                        CarrierFixtureArea(chunkRef, sectionRef, origin)
                    } else {
                        null
                    }
                complete(area, error)
            }
        }
}

internal object CarrierAcceptanceSelfTest {
    internal enum class Phase { PICKUP, DRAIN, REMOVE, BURST }

    internal data class Run(
        val world: World,
        val player: PlayerRef,
        val entity: Ref<EntityStore>,
        val storage: ItemContainer,
        val slot: Short,
        val originalItem: ItemStack?,
        val originalSpeed: Float,
        val fixtureArea: CarrierFixtureArea,
        val origin: BlockPos,
        val report: (String) -> Unit,
        val reportSummary: () -> Unit,
        var lastBurstStatus: String = "not polled",
        var lastBurstReportTick: Long = Long.MIN_VALUE,
        var phase: Phase = Phase.PICKUP,
        var startedTick: Long = world.tick,
        var lastCarrierProcessTick: Long = -1,
        var drainStartStability: Double = 0.0,
        var wellCount: Int = 0,
        var guardianCount: Int = 0,
        var preexistingWellIds: Set<Long> = emptySet(),
        var expectedBurstWellPosition: BlockPos? = null,
        val createdBurstWells: MutableMap<BlockPos, Ref<ChunkStore>> = linkedMapOf(),
        val createdBurstGuardians: MutableMap<BlockPos, Ref<EntityStore>> = linkedMapOf(),
    )

    private var active: Run? = null

    fun start(
        world: World,
        report: (String) -> Unit,
    ) {
        val reporter = CarrierAcceptanceReporter(report)
        if (active != null) {
            reporter("selftest: FAIL carrier live acceptance already has an active run")
            reporter.summary()
            return
        }
        loadCarrierTestSection(world) { fixtureArea, error ->
            if (error != null) {
                reporter("selftest: FAIL carrier live acceptance $error")
                reporter.summary()
                return@loadCarrierTestSection
            }
            val area =
                fixtureArea ?: run {
                    reporter("selftest: FAIL carrier live acceptance fixture area is unavailable")
                    reporter.summary()
                    return@loadCarrierTestSection
                }
            val setup =
                createRun(world, area, reporter, reporter::summary) ?: run {
                    reporter.summary()
                    return@loadCarrierTestSection
                }
            world.chunkStore.store
                .getComponent(area.chunkRef, WorldChunk.getComponentType())
                ?.addKeepLoaded()
            active = setup.first
            val position = setup.second.position
            report(
                "selftest: carrier live acceptance started (tick=${world.tick}, " +
                    "position=${position.x},${position.y},${position.z})",
            )
        }
    }

    private fun createRun(
        world: World,
        fixtureArea: CarrierFixtureArea,
        report: (String) -> Unit,
        reportSummary: () -> Unit,
    ): Pair<Run, TransformComponent>? {
        val origin = fixtureArea.origin
        val player = WellSelfTestPlayer.createCarrierSelfTestPlayer(world, origin)
        val entity = player.reference
        if (entity == null || !entity.isValid) {
            report("selftest: FAIL carrier live acceptance test player reference is unavailable")
            return null
        }
        val store = world.entityStore.store
        val movement = store.getComponent(entity, MovementManager.getComponentType())
        val transform = store.getComponent(entity, TransformComponent.getComponentType())
        if (movement == null || transform == null) {
            store.removeEntity(entity, RemoveReason.REMOVE)
            report("selftest: FAIL carrier live acceptance test player movement or transform is unavailable")
            return null
        }
        if (alreadyCarryingFilledJar(store, entity)) {
            store.removeEntity(entity, RemoveReason.REMOVE)
            report("selftest: FAIL carrier live acceptance player already carries a filled jar")
            return null
        }
        val storage =
            store.getComponent(entity, InventoryComponent.Storage.getComponentType())?.getInventory()
        if (storage == null) {
            store.removeEntity(entity, RemoveReason.REMOVE)
            report("selftest: FAIL carrier live acceptance player storage is unavailable")
            return null
        }
        val slot = firstEmptySlot(storage)
        if (slot == null) {
            store.removeEntity(entity, RemoveReason.REMOVE)
            report("selftest: FAIL carrier live acceptance found no empty inventory slot")
            return null
        }
        val run =
            Run(
                world,
                player,
                entity,
                storage,
                slot,
                storage.getItemStack(slot),
                movement.settings.baseSpeed,
                fixtureArea,
                origin,
                report,
                reportSummary,
            )
        WellSelfTestExclusions.registerEntity(entity)
        if (!insertFilledJar(storage, slot, report)) {
            WellSelfTestExclusions.releaseEntity(entity)
            store.removeEntity(entity, RemoveReason.REMOVE)
            return null
        }
        return run to transform
    }

    private fun insertFilledJar(
        inventory: ItemContainer,
        slot: Short,
        report: (String) -> Unit,
    ): Boolean {
        val transaction = inventory.setItemStackForSlot(slot, testJar(100.0))
        val inserted = transaction.succeeded()
        val insertedJar = inventory.getItemStack(slot)
        if (inserted && insertedJar?.itemId == CraftoryIds.GOB_JAR_FILLED) return true
        report(
            "selftest: FAIL carrier pickup could not insert a filled jar " +
                "(transaction=$transaction, item=${insertedJar?.itemId})",
        )
        return false
    }

    fun tick(
        world: World,
        store: Store<EntityStore>,
    ) {
        val run = active?.takeIf { it.world === world } ?: return
        run.world.chunkStore.store
            .getComponent(run.fixtureArea.sectionRef, ChunkSection.getComponentType())
            ?.resetActiveTimer()
        if (!run.entity.isValid) {
            finish(run, false, "carrier ${run.phase.name.lowercase()} timed out after 60 ticks")
            return
        }
        if (!processCarrierIfDue(run, store, world)) return
        when (run.phase) {
            Phase.PICKUP -> pollPickup(run, store, world.tick)
            Phase.DRAIN -> pollDrain(run, world.tick)
            Phase.REMOVE -> pollRemoval(run, store, world)
            Phase.BURST -> pollBurst(run, store, world.tick)
        }
        val phaseTimeoutTicks = if (run.phase == Phase.DRAIN) CARRIER_DRAIN_TIMEOUT_TICKS else 60L
        if (active === run && world.tick - run.startedTick >= phaseTimeoutTicks) {
            val status =
                when (run.phase) {
                    Phase.BURST -> {
                        " (${run.lastBurstStatus})"
                    }

                    Phase.DRAIN -> {
                        val currentStability =
                            run.storage
                                .getItemStack(run.slot)
                                ?.getFromMetadataOrNull(GobJarMetadata.STABILITY)
                        " (before=${run.drainStartStability}, after=$currentStability)"
                    }

                    else -> {
                        ""
                    }
                }
            finish(run, false, "carrier ${run.phase.name.lowercase()} timed out after $phaseTimeoutTicks ticks$status")
        }
    }

    private fun pollPickup(
        run: Run,
        store: Store<EntityStore>,
        tick: Long,
    ) {
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        val speed = store.getComponent(run.entity, MovementManager.getComponentType())?.settings?.baseSpeed
        if (carrier != null && speed != null && abs(speed - run.originalSpeed * 0.7f) <= 0.01f) {
            run.report("selftest: PASS carrier pickup slows movement and creates component")
            run.drainStartStability =
                run.storage
                    .getItemStack(run.slot)
                    ?.getFromMetadataOrNull(GobJarMetadata.STABILITY) ?: 100.0
            run.phase = Phase.DRAIN
            run.startedTick = tick
        }
    }

    private fun pollDrain(
        run: Run,
        tick: Long,
    ) {
        val item = run.storage.getItemStack(run.slot)
        if (item?.itemId != CraftoryIds.GOB_JAR_FILLED) {
            finish(run, false, "carrier stability drain lost filled jar before 5-second assertion")
            return
        }
        val stability = item.getFromMetadataOrNull(GobJarMetadata.STABILITY) ?: 100.0
        if (tick - run.startedTick < CARRIER_DRAIN_OBSERVATION_TICKS) return
        val decrease = run.drainStartStability - stability
        if (decrease < MINIMUM_CARRIER_DRAIN_OVER_FIVE_SECONDS) {
            finish(
                run,
                false,
                "carrier stability drain was below 0.6 after 5 seconds " +
                    "(before=${run.drainStartStability}, after=$stability, decrease=$decrease)",
            )
            return
        }
        run.report(
            "selftest: PASS carrier jar stability dropped by at least 0.6 in 5 seconds " +
                "(before=${run.drainStartStability}, after=$stability, decrease=$decrease)",
        )
        run.storage.setItemStackForSlot(run.slot, ItemStack.EMPTY)
        run.phase = Phase.REMOVE
        run.startedTick = tick
    }

    private fun pollRemoval(
        run: Run,
        store: Store<EntityStore>,
        world: World,
    ) {
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        val speed = store.getComponent(run.entity, MovementManager.getComponentType())?.settings?.baseSpeed
        val itemGone = run.storage.getItemStack(run.slot)?.itemId != CraftoryIds.GOB_JAR_FILLED
        val movementRestored = carrier == null && speed != null && abs(speed - run.originalSpeed) <= 0.01f
        if (itemGone && movementRestored) {
            run.report("selftest: PASS carrier removal restores movement and component")
            val burstOrigin =
                store.getComponent(run.entity, TransformComponent.getComponentType())?.position?.let {
                    BlockPos(floor(it.x).toInt(), floor(it.y).toInt(), floor(it.z).toInt())
                }
            run.expectedBurstWellPosition =
                burstOrigin?.let { WellBirths.findSurface(world, it.x, it.z, it.y) }
            run.preexistingWellIds =
                run.expectedBurstWellPosition
                    ?.let { expected ->
                        CraftoryCore
                            .wells()
                            .wellsNear(
                                world.chunkStore.store,
                                expected,
                                0,
                            ).map { it.pos.pack() }
                            .toSet()
                    } ?: emptySet()
            run.storage.setItemStackForSlot(run.slot, testJar(0.0))
            run.phase = Phase.BURST
            run.startedTick = world.tick
            run.lastCarrierProcessTick = -1L
        }
    }

    private fun pollBurst(
        run: Run,
        store: Store<EntityStore>,
        tick: Long,
    ) {
        val item = run.storage.getItemStack(run.slot)
        val jarGone = item?.itemId != CraftoryIds.GOB_JAR_FILLED
        val expectedPosition = run.expectedBurstWellPosition
        val well =
            if (jarGone && expectedPosition != null && expectedPosition.pack() !in run.preexistingWellIds) {
                CraftoryCore
                    .wells()
                    .wellsNear(run.world.chunkStore.store, run.origin, 2)
                    .firstOrNull { it.pos == expectedPosition && it.well.state == WellState.WILD }
            } else {
                null
            }
        val guardianId = well?.pos?.pack()?.toString()
        val guardians = store.getResource(WellGuardianIndex.type)
        if (well != null) {
            run.createdBurstWells.putIfAbsent(well.pos, well.ref)
            WellGuardianCalmSystem().trySpawnGuardian(run.world, store, well.pos, well.well, guardians)
            WellSelfTestExclusions.registerWell(well.pos)
        }
        val guardianRef = guardianId?.let { guardians.refsByWellId[it] }
        if (well != null && guardianRef?.isValid == true) {
            run.createdBurstGuardians.putIfAbsent(well.pos, guardianRef)
        }
        val guardian = guardianRef?.isValid == true
        val stability = item?.getFromMetadataOrNull(GobJarMetadata.STABILITY)
        run.lastBurstStatus =
            "jarGone=$jarGone stability=$stability well=${well?.pos} guardianIndexed=$guardian"
        if (tick - run.lastBurstReportTick >= 20L) {
            run.report("selftest: carrier burst poll (${run.lastBurstStatus})")
            run.lastBurstReportTick = tick
        }
        if (jarGone && well != null && guardian) {
            finish(run, true, "forced burst removed jar, spawned WILD Well and indexed guardian")
        }
    }

    internal fun finish(
        run: Run,
        passed: Boolean,
        detail: String,
    ) {
        val store = run.world.entityStore.store
        restoreTestPlayer(run, store)
        val remainingWells = cleanupBurstWells(run, store)
        if (run.entity.isValid) store.removeEntity(run.entity, RemoveReason.REMOVE)
        val playerRemoved = if (run.entity.isValid) 0 else 1
        run.world.chunkStore.store
            .getComponent(run.fixtureArea.chunkRef, WorldChunk.getComponentType())
            ?.removeKeepLoaded()
        val passedWithCleanup = passed && playerRemoved == 1 && remainingWells == 0
        run.report("selftest: ${if (passedWithCleanup) "PASS" else "FAIL"} $detail")
        run.report(
            "selftest: cleanup (carrierWells=${run.wellCount}, carrierGuardians=${run.guardianCount}, " +
                "carrierPlayersCreated=1, carrierPlayersRemoved=$playerRemoved, remainingWells=$remainingWells)",
        )
        run.reportSummary()
        WellSelfTestExclusions.releaseEntity(run.entity)
        active = null
    }

    private fun restoreTestPlayer(
        run: Run,
        store: Store<EntityStore>,
    ) {
        if (!run.entity.isValid) return
        run.storage.setItemStackForSlot(run.slot, run.originalItem ?: ItemStack.EMPTY)
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        if (carrier != null) {
            store.removeComponent(run.entity, JarCarrier.type)
            val position = store.getComponent(run.entity, TransformComponent.getComponentType())?.position
            if (position != null) clearCarrierReadout(run.player, position, store)
        }
        val movement = store.getComponent(run.entity, MovementManager.getComponentType())
        if (movement != null) {
            movement.settings.baseSpeed = run.originalSpeed
            movement.update(run.player.packetHandler)
        }
    }

    private fun cleanupBurstWells(
        run: Run,
        store: Store<EntityStore>,
    ): Int {
        val registry = CraftoryCore.wells()
        val guardians = store.getResource(WellGuardianIndex.type)
        run.createdBurstWells.forEach { (position, wellRef) ->
            val id = position.pack().toString()
            val guardian = run.createdBurstGuardians[position]
            if (guardian != null && guardian.isValid) {
                if (guardians.refsByWellId[id] == guardian) guardians.refsByWellId.remove(id)
                store.removeEntity(guardian, RemoveReason.REMOVE)
                run.guardianCount++
            }
            val currentWell =
                registry
                    .wellsNear(run.world.chunkStore.store, position, 0)
                    .firstOrNull { it.pos == position && it.ref == wellRef }
            if (currentWell != null && registry.remove(run.world, position)) run.wellCount++
        }
        val remaining =
            run.createdBurstWells.count { (position, wellRef) ->
                registry
                    .wellsNear(run.world.chunkStore.store, position, 0)
                    .any { it.pos == position && it.ref == wellRef }
            }
        if (remaining == 0) run.createdBurstWells.keys.forEach(WellSelfTestExclusions::releaseWell)
        return remaining
    }
}

private fun processCarrierIfDue(
    run: CarrierAcceptanceSelfTest.Run,
    store: Store<EntityStore>,
    world: World,
): Boolean {
    val tick = world.tick
    if (tick - run.lastCarrierProcessTick < CARRIER_PROCESS_INTERVAL_TICKS) return true
    val playerRef = store.getComponent(run.entity, PlayerRef.getComponentType())
    val transform = store.getComponent(run.entity, TransformComponent.getComponentType())
    if (playerRef == null || transform == null) {
        CarrierAcceptanceSelfTest.finish(run, false, "carrier ${run.phase.name.lowercase()} lost its player components")
        return false
    }
    val firstProcess = run.lastCarrierProcessTick < 0
    JarCarrierSystem().processPlayer(
        run.entity,
        playerRef,
        transform.position,
        store,
        world,
        tick,
    )
    if (firstProcess) {
        val item = run.storage.getItemStack(run.slot)
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        val speed = store.getComponent(run.entity, MovementManager.getComponentType())?.settings?.baseSpeed
        run.report(
            "selftest: carrier pickup poll " +
                "(item=${item?.itemId}, stability=${item?.getFromMetadataOrNull(GobJarMetadata.STABILITY)}, " +
                "carrier=${carrier != null}, speed=$speed)",
        )
    }
    run.lastCarrierProcessTick = tick
    return true
}

private fun firstEmptySlot(inventory: ItemContainer): Short? =
    (0 until inventory.capacity.toInt())
        .firstOrNull { inventory.getItemStack(it.toShort()) == null }
        ?.toShort()

private fun alreadyCarryingFilledJar(
    store: Store<EntityStore>,
    entity: Ref<EntityStore>,
): Boolean {
    if (store.getComponent(entity, JarCarrier.type) != null) return true
    val inventory = InventoryComponent.getCombined(store, entity, *InventoryComponent.EVERYTHING)
    return (0 until inventory.capacity.toInt()).any {
        inventory.getItemStack(it.toShort())?.itemId == CraftoryIds.GOB_JAR_FILLED
    }
}

private fun testJar(stability: Double): ItemStack =
    JarActions
        .filledJar(
            JarCatchResult.Caught(
                Temperament.SPARK,
                WellStrength.STEADY,
                "Ingredient_Scraps",
                "carrier-acceptance",
            ),
        ).withMetadata(GobJarMetadata.STABILITY, stability)

private const val CARRIER_TEST_X = 10_010
private const val CARRIER_TEST_Z = 10_010
private const val CARRIER_PROCESS_INTERVAL_TICKS = 20L
private const val CARRIER_DRAIN_OBSERVATION_TICKS = 100L
private const val CARRIER_DRAIN_TIMEOUT_TICKS = 120L
private const val MINIMUM_CARRIER_DRAIN_OVER_FIVE_SECONDS = 0.6
