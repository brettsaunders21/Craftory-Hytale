package studio.craftory.tech.machine

import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.crew.CrewService
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.WellSelfTestExclusions
import studio.craftory.tech.well.WellSelfTestPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal object MachineOwnerSelfTest {
    private val cradlePosition = Position(10_505, 148, 10_000)
    private val resonatorPosition = Position(10_506, 148, 10_000)
    private val fixtures = listOf(CraftoryIds.WELL_CRADLE to cradlePosition, CraftoryIds.RESONATOR to resonatorPosition)
    private val checkNames =
        listOf(
            "Cradle placement assigns its synthetic player",
            "Resonator placement assigns its synthetic player",
            "machine owner may use a placed fixture",
            "foreign synthetic player cannot use a placed fixture",
        )

    fun start(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
    ) {
        context.sendMessage(Message.raw("selftest: starting machine-owner placement check"))
        world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(
                cradlePosition.x,
                cradlePosition.y,
                cradlePosition.z,
                GetChunkFlags.SET_TICKING,
            ).whenComplete { sectionRef, failure ->
                world.execute {
                    run(context, world, sectionRef, failure)
                }
            }
    }

    private fun run(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
        sectionRef: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>?,
        failure: Throwable?,
    ) {
        val store = world.chunkStore.store
        val results = linkedMapOf<String, Boolean>()
        val blockRefs = mutableListOf<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>>()
        val playerRefs = mutableListOf<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>>()
        val players = mutableListOf<com.hypixel.hytale.server.core.universe.PlayerRef>()
        var chunkIndex: Long? = null
        var chunkHeld = false
        var crews: CrewService? = null
        var crewDirectory: Path? = null

        fun check(
            name: String,
            passed: Boolean,
            detail: String,
        ) {
            if (name in results) return
            results[name] = passed
            context.sendMessage(Message.raw("selftest: ${if (passed) "PASS" else "FAIL"} $name ($detail)"))
        }

        fun failUnreportedChecks(detail: String) {
            checkNames.filterNot(results::containsKey).forEach { check(it, false, detail) }
        }

        try {
            if (failure != null) error("owner fixture chunk load failed (${failure.message ?: "unknown error"})")
            val fixtureSectionRef = sectionRef?.takeIf { it.isValid }
                ?: error("owner fixture chunk load returned an invalid section")

            val section =
                store.getComponent(fixtureSectionRef, BlockComponentSection.getComponentType())
                    ?: error("owner fixture block section is missing")
            val keptChunkIndex = ChunkUtil.indexChunk(cradlePosition.x shr 5, cradlePosition.z shr 5)
            chunkIndex = keptChunkIndex
            val chunkRef = world.chunkStore.getChunkReference(keptChunkIndex)?.takeIf { it.isValid }
                ?: error("owner fixture chunk reference is missing")
            val worldChunk = store.getComponent(chunkRef, WorldChunk.getComponentType())
                ?: error("owner fixture WorldChunk component is missing")
            worldChunk.addKeepLoaded()
            chunkHeld = true

            fixtures.forEach { (_, position) ->
                FluidSelfTestSetup.clearBlock(world, position)
            }
            val owner =
                WellSelfTestPlayer.createInWorld(
                    world,
                    BlockPos(cradlePosition.x, cradlePosition.y, cradlePosition.z),
                )
            players += owner
            owner.reference?.let {
                playerRefs += it
                WellSelfTestExclusions.registerEntity(it)
            }
            val foreign =
                WellSelfTestPlayer.createInWorld(
                    world,
                    BlockPos(cradlePosition.x, cradlePosition.y, cradlePosition.z),
                )
            players += foreign
            foreign.reference?.let {
                playerRefs += it
                WellSelfTestExclusions.registerEntity(it)
            }
            val tempCrewDirectory = Files.createTempDirectory("craftory-machine-owner-selftest-")
            crewDirectory = tempCrewDirectory
            crews = CrewService(tempCrewDirectory.resolve("crews.json"))

            fixtures.forEach { (itemId, position) ->
                placeBlock(world, fixtureSectionRef, section, itemId, position)
                val blockRef =
                    BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                        ?.takeIf { it.isValid }
                        ?: error("$itemId block entity is missing")
                blockRefs += blockRef
                WellSelfTestExclusions.registerBlock(blockRef)
            }

            val ownerEntity = owner.reference ?: error("owner synthetic player entity is missing")
            world.entityStore.store.invoke(
                ownerEntity,
                PlaceBlockEvent(ItemStack(CraftoryIds.WELL_CRADLE), Vector3i(cradlePosition.x, cradlePosition.y, cradlePosition.z), RotationTuple.NONE),
            )
            world.entityStore.store.invoke(
                ownerEntity,
                PlaceBlockEvent(ItemStack(CraftoryIds.RESONATOR), Vector3i(resonatorPosition.x, resonatorPosition.y, resonatorPosition.z), RotationTuple.NONE),
            )
            world.execute {
                world.scheduleAfter(
                    {
                        world.execute {
                            verifyAndCleanup(
                                context,
                                world,
                                sectionRef,
                                players,
                                playerRefs,
                                blockRefs,
                                owner.uuid,
                                foreign.uuid,
                                crews,
                                crewDirectory,
                                chunkIndex,
                                chunkHeld,
                                results,
                                ::check,
                                ::failUnreportedChecks,
                            )
                        }
                    },
                    100,
                    TimeUnit.MILLISECONDS,
                )
            }
        } catch (testFailure: Exception) {
            failUnreportedChecks(testFailure.message ?: testFailure.javaClass.simpleName)
            world.scheduleAfter(
                {
                    world.execute {
                        verifyAndCleanup(
                            context,
                            world,
                            sectionRef,
                            players,
                            playerRefs,
                            blockRefs,
                            players.firstOrNull()?.uuid,
                            players.getOrNull(1)?.uuid,
                            crews,
                            crewDirectory,
                            chunkIndex,
                            chunkHeld,
                            results,
                            ::check,
                            ::failUnreportedChecks,
                            testFailure,
                        )
                    }
                },
                100,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun verifyAndCleanup(
        context: com.hypixel.hytale.server.core.command.system.CommandContext,
        world: World,
        sectionRef: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>?,
        players: List<com.hypixel.hytale.server.core.universe.PlayerRef>,
        playerRefs: List<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>>,
        blockRefs: List<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>>,
        ownerUuid: java.util.UUID?,
        foreignUuid: java.util.UUID?,
        crews: CrewService?,
        crewDirectory: Path?,
        chunkIndex: Long?,
        chunkHeld: Boolean,
        results: MutableMap<String, Boolean>,
        check: (String, Boolean, String) -> Unit,
        failUnreportedChecks: (String) -> Unit,
        setupFailure: Exception? = null,
    ) {
        val store = world.chunkStore.store
        var removalFailure: Throwable? = setupFailure
        try {
            if (setupFailure != null) error(setupFailure.message ?: "fixture setup failed")
            val owner = ownerUuid ?: error("owner synthetic player is missing")
            val foreign = foreignUuid ?: error("foreign synthetic player is missing")
            val crew = checkNotNull(crews)
            val ownerAllowed =
                fixtures.all { (_, position) ->
                    machineOwnerAt(world, store, position) == owner &&
                        mayUseMachine(owner, owner, players.firstOrNull(), crew)
                } && owner in crew.crewOf(owner).members
            check("Cradle placement assigns its synthetic player", machineOwnerAt(world, store, cradlePosition) == owner, "owner=$owner")
            check("Resonator placement assigns its synthetic player", machineOwnerAt(world, store, resonatorPosition) == owner, "owner=$owner")
            check("machine owner may use a placed fixture", ownerAllowed, "owner crew allowed=$ownerAllowed")
            val foreignAllowed =
                fixtures.any { (_, position) ->
                    val fixtureOwner = machineOwnerAt(world, store, position)
                    fixtureOwner != null && mayUseMachine(fixtureOwner, foreign, players.getOrNull(1), crew)
                } || owner in crew.crewOf(foreign).members
            check("foreign synthetic player cannot use a placed fixture", !foreignAllowed, "foreign allowed=$foreignAllowed")
        } catch (testFailure: Exception) {
            failUnreportedChecks(testFailure.message ?: testFailure.javaClass.simpleName)
        }

        val initialOwners =
            fixtures.count { (_, position) ->
                machineOwnerAt(world, store, position) != null
            }
        var blocksRemoved = 0
        val ownersRemoved = initialOwners
        var playersRemoved = 0
        playerRefs.forEach { ref ->
            if (ref.isValid) {
                runCatching { store.removeEntity(ref, RemoveReason.REMOVE) }
                    .onFailure { if (removalFailure == null) removalFailure = it }
            }
            if (!ref.isValid) playersRemoved++
            WellSelfTestExclusions.releaseEntity(ref)
        }
        fixtures.forEach { (_, position) ->
            runCatching { FluidSelfTestSetup.clearBlock(world, position) }
                .onSuccess { blocksRemoved++ }
                .onFailure { if (removalFailure == null) removalFailure = it }
        }
        blockRefs.forEach(WellSelfTestExclusions::releaseBlock)

        var keepLoadedReleased = !chunkHeld
        if (chunkHeld && chunkIndex != null) {
            val keptChunk = world.chunkStore.getChunkReference(chunkIndex)
                ?.takeIf { it.isValid }
                ?.let { store.getComponent(it, WorldChunk.getComponentType()) }
            if (keptChunk != null) {
                runCatching {
                    keptChunk.removeKeepLoaded()
                    keepLoadedReleased = true
                }.onFailure { if (removalFailure == null) removalFailure = it }
            }
        }
        runCatching { crews?.close() }
            .onFailure { if (removalFailure == null) removalFailure = it }
        if (crewDirectory != null) {
            runCatching { Files.deleteIfExists(crewDirectory.resolve("crews.json")) }
                .onFailure { if (removalFailure == null) removalFailure = it }
            runCatching { Files.deleteIfExists(crewDirectory) }
                .onFailure { if (removalFailure == null) removalFailure = it }
        }

        val blockSection =
            sectionRef?.takeIf { it.isValid }
                ?.let { store.getComponent(it, BlockSection.getComponentType()) }
        val remainingBlocks =
            fixtures.count { (_, position) ->
                blockSection?.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
            }
        val remainingOwners =
            fixtures.count { (_, position) ->
                BlockModule.getBlockEntity(world, position.x, position.y, position.z)
                    ?.let { store.getComponent(it, MachineOwner.type) != null } == true
            }
        val remainingPlayers = playerRefs.count { it.isValid }
        val remainingExclusions =
            blockRefs.count(WellSelfTestExclusions::isBlock) +
                playerRefs.count(WellSelfTestExclusions::isEntity)
        val remainingKeepLoaded = if (keepLoadedReleased) 0 else 1
        val cleanupPassed =
            removalFailure == null &&
                remainingBlocks == 0 &&
                remainingOwners == 0 &&
                remainingPlayers == 0 &&
                remainingExclusions == 0 &&
                remainingKeepLoaded == 0 &&
                blocksRemoved == fixtures.size &&
                playersRemoved == playerRefs.size
        check(
            "machine-owner fixture cleanup",
            cleanupPassed,
            "blocksRemoved=$blocksRemoved ownersRemoved=$ownersRemoved playersRemoved=$playersRemoved " +
                "remainingBlocks=$remainingBlocks remainingOwners=$remainingOwners remainingPlayers=$remainingPlayers " +
                "remainingExclusions=$remainingExclusions remainingKeepLoaded=$remainingKeepLoaded",
        )
        val passedCount = results.values.count { it }
        val failedCount = results.size - passedCount
        context.sendMessage(
            Message.raw("selftest: machine-owner $passedCount passed, $failedCount failed"),
        )
        context.sendMessage(
            Message.raw(
                "selftest: machine-owner cleanup (blocks=$blocksRemoved, owners=$ownersRemoved, " +
                    "playersCreated=${players.size}, playersRemoved=$playersRemoved, " +
                    "remainingBlocks=$remainingBlocks, remainingOwners=$remainingOwners, " +
                    "remainingPlayers=$remainingPlayers, remainingExclusions=$remainingExclusions, " +
                    "remainingKeepLoaded=$remainingKeepLoaded)",
            ),
        )
    }

    private fun placeBlock(
        world: World,
        sectionRef: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>,
        section: BlockComponentSection,
        blockId: String,
        position: Position,
    ) {
        val type = BlockType.fromString(blockId) ?: error("missing owner test block $blockId")
        check(
            BlockOperations.setBlock(
                world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.getAssetMap().getIndex(type.id),
                type,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            ),
        ) { "failed to place owner test block $blockId at $position" }
        BlockEntity.ensureBlockEntity(
            world.chunkStore.store,
            sectionRef,
            section,
            position.x and 31,
            position.y and 31,
            position.z and 31,
            type,
            0,
        ) ?: error("failed to create owner test block entity for $blockId at $position")
    }

    private fun machineOwnerAt(
        world: World,
        store: com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>,
        position: Position,
    ): java.util.UUID? {
        val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return null
        return store.getComponent(ref, MachineOwner.type)?.placerUuid
    }
}
