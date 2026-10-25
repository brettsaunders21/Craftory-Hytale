package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer

object CraftorySelfTest {
    fun execute(context: CommandContext): CompletableFuture<Void> {
        val world = Universe.get().defaultWorld
        if (world == null) {
            context.sendMessage(Message.raw("selftest: FAIL default world is unavailable"))
            return CompletableFuture.completedFuture(null)
        }
        context.sendMessage(Message.raw("selftest: loading isolated test area in ${world.name}"))
        SelfTestRun(context, world).loadArea()
        return CompletableFuture.completedFuture(null)
    }

    @Suppress("TooManyFunctions")
    private class SelfTestRun(
        private val context: CommandContext,
        private val world: World,
    ) {
        private val x = TEST_ORIGIN
        private val y = TEST_Y
        private val z = TEST_ORIGIN
        private val positions =
            listOf(
                BlockPos(x, y, z),
                BlockPos(x + 1, y, z),
                BlockPos(x + 2, y, z),
                BlockPos(x + 3, y, z),
                BlockPos(x + 4, y, z),
                BlockPos(x + 10, y, z),
                BlockPos(x + 20, y, z),
                BlockPos(x + 30, y, z),
            )
        private var passed = 0
        private var failed = 0
        private var startTick = 0L

        fun loadArea() {
            val requests =
                positions.map { position ->
                    world.chunkStore.getChunkSectionReferenceAtBlockAsync(position.x, position.y, position.z)
                }
            CompletableFuture
                .allOf(*requests.toTypedArray())
                .whenComplete { _, error ->
                    world.execute {
                        if (error != null) {
                            report(false, "chunk load", error.cause?.message ?: error.message ?: "unknown error")
                            finish()
                        } else {
                            setup()
                        }
                    }
                }
        }

        private fun setup() {
            try {
                val blockIds =
                    listOf(
                        CraftoryIds.BURNER,
                        CraftoryIds.PIPE,
                        CraftoryIds.CANISTER,
                        CraftoryIds.PIPE,
                        CraftoryIds.SCORCH_OVEN,
                        CraftoryIds.CRANK,
                        CraftoryIds.GNASHER_PIT,
                        CraftoryIds.SCORCH_OVEN,
                    )
                positions.zip(blockIds).forEach { (position, id) -> setBlock(position, id) }
                startTick = world.tick
                waitUntil(startTick + 1) { prepareMachines(20) }
            } catch (error: IllegalStateException) {
                report(false, "setup", error.message ?: error.javaClass.simpleName)
                finish()
            }
        }

        private fun prepareMachines(remainingTicks: Int) {
            val poweredOven = processingBench(positions[4])
            val unpoweredOven = processingBench(positions[7])
            if (poweredOven == null || unpoweredOven == null) {
                if (remainingTicks > 0) {
                    waitUntil(world.tick + 1) { prepareMachines(remainingTicks - 1) }
                    return
                }
                val availableBlockEntities =
                    positions.count { BlockModule.getBlockEntity(world, it.x, it.y, it.z) != null }
                val blockStatus = positions.joinToString { blockStatus(it) }
                report(
                    false,
                    "processing benches",
                    "Scorch Oven block entities did not initialize; " +
                        "found=$availableBlockEntities/8 blocks=[$blockStatus]",
                )
                finish()
                return
            }
            val inputSet =
                poweredOven.inputContainer
                    .setItemStackForSlot(
                        0.toShort(),
                        com.hypixel.hytale.server.core.inventory
                            .ItemStack("Ore_Copper", 1),
                    ).succeeded()
            val unpoweredInputSet =
                unpoweredOven.inputContainer
                    .setItemStackForSlot(
                        0.toShort(),
                        com.hypixel.hytale.server.core.inventory
                            .ItemStack("Ore_Copper", 1),
                    ).succeeded()
            report(inputSet && unpoweredInputSet, "oven inputs", "powered=$inputSet unpowered=$unpoweredInputSet")
            val runtime = runtime()
            val burnerId = positions[0].pack()
            val fuel = runtime.addFuel(burnerId, FUEL_ITEM, world.tick)
            val lit = runtime.kick(burnerId, world.tick)
            report(fuel > 0 && lit, "burner ignition", "fuelTicks=$fuel kicked=$lit")
            val crankOutput = runtime.use(positions[5].pack(), null, world.tick, world.entityStore.store)
            val crankSupply = CraftoryCore.energyAccess(world).networkSupply(positions[5].pack())
            report(crankOutput != null && crankSupply > 0, "crank production starts", "supply=$crankSupply")
            waitUntil(startTick + 40) { checkBurnerAndCanister() }
        }

        private fun checkBurnerAndCanister() {
            val energy = CraftoryCore.energyAccess(world)
            val canisterStored = energy.stored(positions[2].pack())
            val burnerState = interactionState(positions[0])
            report(canisterStored > 0, "burner charges canister", "stored=$canisterStored")
            report(burnerState == "On", "burner visual state", "state=${burnerState ?: "missing"}")
            val fuelCharges =
                processingBench(positions[4])
                    ?.fuelContainer
                    ?.getItemStack(0.toShort())
                    ?.takeIf { it.itemId == CraftoryIds.FIZZ_CHARGE }
                    ?.quantity ?: 0
            report(fuelCharges > 0, "oven receives Fizz Charge", "charges=$fuelCharges")
            waitUntil(world.tick + 45) { checkCrankAndOvens() }
        }

        private fun checkCrankAndOvens() {
            val crankSupply = CraftoryCore.energyAccess(world).networkSupply(positions[5].pack())
            report(crankSupply == 0L, "crank spin expires", "supply=$crankSupply")
            waitUntil(startTick + 125) { checkUnpoweredOven() }
        }

        private fun checkUnpoweredOven() {
            val unpoweredBench = processingBench(positions[7])
            val unpoweredProgress = unpoweredBench?.inputProgress ?: -1f
            report(
                unpoweredProgress == 0f,
                "unpowered oven stays idle after six seconds",
                "progress=$unpoweredProgress",
            )
            if (runtime().chargeInsertionFailed(positions[4].pack())) {
                report(false, "Fizz Charge insertion", "fuel container rejected Craftory_Fizz_Charge")
                finish()
                return
            }
            waitUntil(startTick + 160) { checkOvenAndStartGnasher() }
        }

        private fun checkOvenAndStartGnasher() {
            val bench = processingBench(positions[4])
            val bars =
                bench
                    ?.outputContainer
                    ?.let { container ->
                        (0 until container.capacity)
                            .sumOf { slot ->
                                val stack = container.getItemStack(slot.toShort())
                                if (stack?.itemId == COPPER_BAR) stack.quantity else 0
                            }
                    } ?: 0
            report(bars > 0, "powered oven smelts copper", "copperBars=$bars")
            val runtime = runtime()
            val deposited =
                runtime.depositOre(positions[6].pack(), "Ore_Copper", world.tick) &&
                    runtime.depositOre(positions[6].pack(), "Ore_Copper", world.tick)
            report(deposited, "gnasher accepts copper ore", "two ore queued")
            grindPulse(0)
        }

        private fun grindPulse(index: Int) {
            if (index == GnasherPitMachine.PULSES_PER_ORE * 2) {
                val dropped = gritQuantityNear(positions[6])
                report(dropped == 4, "gnasher drops four grit", "dropped=$dropped")
                val accepted =
                    (1..HOPPER_JAM_COUNT).count {
                        runtime().depositOre(positions[6].pack(), "Ore_Copper", world.tick)
                    }
                val jamMessage =
                    runtime().use(positions[6].pack(), null, world.tick, world.entityStore.store)
                report(accepted == HOPPER_JAM_COUNT, "gnasher accepts ninth ore", "accepted=$accepted")
                report(jamMessage == "Jammed! Kick it.", "jam message", "message=${jamMessage ?: "missing"}")
                val kicked = runtime().kickGnasher(positions[6].pack(), world.tick)
                report(kicked, "kick clears gnasher jam", "cleared=$kicked")
                splitNetwork()
                return
            }
            runtime().use(positions[6].pack(), null, world.tick, world.entityStore.store)
            waitUntil(world.tick + CrankMachine.PULSE_INTERVAL_TICKS) { grindPulse(index + 1) }
        }

        private fun splitNetwork() {
            val pipe = positions[3]
            setBlock(pipe, BlockType.EMPTY)
            waitUntil(world.tick + 4) {
                val energy = CraftoryCore.energyAccess(world)
                val canisterNetwork = energy.networkOf(positions[2].pack())
                val ovenNetwork = energy.networkOf(positions[4].pack())
                report(
                    canisterNetwork >= 0 && ovenNetwork >= 0 && canisterNetwork != ovenNetwork,
                    "pipe removal splits network",
                    "canister=$canisterNetwork oven=$ovenNetwork",
                )
                finish()
            }
        }

        private fun waitUntil(
            targetTick: Long,
            action: () -> Unit,
        ) {
            if (!world.isAlive) {
                report(false, "world ticking", "world stopped before selftest completed")
                finish()
                return
            }
            if (world.tick >= targetTick) {
                action()
                return
            }
            world.scheduleAfter(
                { world.execute { waitUntil(targetTick, action) } },
                50,
                TimeUnit.MILLISECONDS,
            )
        }

        private fun report(
            success: Boolean,
            name: String,
            detail: String,
        ) {
            if (success) passed++ else failed++
            val line = "selftest: ${if (success) "PASS" else "FAIL"} $name ($detail)"
            context.sendMessage(Message.raw(line))
            LOGGER.atInfo().log("%s", line)
        }

        private fun finish() {
            if (positions.isNotEmpty()) {
                positions.forEach { position -> runCatching { setBlock(position, BlockType.EMPTY) } }
                removeDroppedGrit()
            }
            context.sendMessage(Message.raw("selftest: skipped chunk unload/reload (no verified force-unload API)"))
            context.sendMessage(Message.raw("selftest: $passed passed, $failed failed"))
            LOGGER.atInfo().log("selftest: %s passed, %s failed", passed, failed)
        }

        private fun setBlock(
            position: BlockPos,
            id: String,
        ) {
            val blockType = BlockType.fromString(id) ?: error("Unknown block type $id")
            setBlock(position, blockType)
        }

        private fun setBlock(
            position: BlockPos,
            blockType: BlockType,
        ) {
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                    ?: error("Test chunk section is not loaded at $position")
            val id =
                if (blockType == BlockType.EMPTY) {
                    BlockType.EMPTY_ID
                } else {
                    BlockType.getAssetMap().getIndex(blockType.id)
                }
            check(
                BlockOperations.setBlock(
                    world.chunkStore,
                    sectionRef,
                    position.x,
                    position.y,
                    position.z,
                    id,
                    blockType,
                    0,
                    0,
                    if (blockType == BlockType.EMPTY) {
                        SetBlockSettings.NO_DROP_ITEMS
                    } else {
                        SetBlockSettings.PERFORM_BLOCK_UPDATE
                    },
                ),
            ) { "Could not place ${blockType.id} at $position" }
            val blockSection =
                world.chunkStore.store.getComponent(sectionRef, BlockSection.getComponentType())
                    ?: error("Block section disappeared at $position")
            val actualId = blockSection.get(position.x and 31, position.y and 31, position.z and 31)
            check(
                actualId == id,
            ) { "Placed ${blockType.id} at $position but found ${blockStatus(position)}" }
        }

        private fun blockStatus(position: BlockPos): String {
            val sectionRef =
                world.chunkStore.getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
                    ?: return "$position=unloaded"
            val section =
                world.chunkStore.store.getComponent(sectionRef, BlockSection.getComponentType())
                    ?: return "$position=missing-section"
            val id = section.get(position.x and 31, position.y and 31, position.z and 31)
            val blockType = BlockType.getAssetMap().getAsset(id)
            val hasTemplate = blockType?.blockEntity != null
            val hasEntity = BlockModule.getBlockEntity(world, position.x, position.y, position.z) != null
            return "$position=${blockType?.id ?: id}(template=$hasTemplate,entity=$hasEntity)"
        }

        private fun processingBench(position: BlockPos) =
            BlockModule
                .getBlockEntity(world, position.x, position.y, position.z)
                ?.let { blockRef ->
                    world.chunkStore.store.getComponent(
                        blockRef,
                        com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock
                            .getComponentType(),
                    )
                }

        private fun runtime(): JunkPowerWorld = world.chunkStore.store.getResource(JunkPowerWorld.type)

        private fun interactionState(position: BlockPos): String? {
            val blockEntity = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return null
            val store = world.chunkStore.store
            val state = store.getComponent(blockEntity, BlockModule.BlockStateInfo.getComponentType()) ?: return null
            val section = store.getComponent(state.sectionRef, BlockSection.getComponentType()) ?: return null
            val blockId = section.get(position.x and 31, position.y and 31, position.z and 31)
            return BlockType.getAssetMap().getAsset(blockId)?.currentInteractionState
        }

        @Suppress("LoopWithTooManyJumpStatements")
        private fun gritQuantityNear(position: BlockPos): Int {
            var quantity = 0
            val entityStore = world.entityStore.store
            val query = Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType())
            entityStore.forEachChunk(
                query,
                BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, _ ->
                    for (index in 0 until chunk.size()) {
                        val item = chunk.getComponent(index, ItemComponent.getComponentType()) ?: continue
                        val stack = item.itemStack ?: continue
                        val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                        val point = transform.position
                        if (
                            stack.itemId == COPPER_GRIT &&
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
        private fun removeDroppedGrit() {
            val entityStore = world.entityStore.store
            val query = Query.and(ItemComponent.getComponentType(), TransformComponent.getComponentType())
            entityStore.forEachChunk(
                query,
                BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> { chunk, commandBuffer ->
                    for (index in 0 until chunk.size()) {
                        val item = chunk.getComponent(index, ItemComponent.getComponentType()) ?: continue
                        val stack = item.itemStack ?: continue
                        val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
                        val point = transform.position
                        if (isTestDrop(stack.itemId, point)) {
                            commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
                        }
                    }
                },
            )
        }

        private fun isTestDrop(
            itemId: String,
            point: org.joml.Vector3d,
        ): Boolean =
            itemId.startsWith("Craftory_Grit_") &&
                isWithin(point.x, TEST_ORIGIN - 1.0, TEST_ORIGIN + 32.0) &&
                isWithin(point.z, TEST_ORIGIN - 1.0, TEST_ORIGIN + 2.0)

        private fun isWithin(
            value: Double,
            minimum: Double,
            maximum: Double,
        ): Boolean = value >= minimum && value <= maximum
    }

    private const val TEST_ORIGIN = 10_000
    private const val TEST_Y = 150
    private const val FUEL_ITEM = "Plant_Crop_Mushroom_Common_Brown"
    private const val COPPER_BAR = "Ingredient_Bar_Copper"
    private const val COPPER_GRIT = "Craftory_Grit_Copper"
    private const val HOPPER_JAM_COUNT = 9

    private val LOGGER =
        com.hypixel.hytale.logger.HytaleLogger
            .get("CraftorySelfTest")
}
