package studio.craftory.tech.machine

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.block.BlockEntity
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.crew.CrewService
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.WellSelfTestExclusions
import studio.craftory.tech.well.WellSelfTestPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val CLEANUP_DELAY_MILLIS = 100L
private val machineOwnerFixtures =
    listOf(
        CraftoryIds.WELL_CRADLE to Position(10_505, 148, 10_000),
        CraftoryIds.RESONATOR to Position(10_506, 148, 10_000),
    )
private val machineOwnerChecks =
    listOf(
        "Cradle placement assigns its synthetic player",
        "Resonator placement assigns its synthetic player",
        "machine owner may use a placed fixture",
        "foreign synthetic player cannot use a placed fixture",
    )

internal object MachineOwnerSelfTest {
    fun start(
        context: CommandContext,
        world: World,
        onComplete: () -> Unit = {},
    ) {
        context.sendMessage(Message.raw("selftest: starting machine-owner placement check"))
        world.chunkStore
            .getChunkSectionReferenceAtBlockAsync(
                machineOwnerFixtures.first().second.x,
                machineOwnerFixtures.first().second.y,
                machineOwnerFixtures.first().second.z,
                GetChunkFlags.SET_TICKING,
            ).whenComplete { sectionRef, failure ->
                world.execute {
                    MachineOwnerSelfTestRun(context, world, sectionRef, failure, onComplete).start()
                }
            }
    }
}

