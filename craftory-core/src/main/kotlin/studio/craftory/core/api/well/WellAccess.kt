package studio.craftory.core.api.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger

data class WellRef(
    val pos: BlockPos,
    val ref: Ref<ChunkStore>,
    val well: Glimmerwell,
)

@Suppress("TooGenericExceptionCaught")
class WellAccess internal constructor() {
    private val bornListeners = CopyOnWriteArrayList<(WellEvent) -> Unit>()
    private val scannedListeners = CopyOnWriteArrayList<(WellScanEvent) -> Unit>()

    fun wellsNear(
        store: Store<ChunkStore>,
        pos: BlockPos,
        radius: Int,
    ): List<WellRef> {
        require(radius >= 0) { "Well radius must not be negative" }
        val radiusSquared = radius.toLong() * radius
        return store
            .getResource(WellIndex.type)
            .entries()
            .mapNotNull { (packed, ref) ->
                if (!ref.isValid) return@mapNotNull null
                val well = store.getComponent(ref, Glimmerwell.type) ?: return@mapNotNull null
                val wellPos = BlockPos.unpack(packed)
                val dx = wellPos.x.toLong() - pos.x
                val dy = wellPos.y.toLong() - pos.y
                val dz = wellPos.z.toLong() - pos.z
                if (dx * dx + dy * dy + dz * dz > radiusSquared) return@mapNotNull null
                WellRef(wellPos, ref, well)
            }.sortedBy { distanceSquared(it.pos, pos) }
    }

    fun count(store: Store<ChunkStore>): Int = store.getResource(WellIndex.type).size

    fun spawn(
        world: World,
        pos: BlockPos,
        temperament: Temperament,
        strength: WellStrength,
        state: WellState = WellState.WILD,
        taste: String = defaultTaste(temperament),
    ): Ref<ChunkStore>? {
        world.debugAssertInTickingThread()
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z) ?: return null
        val store = world.chunkStore.store
        val section = store.getComponent(sectionRef, BlockComponentSection.getComponentType()) ?: return null
        val blockType = BlockType.fromString(ANCHOR_BLOCK_ID) ?: return null
        val blockIndex = BlockType.getAssetMap().getIndex(blockType.id)
        if (
            !BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                pos.x,
                pos.y,
                pos.z,
                blockIndex,
                blockType,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            )
        ) {
            return null
        }
        val ref =
            BlockEntity.ensureBlockEntity(
                store,
                sectionRef,
                section,
                pos.x and 31,
                pos.y and 31,
                pos.z and 31,
                blockType,
                0,
            ) ?: return null
        val well = Glimmerwell(temperament, strength, state, taste, world.tick)
        store.addComponent(ref, Glimmerwell.type, well)
        val event = WellEvent(world, pos, ref, well)
        bornListeners.forEach { listener ->
            try {
                listener(event)
            } catch (exception: Exception) {
                LOGGER.log(Level.WARNING, "Glimmerwell birth listener failed at $pos", exception)
            }
        }
        return ref
    }

    fun remove(
        world: World,
        pos: BlockPos,
    ): Boolean {
        world.debugAssertInTickingThread()
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z) ?: return false
        val store = world.chunkStore.store
        val removed =
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                pos.x,
                pos.y,
                pos.z,
                BlockType.EMPTY_ID,
                BlockType.EMPTY,
                0,
                0,
                SetBlockSettings.NO_DROP_ITEMS,
            )
        if (removed) store.getResource(WellIndex.type).remove(pos.pack())
        return removed
    }

    fun onWellBorn(listener: (WellEvent) -> Unit): AutoCloseable {
        bornListeners.add(listener)
        return AutoCloseable { bornListeners.remove(listener) }
    }

    fun onWellScanned(listener: (WellScanEvent) -> Unit): AutoCloseable {
        scannedListeners.add(listener)
        return AutoCloseable { scannedListeners.remove(listener) }
    }

    fun fireWellScanned(event: WellScanEvent) {
        scannedListeners.forEach { listener ->
            try {
                listener(event)
            } catch (exception: Exception) {
                LOGGER.log(Level.WARNING, "Glimmerwell scan listener failed at ${event.pos}", exception)
            }
        }
    }

    private fun distanceSquared(
        left: BlockPos,
        right: BlockPos,
    ): Long {
        val dx = left.x.toLong() - right.x
        val dy = left.y.toLong() - right.y
        val dz = left.z.toLong() - right.z
        return dx * dx + dy * dy + dz * dz
    }

    companion object {
        const val ANCHOR_BLOCK_ID = "Craftory_Glimmerwell_Anchor"
        private val LOGGER: Logger = Logger.getLogger(WellAccess::class.java.name)

        fun defaultTaste(temperament: Temperament): String =
            when (temperament) {
                Temperament.SPARK -> "Ingredient_Scraps"
                Temperament.EMBER -> "Plant_Crop_Mushroom_Glowing_Orange"
                Temperament.TIDE -> "Plant_Crop_Mushroom_Glowing_Blue"
                Temperament.GROWTH -> "Plant_Crop_Mushroom_Common_Lime"
                Temperament.STONE -> "Ore_Iron"
                Temperament.GALE -> "Plant_Crop_Mushroom_Flatcap_Blue"
                Temperament.CLANK -> "Ingredient_Scraps"
                Temperament.HOLLOW -> "Plant_Crop_Mushroom_Glowing_Purple"
            }
    }
}
