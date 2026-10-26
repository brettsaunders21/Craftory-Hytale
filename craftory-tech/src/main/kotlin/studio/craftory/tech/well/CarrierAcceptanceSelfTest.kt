package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import kotlin.math.abs
import kotlin.math.floor

internal object CarrierAcceptanceSelfTest {
    private enum class Phase { PICKUP, REMOVE, BURST }

    private data class Run(
        val world: World,
        val player: PlayerRef,
        val entity: Ref<EntityStore>,
        val slot: Short,
        val originalItem: ItemStack?,
        val originalSpeed: Float,
        val origin: BlockPos,
        val report: (String) -> Unit,
        var phase: Phase = Phase.PICKUP,
        var startedTick: Long = world.tick,
        var wellCount: Int = 0,
        var guardianCount: Int = 0,
        var preexistingWellIds: Set<Long> = emptySet(),
    )

    private var active: Run? = null

    fun start(
        world: World,
        player: PlayerRef?,
        report: (String) -> Unit,
    ) {
        if (player == null) {
            report("selftest: FAIL carrier live acceptance requires a player sender")
            return
        }
        val entity = player.reference
        if (entity == null || !entity.isValid) {
            report("selftest: FAIL carrier live acceptance player reference is unavailable")
            return
        }
        val store = world.entityStore.store
        val movement = store.getComponent(entity, MovementManager.getComponentType())
        val transform = store.getComponent(entity, TransformComponent.getComponentType())
        if (movement == null || transform == null) {
            report("selftest: FAIL carrier live acceptance player movement or transform is unavailable")
            return
        }
        val inventory = InventoryComponent.getCombined(store, entity, *InventoryComponent.EVERYTHING)
        if (store.getComponent(entity, JarCarrier.type) != null ||
            (0 until inventory.capacity.toInt()).any {
                inventory.getItemStack(it.toShort())?.itemId == CraftoryIds.GOB_JAR_FILLED
            }
        ) {
            report("selftest: FAIL carrier live acceptance player already carries a filled jar")
            return
        }
        val slot =
            (0 until inventory.capacity.toInt())
                .firstOrNull { inventory.getItemStack(it.toShort()) == null }
                ?.toShort()
        if (slot == null) {
            report("selftest: FAIL carrier live acceptance found no empty inventory slot")
            return
        }
        val old = active
        if (old != null) {
            report("selftest: FAIL carrier live acceptance already has an active run")
            return
        }
        val origin =
            BlockPos(
                floor(transform.position.x).toInt(),
                floor(transform.position.y).toInt(),
                floor(transform.position.z).toInt(),
            )
        val run =
            Run(
                world,
                player,
                entity,
                slot,
                inventory.getItemStack(slot),
                movement.settings.baseSpeed,
                origin,
                report,
            )
        inventory.setItemStackForSlot(slot, testJar(100.0))
        active = run
    }

    fun tick(
        world: World,
        store: Store<EntityStore>,
    ) {
        val run = active?.takeIf { it.world === world } ?: return
        if (!run.entity.isValid) {
            finish(run, false, "carrier ${run.phase.name.lowercase()} timed out after 60 ticks")
            return
        }
        when (run.phase) {
            Phase.PICKUP -> pollPickup(run, store, world.tick)
            Phase.REMOVE -> pollRemoval(run, store, world)
            Phase.BURST -> pollBurst(run, store)
        }
        if (active === run && world.tick - run.startedTick >= 60L) {
            finish(run, false, "carrier ${run.phase.name.lowercase()} timed out after 60 ticks")
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
            InventoryComponent
                .getCombined(store, run.entity, *InventoryComponent.EVERYTHING)
                .setItemStackForSlot(run.slot, ItemStack.EMPTY)
            run.phase = Phase.REMOVE
            run.startedTick = tick
        }
    }