internal class MachineOwnerSelfTestRun(
    val context: CommandContext,
    val world: World,
    val sectionRef: Ref<ChunkStore>?,
    val sectionFailure: Throwable?,
    val onComplete: () -> Unit,
) {
    val chunkStore = world.chunkStore.store
    val entityStore = world.entityStore.store
    val results = linkedMapOf<String, Boolean>()
    val blockRefs = mutableListOf<Ref<ChunkStore>>()
    val playerRefs = mutableListOf<Ref<EntityStore>>()
    val players = mutableListOf<PlayerRef>()
    val placedFixtures = mutableListOf<Pair<String, Position>>()
    var owner: PlayerRef? = null
    var foreign: PlayerRef? = null
    var chunkIndex: Long? = null
    var chunkHeld = false
    var crews: CrewService? = null
    var crewDirectory: Path? = null
    var cleanupFailure: Throwable? = null

    fun start() {
        val setupFailure = runCatching { MachineOwnerSelfTestSetup.setup(this) }.exceptionOrNull()
        if (setupFailure != null) {
            failUnreportedChecks(setupFailure.message ?: setupFailure.javaClass.simpleName)
        }
        world.scheduleAfter(
            {
                world.execute {
                    verifyAndCleanup(setupFailure)
                }
            },
            CLEANUP_DELAY_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun verifyAndCleanup(setupFailure: Throwable?) {
        val verificationFailure =
            setupFailure ?: runCatching { MachineOwnerSelfTestVerification.verify(this) }.exceptionOrNull()
        if (verificationFailure != null) {
            failUnreportedChecks(verificationFailure.message ?: verificationFailure.javaClass.simpleName)
        }
        val cleanup = MachineOwnerSelfTestCleanup.cleanup(this)
        check("machine-owner fixture cleanup", cleanup.passed, cleanup.details)
        reportResults(cleanup)
        onComplete()
    }

    fun failUnreportedChecks(detail: String) {
        machineOwnerChecks.filterNot(results::containsKey).forEach { check(it, false, detail) }
    }

    fun check(
        name: String,
        passed: Boolean,
        detail: String,
    ) {
        if (name in results) return
        results[name] = passed
        val result = if (passed) "PASS" else "FAIL"
        context.sendMessage(Message.raw("selftest: $result $name ($detail)"))
    }

    private fun reportResults(cleanup: MachineOwnerCleanupCounts) {
        val passed = results.values.count { it }
        val failed = results.size - passed
        context.sendMessage(Message.raw("selftest: machine-owner $passed passed, $failed failed"))
        context.sendMessage(
            Message.raw(
                "selftest: machine-owner cleanup (blocks=${cleanup.blocksRemoved}, " +
                    "owners=${cleanup.ownersRemoved}, playersCreated=${cleanup.playersCreated}, " +
                    "playersRemoved=${cleanup.playersRemoved}, remainingBlocks=${cleanup.remainingBlocks}, " +
                    "remainingOwners=${cleanup.remainingOwners}, remainingPlayers=${cleanup.remainingPlayers}, " +
                    "remainingExclusions=${cleanup.remainingExclusions}, " +
                    "remainingKeepLoaded=${cleanup.remainingKeepLoaded})",
            ),
        )
    }
}

private object MachineOwnerSelfTestSetup {
    fun setup(run: MachineOwnerSelfTestRun) {
        val (sectionRef, section) = fixtureSection(run)
        val blockSection =
            run.chunkStore.getComponent(sectionRef, BlockSection.getComponentType())
                ?: error("owner fixture block section is missing")
        preflightFixtures(blockSection)
        holdFixtureChunk(run)
        run.owner = createPlayer(run)
        run.foreign = createPlayer(run)
        createCrewService(run)
        placeFixtures(run, sectionRef, section)
        dispatchPlacementEvents(run)
    }

    private fun fixtureSection(run: MachineOwnerSelfTestRun): Pair<Ref<ChunkStore>, BlockComponentSection> {
        if (run.sectionFailure != null) {
            error("owner fixture chunk load failed (${run.sectionFailure.message ?: "unknown error"})")
        }
        val ref =
            run.sectionRef?.takeIf { it.isValid }
                ?: error("owner fixture chunk load returned an invalid section")
        val section =
            run.chunkStore.getComponent(ref, BlockComponentSection.getComponentType())
                ?: error("owner fixture block section is missing")
        return ref to section
    }

    private fun preflightFixtures(section: BlockSection) {
        val occupied =
            machineOwnerFixtures.filter { (_, position) ->
                section.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
            }
        if (occupied.isNotEmpty()) {
            error("position occupied: ${occupied.joinToString { (_, position) -> position.toString() }}")
        }
    }

    private fun holdFixtureChunk(run: MachineOwnerSelfTestRun) {
        val position = machineOwnerFixtures.first().second
        val index = ChunkUtil.indexChunk(position.x shr 5, position.z shr 5)
        run.chunkIndex = index
        val ref =
            run.world.chunkStore
                .getChunkReference(index)
                ?.takeIf { it.isValid }
                ?: error("owner fixture chunk reference is missing")
        val chunk =
            run.chunkStore.getComponent(ref, WorldChunk.getComponentType())
                ?: error("owner fixture WorldChunk component is missing")
        chunk.addKeepLoaded()
        run.chunkHeld = true
    }

    private fun createPlayer(run: MachineOwnerSelfTestRun): PlayerRef {
        val position = machineOwnerFixtures.first().second
        val player = WellSelfTestPlayer.createInWorld(run.world, BlockPos(position.x, position.y, position.z))
        run.players += player
        val ref = player.reference ?: error("synthetic player entity is missing")
        run.playerRefs += ref
        WellSelfTestExclusions.registerEntity(ref)
        return player
    }

    private fun createCrewService(run: MachineOwnerSelfTestRun) {
        val directory = Files.createTempDirectory("craftory-machine-owner-selftest-")
        run.crewDirectory = directory
        run.crews = CrewService(directory.resolve("crews.json"))
    }

    private fun placeFixtures(
        run: MachineOwnerSelfTestRun,
        sectionRef: Ref<ChunkStore>,
        section: BlockComponentSection,
    ) {
        machineOwnerFixtures.forEach { (itemId, position) ->
            placeFixture(run, sectionRef, section, itemId, position)
        }
    }

    private fun placeFixture(
        run: MachineOwnerSelfTestRun,
        sectionRef: Ref<ChunkStore>,
        section: BlockComponentSection,
        itemId: String,
        position: Position,
    ) {
        val type = BlockType.fromString(itemId) ?: error("missing owner test block $itemId")
        val placed =
            BlockOperations.setBlock(
                run.world.chunkStore,
                sectionRef,
                position.x,
                position.y,
                position.z,
                BlockType.getAssetMap().getIndex(type.id),
                type,
                0,
                0,
                SetBlockSettings.NO_UPDATE_STATE,
            )
        if (!placed) error("failed to place owner test block $itemId at $position")
        run.placedFixtures += itemId to position
        BlockEntity.ensureBlockEntity(
            run.world.chunkStore.store,
            sectionRef,
            section,
            position.x and 31,
            position.y and 31,
            position.z and 31,
            type,
            0,
        ) ?: error("failed to create owner test block entity for $itemId at $position")
        val blockRef =
            BlockModule
                .getBlockEntity(run.world, position.x, position.y, position.z)
                ?.takeIf { it.isValid }
                ?: error("$itemId block entity is missing")
        run.blockRefs += blockRef
        WellSelfTestExclusions.registerBlock(blockRef)
    }

    private fun dispatchPlacementEvents(run: MachineOwnerSelfTestRun) {
        val ownerRef = run.owner?.reference ?: error("owner synthetic player entity is missing")
        run.placedFixtures.forEach { (itemId, position) ->
            run.world.entityStore.store.invoke(
                ownerRef,
                PlaceBlockEvent(
                    ItemStack(itemId),
                    Vector3i(position.x, position.y, position.z),
                    RotationTuple.NONE,
                ),
            )
        }
    }
}

private object MachineOwnerSelfTestVerification {
    fun verify(run: MachineOwnerSelfTestRun) {
        val owner = run.owner ?: error("owner synthetic player is missing")
        val foreign = run.foreign ?: error("foreign synthetic player is missing")
        val crews = run.crews ?: error("owner test crew service is missing")
        val ownerUuid = owner.uuid
        val cradleOwner = machineOwnerAt(run.world, run.chunkStore, machineOwnerFixtures[0].second)
        val resonatorOwner = machineOwnerAt(run.world, run.chunkStore, machineOwnerFixtures[1].second)
        run.check(
            machineOwnerChecks[0],
            cradleOwner == ownerUuid,
            "owner=$ownerUuid actual=$cradleOwner",
        )
        run.check(
            machineOwnerChecks[1],
            resonatorOwner == ownerUuid,
            "owner=$ownerUuid actual=$resonatorOwner",
        )
        verifyOwnerUse(run, owner, ownerUuid, crews)
        verifyForeignRejection(run, foreign, ownerUuid, crews)
    }

    private fun verifyOwnerUse(
        run: MachineOwnerSelfTestRun,
        owner: PlayerRef,
        ownerUuid: UUID,
        crews: CrewService,
    ) {
        val ownerAllowed =
            machineOwnerFixtures.all { (_, position) ->
                val fixtureOwner = machineOwnerAt(run.world, run.chunkStore, position) ?: return@all false
                fixtureOwner == ownerUuid && mayUseMachine(fixtureOwner, ownerUuid, owner, crews)
            } && ownerUuid in crews.crewOf(ownerUuid).members
        run.check(machineOwnerChecks[2], ownerAllowed, "owner crew allowed=$ownerAllowed")
    }

    private fun verifyForeignRejection(
        run: MachineOwnerSelfTestRun,
        foreign: PlayerRef,
        ownerUuid: UUID,
        crews: CrewService,
    ) {
        val foreignAllowed =
            machineOwnerFixtures.any { (_, position) ->
                val fixtureOwner = machineOwnerAt(run.world, run.chunkStore, position) ?: return@any false
                mayUseMachine(fixtureOwner, foreign.uuid, foreign, crews)
            } || ownerUuid in crews.crewOf(foreign.uuid).members
        run.check(machineOwnerChecks[3], !foreignAllowed, "foreign allowed=$foreignAllowed")
    }
}

private object MachineOwnerSelfTestCleanup {
    fun cleanup(run: MachineOwnerSelfTestRun): MachineOwnerCleanupCounts {
        val ownersRemoved = countOwners(run)
        val playersRemoved = removePlayers(run)
        val blocksRemoved = removeBlocks(run)
        releaseBlockExclusions(run)
        releaseCrewResources(run)
        val remainingBlocks = countRemainingBlocks(run)
        val remainingOwners = countOwners(run)
        val remainingPlayers = run.playerRefs.count { it.isValid }
        val remainingExclusions = countRemainingExclusions(run)
        val keepLoadedReleased = releaseFixtureChunk(run)
        val remainingKeepLoaded = if (keepLoadedReleased) 0 else 1
        return MachineOwnerCleanupCounts(
            blocksRemoved,
            ownersRemoved,
            run.players.size,
            playersRemoved,
            remainingBlocks,
            remainingOwners,
            remainingPlayers,
            remainingExclusions,
            remainingKeepLoaded,
            run.cleanupFailure == null,
            blocksRemoved == run.placedFixtures.size,
            playersRemoved == run.players.size,
        )
    }

    private fun removePlayers(run: MachineOwnerSelfTestRun): Int {
        var removed = 0
        run.playerRefs.forEach { ref ->
            if (ref.isValid) {
                runCatching { run.entityStore.removeEntity(ref, RemoveReason.REMOVE) }
                    .onFailure { recordFailure(run, it) }
            }
            if (!ref.isValid) removed++
            WellSelfTestExclusions.releaseEntity(ref)
        }
        return removed
    }

    private fun removeBlocks(run: MachineOwnerSelfTestRun): Int {
        val section = currentBlockSection(run)
        var removed = 0
        run.placedFixtures.forEach { (_, position) ->
            val existed = section?.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
            runCatching { FluidSelfTestSetup.clearBlock(run.world, position) }
                .onSuccess { if (existed) removed++ }
                .onFailure { recordFailure(run, it) }
        }
        return removed
    }

    private fun releaseBlockExclusions(run: MachineOwnerSelfTestRun) {
        run.blockRefs.forEach(WellSelfTestExclusions::releaseBlock)
    }

    private fun releaseCrewResources(run: MachineOwnerSelfTestRun) {
        runCatching { run.crews?.close() }.onFailure { recordFailure(run, it) }
        val directory = run.crewDirectory ?: return
        runCatching { Files.deleteIfExists(directory.resolve("crews.json")) }
            .onFailure { recordFailure(run, it) }
        runCatching { Files.deleteIfExists(directory) }.onFailure { recordFailure(run, it) }
    }

    private fun releaseFixtureChunk(run: MachineOwnerSelfTestRun): Boolean {
        if (!run.chunkHeld) return true
        val ref =
            run.chunkIndex
                ?.let(run.world.chunkStore::getChunkReference)
                ?.takeIf { it.isValid }
                ?: return false
        val chunk = run.chunkStore.getComponent(ref, WorldChunk.getComponentType()) ?: return false
        val result = runCatching { chunk.removeKeepLoaded() }
        result.onFailure { recordFailure(run, it) }
        if (result.isFailure) return false
        run.chunkHeld = false
        return true
    }

    private fun countOwners(run: MachineOwnerSelfTestRun): Int =
        run.placedFixtures.count { (_, position) ->
            machineOwnerAt(run.world, run.chunkStore, position) != null
        }

    private fun countRemainingBlocks(run: MachineOwnerSelfTestRun): Int {
        val section = currentBlockSection(run) ?: return run.placedFixtures.size
        return run.placedFixtures.count { (_, position) ->
            section.get(position.x and 31, position.y and 31, position.z and 31) != BlockType.EMPTY_ID
        }
    }

    private fun currentBlockSection(run: MachineOwnerSelfTestRun): BlockSection? =
        run.sectionRef
            ?.takeIf { it.isValid }
            ?.let { run.chunkStore.getComponent(it, BlockSection.getComponentType()) }

    private fun countRemainingExclusions(run: MachineOwnerSelfTestRun): Int =
        run.blockRefs.count(WellSelfTestExclusions::isBlock) +
            run.playerRefs.count(WellSelfTestExclusions::isEntity)

    private fun recordFailure(
        run: MachineOwnerSelfTestRun,
        failure: Throwable,
    ) {
        if (run.cleanupFailure == null) run.cleanupFailure = failure
    }
}

private fun machineOwnerAt(
    world: World,
    store: Store<ChunkStore>,
    position: Position,
): UUID? {
    val ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z) ?: return null
    return store.getComponent(ref, MachineOwner.type)?.placerUuid
}

private data class MachineOwnerCleanupCounts(
    val blocksRemoved: Int,
    val ownersRemoved: Int,
    val playersCreated: Int,
    val playersRemoved: Int,
    val remainingBlocks: Int,
    val remainingOwners: Int,
    val remainingPlayers: Int,
    val remainingExclusions: Int,
    val remainingKeepLoaded: Int,
    val noFailures: Boolean,
    val allPlacedBlocksRemoved: Boolean,
    val allPlayersRemoved: Boolean,
) {
    val passed: Boolean
        get() =
            noFailures &&
                allPlacedBlocksRemoved &&
                allPlayersRemoved &&
                remainingBlocks == 0 &&
                remainingOwners == 0 &&
                remainingPlayers == 0 &&
                remainingExclusions == 0 &&
                remainingKeepLoaded == 0

    val details: String
        get() =
            "blocksRemoved=$blocksRemoved ownersRemoved=$ownersRemoved " +
                "playersRemoved=$playersRemoved remainingBlocks=$remainingBlocks " +
                "remainingOwners=$remainingOwners remainingPlayers=$remainingPlayers " +
                "remainingExclusions=$remainingExclusions remainingKeepLoaded=$remainingKeepLoaded"
}