    private fun pollRemoval(
        run: Run,
        store: Store<EntityStore>,
        world: World,
    ) {
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        val speed = store.getComponent(run.entity, MovementManager.getComponentType())?.settings?.baseSpeed
        if (carrier == null && speed != null && abs(speed - run.originalSpeed) <= 0.01f) {
            run.report("selftest: PASS carrier removal restores movement and component")
            run.preexistingWellIds =
                CraftoryCore
                    .wells()
                    .wellsNear(world.chunkStore.store, run.origin, 2)
                    .map { it.pos.pack() }
                    .toSet()
            InventoryComponent
                .getCombined(store, run.entity, *InventoryComponent.EVERYTHING)
                .setItemStackForSlot(run.slot, testJar(0.0))
            run.phase = Phase.BURST
            run.startedTick = world.tick
        }
    }

    private fun pollBurst(
        run: Run,
        store: Store<EntityStore>,
    ) {
        val well =
            CraftoryCore
                .wells()
                .wellsNear(run.world.chunkStore.store, run.origin, 2)
                .firstOrNull { it.well.state == WellState.WILD && it.pos.pack() !in run.preexistingWellIds }
        val item =
            InventoryComponent
                .getCombined(store, run.entity, *InventoryComponent.EVERYTHING)
                .getItemStack(run.slot)
        val jarGone = item?.itemId != CraftoryIds.GOB_JAR_FILLED
        val guardianId = well?.pos?.pack()?.toString()
        val guardian =
            guardianId?.let { store.getResource(WellGuardianIndex.type).refsByWellId[it]?.isValid } == true
        if (jarGone && well != null && guardian) {
            finish(run, true, "forced burst removed jar, spawned WILD Well and indexed guardian")
        }
    }

    private fun testJar(stability: Double): ItemStack =
        JarActions
            .filledJar(
                JarCatchResult.Caught(
                    Temperament.SPARK,
                    WellStrength.FLICKER,
                    "Ingredient_Scraps",
                    "carrier-acceptance",
                ),
            ).withMetadata(GobJarMetadata.STABILITY, stability)

    private fun finish(
        run: Run,
        passed: Boolean,
        detail: String,
    ) {
        val store = run.world.entityStore.store
        if (!run.entity.isValid) {
            active = null
            run.report("selftest: FAIL carrier live acceptance player was removed before cleanup")
            return
        }
        val inventory = InventoryComponent.getCombined(store, run.entity, *InventoryComponent.EVERYTHING)
        inventory.setItemStackForSlot(run.slot, run.originalItem ?: ItemStack.EMPTY)
        val carrier = store.getComponent(run.entity, JarCarrier.type)
        if (carrier != null) {
            store.removeComponent(run.entity, JarCarrier.type)
            val position = store.getComponent(run.entity, TransformComponent.getComponentType())?.position
            if (position != null) clearCarrierReadout(run.player, position, store)
        }
        val movement = store.getComponent(run.entity, MovementManager.getComponentType())
        if (movement != null) {
            movement.settings.baseSpeed = run.originalSpeed
            run.player.packetHandler?.let(movement::update)
        }
        val nearby =
            CraftoryCore
                .wells()
                .wellsNear(run.world.chunkStore.store, run.origin, 2)
                .filter { it.well.state == WellState.WILD && it.pos.pack() !in run.preexistingWellIds }
        nearby.forEach { well ->
            val id = well.pos.pack().toString()
            val guardian = store.getResource(WellGuardianIndex.type).refsByWellId.remove(id)
            if (guardian != null && guardian.isValid) {
                store.removeEntity(guardian, RemoveReason.REMOVE)
                run.guardianCount++
            }
            if (CraftoryCore.wells().remove(run.world, well.pos)) run.wellCount++
        }
        run.report("selftest: ${if (passed) "PASS" else "FAIL"} $detail")
        run.report(
            "selftest: cleanup (carrierWells=${run.wellCount}, carrierGuardians=${run.guardianCount}, " +
                "playerRestored=${run.entity.isValid}, playerRemoved=0)",
        )
        active = null
    }
}
