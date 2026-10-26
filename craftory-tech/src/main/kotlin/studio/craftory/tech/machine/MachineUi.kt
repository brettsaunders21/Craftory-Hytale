package studio.craftory.tech.machine

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import org.joml.Vector3i
import studio.craftory.core.api.BlockFootprints
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyAccess
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.jank.Jank
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class MachineStatus {
    RUNNING,
    IDLE,
    NO_POWER,
    STALLED,
    STOLEN,
}

data class RattlewheelHud(
    val waterFlow: Int,
    val wind: Boolean,
    val fizzPerSecond: Long,
)

data class ShroomBedHud(
    val growthPercent: Int,
    val stored: Int,
    val twitch: Int,
    val darkness: String,
)

internal data class MemberActivity(
    val inNetwork: Boolean,
    val production: Long,
    val demand: Long,
    val delivered: Long,
)

data class EnergyHudSnapshot(
    val itemId: String?,
    val stored: Long,
    val capacity: Long,
    val gradeRank: Int,
    val members: Int,
    val supply: Long,
    val demand: Long,
    val inNetwork: Boolean,
    val jank: Int? = null,
    val status: MachineStatus? = null,
    val rattlewheel: RattlewheelHud? = null,
    val shroomBed: ShroomBedHud? = null,
    val carrierStability: Int? = null,
)

internal fun machineStatus(
    jank: Jank?,
    rattlewheel: RattlewheelHud?,
    member: MemberActivity?,
    stored: Long,
): MachineStatus =
    when {
        jank?.stolen == true -> MachineStatus.STOLEN
        (jank?.stallTicks ?: 0) > 0 -> MachineStatus.STALLED
        rattlewheel != null && rattlewheel.fizzPerSecond > 0 -> MachineStatus.RUNNING
        member == null || !member.inNetwork -> MachineStatus.IDLE
        member.production > 0 -> MachineStatus.RUNNING
        member.demand > 0 && member.delivered == 0L && stored == 0L -> MachineStatus.NO_POWER
        member.demand > 0 -> MachineStatus.RUNNING
        else -> MachineStatus.IDLE
    }

class EnergyHudState : Component<EntityStore> {
    var lastSnapshot: EnergyHudSnapshot? = null
    var carrierStability: Int? = null
    var carrierStabilityUntilTick: Long = 0

    override fun clone(): Component<EntityStore> =
        EnergyHudState().also {
            it.lastSnapshot = lastSnapshot
            it.carrierStability = carrierStability
            it.carrierStabilityUntilTick = carrierStabilityUntilTick
        }

    companion object {
        lateinit var type: ComponentType<EntityStore, EnergyHudState>
        val CODEC: BuilderCodec<EnergyHudState> =
            BuilderCodec.builder(EnergyHudState::class.java, ::EnergyHudState).build()
    }
}

internal object ScorchOvenWindowTargets {
    private data class Target(
        val x: Int,
        val y: Int,
        val z: Int,
    )

    private val targets = WeakHashMap<World, MutableMap<UUID, LinkedHashSet<Target>>>()

    @Synchronized
    fun update(
        world: World,
        position: Vector3i,
        openPlayers: Set<UUID>,
    ) {
        val target = Target(position.x, position.y, position.z)
        val byPlayer = targets.getOrPut(world) { mutableMapOf() }
        byPlayer.entries.toList().forEach { (playerId, playerTargets) ->
            if (playerId !in openPlayers) {
                playerTargets.remove(target)
                if (playerTargets.isEmpty()) byPlayer.remove(playerId)
            }
        }
        openPlayers.forEach { playerId ->
            val playerTargets = byPlayer.getOrPut(playerId) { linkedSetOf() }
            playerTargets.remove(target)
            playerTargets.add(target)
        }
    }

    @Synchronized
    fun forPlayer(
        world: World,
        playerId: UUID,
        playerPosition: org.joml.Vector3d,
        isOven: (Vector3i) -> Boolean,
    ): Vector3i? {
        val playerTargets = targets[world]?.get(playerId) ?: return null
        val candidate =
            playerTargets.toList().asReversed().firstOrNull { target ->
                val position = Vector3i(target.x, target.y, target.z)
                val dx = playerPosition.x - target.x
                val dy = playerPosition.y - target.y
                val dz = playerPosition.z - target.z
                dx * dx + dy * dy + dz * dz <= 100.0 && isOven(position)
            }
        playerTargets.removeIf { target ->
            val dx = playerPosition.x - target.x
            val dy = playerPosition.y - target.y
            val dz = playerPosition.z - target.z
            dx * dx + dy * dy + dz * dz > 100.0 ||
                !isOven(Vector3i(target.x, target.y, target.z))
        }
        if (playerTargets.isEmpty()) targets[world]?.remove(playerId)
        return candidate?.let { Vector3i(it.x, it.y, it.z) }
    }
}

interface EnergyHudPort {
    fun showOrUpdate(
        playerRef: PlayerRef,
        player: Player,
        snapshot: EnergyHudSnapshot,
    )

    fun hide(
        playerRef: PlayerRef,
        player: Player,
    )
}

private class CustomUiEnergyHudPort : EnergyHudPort {
    override fun showOrUpdate(
        playerRef: PlayerRef,
        player: Player,
        snapshot: EnergyHudSnapshot,
    ) {
        val manager = player.hudManager
        val hud =
            (manager.getCustomHud(CraftoryIds.MACHINE_HUD) as? CraftoryEnergyHud)
                ?: CraftoryEnergyHud(playerRef).also { manager.addCustomHud(playerRef, it) }
        hud.render(snapshot)
    }

    override fun hide(
        playerRef: PlayerRef,
        player: Player,
    ) {
        player.hudManager.removeCustomHud(playerRef, CraftoryIds.MACHINE_HUD)
    }
}

private class CraftoryEnergyHud(
    playerRef: PlayerRef,
) : CustomUIHud(playerRef, CraftoryIds.MACHINE_HUD, 10) {
    private var lastSnapshot: EnergyHudSnapshot? = null

    override fun build(commandBuilder: UICommandBuilder) {
        commandBuilder.append("Craftory/Hud/EnergyHud.ui")
    }

    fun render(snapshot: EnergyHudSnapshot) {
        if (lastSnapshot == snapshot) return
        val commands = UICommandBuilder()
        energyHudCommands(lastSnapshot, snapshot, commands)
        lastSnapshot = snapshot
        update(false, commands)
    }
}

private data class StoredHudValues(
    val stored: Long,
    val capacity: Long,
)

private data class EnergyHudValues(
    val itemId: String?,
    val iconVisible: Boolean,
    val nameKey: String,
    val inNetwork: Boolean,
    val gradeRank: Int,
    val gradeKey: String,
    val gradeColor: String,
    val members: Int,
    val status: MachineStatus?,
    val storedVisible: Boolean,
    val storedText: StoredHudValues,
    val storedProgress: Double,
    val flowVisible: Boolean,
    val supply: Long,
    val demand: Long,
    val jank: Int?,
    val rattlewheel: RattlewheelHud?,
    val shroomBed: ShroomBedHud?,
    val carrierStability: Int?,
    val dividerVisible: Boolean,
)

private fun energyHudValues(snapshot: EnergyHudSnapshot): EnergyHudValues {
    val gradeColor =
        when {
            snapshot.gradeRank == 1 -> "#f2b33d"
            snapshot.gradeRank >= 2 -> "#aa82ff"
            else -> "#80e256"
        }
    val storedVisible = snapshot.capacity > 0
    val flowVisible = snapshot.inNetwork
    val jankVisible = snapshot.jank != null
    val detailVisible = snapshot.rattlewheel != null || snapshot.shroomBed != null
    val jarVisible = snapshot.carrierStability != null
    return EnergyHudValues(
        itemId = snapshot.itemId,
        iconVisible = snapshot.itemId != null,
        nameKey =
            when {
                snapshot.status == null -> "server.craftory.hud.energy.carrier"
                snapshot.itemId == null -> "server.craftory.hud.energy.unknown"
                else -> "server.items.${snapshot.itemId}.name"
            },
        inNetwork = snapshot.inNetwork,
        gradeRank = snapshot.gradeRank,
        gradeKey = GradeTable.DEFAULT.byRank(snapshot.gradeRank)?.id ?: "fizz",
        gradeColor = gradeColor,
        members = snapshot.members,
        status = snapshot.status,
        storedVisible = storedVisible,
        storedText = StoredHudValues(snapshot.stored, snapshot.capacity),
        storedProgress = progress(snapshot.stored, snapshot.capacity),
        flowVisible = flowVisible,
        supply = snapshot.supply,
        demand = snapshot.demand,
        jank = snapshot.jank,
        rattlewheel = snapshot.rattlewheel,
        shroomBed = snapshot.shroomBed,
        carrierStability = snapshot.carrierStability,
        dividerVisible = storedVisible || flowVisible || jankVisible || detailVisible || jarVisible,
    )
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun energyHudCommands(
    previous: EnergyHudSnapshot?,
    next: EnergyHudSnapshot,
    commands: UICommandBuilder,
) {
    val before = previous?.let(::energyHudValues)
    val current = energyHudValues(next)

    fun <T> emitIfChanged(
        previousValue: T?,
        nextValue: T,
        emit: (T) -> Unit,
    ) {
        if (previous == null || previousValue != nextValue) emit(nextValue)
    }

    emitIfChanged(before?.itemId, current.itemId) { itemId ->
        if (itemId == null) commands.setNull("#Icon.ItemId") else commands.set("#Icon.ItemId", itemId)
    }
    emitIfChanged(before?.iconVisible, current.iconVisible) { commands.set("#IconSlot.Visible", it) }
    emitIfChanged(before?.nameKey, current.nameKey) {
        commands.set("#Name.Text", Message.translation(it))
    }
    emitIfChanged(before?.inNetwork, current.inNetwork) { commands.set("#GradeRow.Visible", it) }
    emitIfChanged(before?.gradeRank, current.gradeRank) {
        commands.set("#GradeFizz.Visible", it == 0)
        commands.set("#GradePressurised.Visible", it == 1)
        commands.set("#GradeGlimmer.Visible", it >= 2)
    }
    emitIfChanged(before?.gradeKey, current.gradeKey) {
        commands.set("#Grade.Text", Message.translation("server.craftory.hud.energy.grade.$it"))
    }
    emitIfChanged(before?.gradeColor, current.gradeColor) {
        commands.set("#Grade.Style.TextColor", it)
    }
    emitIfChanged(before?.members, current.members) {
        commands.set(
            "#Members.Text",
            Message.translation("server.craftory.hud.energy.members").param("count", it),
        )
    }

    emitIfChanged(before?.status != null, current.status != null) {
        commands.set("#StatusRow.Visible", it)
    }
    emitIfChanged(before?.status == MachineStatus.RUNNING, current.status == MachineStatus.RUNNING) {
        commands.set("#ChipRunning.Visible", it)
    }
    emitIfChanged(before?.status == MachineStatus.IDLE, current.status == MachineStatus.IDLE) {
        commands.set("#ChipIdle.Visible", it)
    }
    emitIfChanged(before?.status == MachineStatus.NO_POWER, current.status == MachineStatus.NO_POWER) {
        commands.set("#ChipNoPower.Visible", it)
    }
    emitIfChanged(before?.status == MachineStatus.STALLED, current.status == MachineStatus.STALLED) {
        commands.set("#ChipStalled.Visible", it)
    }
    emitIfChanged(before?.status == MachineStatus.STOLEN, current.status == MachineStatus.STOLEN) {
        commands.set("#ChipStolen.Visible", it)
    }

    emitIfChanged(before?.storedVisible, current.storedVisible) { commands.set("#StoredRow.Visible", it) }
    val storedTextChanged =
        previous == null ||
            before!!.storedText != current.storedText ||
            before.storedVisible != current.storedVisible
    if (current.storedVisible && storedTextChanged) {
        commands.set(
            "#StoredValue.Text",
            Message
                .translation("server.craftory.hud.energy.stored-value")
                .param("stored", current.storedText.stored)
                .param("capacity", current.storedText.capacity),
        )
    }
    val storedProgressChanged =
        previous == null ||
            before!!.storedProgress != current.storedProgress ||
            before.storedVisible != current.storedVisible
    if (current.storedVisible && storedProgressChanged) {
        commands.set("#Energy.Value", current.storedProgress)
    }

    emitIfChanged(before?.flowVisible, current.flowVisible) { commands.set("#FlowRow.Visible", it) }
    val supplyChanged =
        previous == null || before!!.supply != current.supply || before.flowVisible != current.flowVisible
    if (current.flowVisible && supplyChanged) {
        commands.set(
            "#In.Text",
            Message.translation("server.craftory.hud.energy.in").param("amount", current.supply),
        )
    }
    val demandChanged =
        previous == null || before!!.demand != current.demand || before.flowVisible != current.flowVisible
    if (current.flowVisible && demandChanged) {
        commands.set(
            "#Out.Text",
            Message.translation("server.craftory.hud.energy.out").param("amount", current.demand),
        )
    }

    emitIfChanged(before?.jank != null, current.jank != null) { commands.set("#JankRow.Visible", it) }
    if (current.jank != null && (previous == null || before!!.jank != current.jank)) {
        commands.set(
            "#JankValue.Text",
            Message.translation("server.craftory.hud.energy.percent").param("value", current.jank),
        )
        commands.set("#JankBar.Value", current.jank / 100f)
    }

    emitIfChanged(before?.rattlewheel != null, current.rattlewheel != null) {
        commands.set("#WaterRow.Visible", it)
        commands.set("#WindRow.Visible", it)
        commands.set("#OutputRow.Visible", it)
    }
    val rattlewheel = current.rattlewheel
    if (rattlewheel != null) {
        val beforeRattlewheel = before?.rattlewheel
        if (previous == null || beforeRattlewheel?.waterFlow != rattlewheel.waterFlow) {
            commands.set(
                "#WaterValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.water")
                    .param("flow", rattlewheel.waterFlow),
            )
        }
        if (previous == null || beforeRattlewheel?.wind != rattlewheel.wind) {
            commands.set(
                "#WindValue.Text",
                Message.translation(
                    if (rattlewheel.wind) {
                        "server.craftory.hud.energy.detail.wind-on"
                    } else {
                        "server.craftory.hud.energy.detail.wind-off"
                    },
                ),
            )
        }
        if (previous == null || beforeRattlewheel?.fizzPerSecond != rattlewheel.fizzPerSecond) {
            commands.set(
                "#OutputValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.output")
                    .param("rate", rattlewheel.fizzPerSecond),
            )
        }
    }

    emitIfChanged(before?.shroomBed != null, current.shroomBed != null) {
        commands.set("#GrowthRow.Visible", it)
        commands.set("#ReadyRow.Visible", it)
        commands.set("#TwitchRow.Visible", it)
        commands.set("#DarkRow.Visible", it)
    }
    val shroomBed = current.shroomBed
    if (shroomBed != null) {
        val beforeShroomBed = before?.shroomBed
        if (previous == null || beforeShroomBed?.growthPercent != shroomBed.growthPercent) {
            commands.set(
                "#GrowthValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.growth")
                    .param("growth", shroomBed.growthPercent),
            )
        }
        if (previous == null || beforeShroomBed?.stored != shroomBed.stored) {
            commands.set(
                "#ReadyValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.ready")
                    .param("count", shroomBed.stored),
            )
        }
        if (previous == null || beforeShroomBed?.twitch != shroomBed.twitch) {
            commands.set(
                "#TwitchValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.twitch")
                    .param("twitch", shroomBed.twitch),
            )
        }
        if (previous == null || beforeShroomBed?.darkness != shroomBed.darkness) {
            commands.set(
                "#DarkValue.Text",
                Message
                    .translation("server.craftory.hud.energy.detail.darkness")
                    .param("source", shroomBed.darkness),
            )
        }
    }

    emitIfChanged(before?.carrierStability != null, current.carrierStability != null) {
        commands.set("#JarRow.Visible", it)
    }
    if (current.carrierStability != null &&
        (previous == null || before!!.carrierStability != current.carrierStability)
    ) {
        commands.set(
            "#JarValue.Text",
            Message
                .translation("server.craftory.hud.energy.percent")
                .param("value", current.carrierStability),
        )
        commands.set("#JarBar.Value", current.carrierStability / 100f)
    }
    emitIfChanged(before?.dividerVisible, current.dividerVisible) { commands.set("#Divider.Visible", it) }
}

internal class JunkPowerHudSystem : DelayedEntitySystem<EntityStore>(0.5f) {
    private val hudPort: EnergyHudPort = CustomUiEnergyHudPort()

    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType(), Player.getComponentType())

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val energy = CraftoryCore.energyAccess(world)
        for (index in 0 until chunk.size()) {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: continue
            val player = chunk.getComponent(index, Player.getComponentType()) ?: continue
            val playerEntity = chunk.getReferenceTo(index)
            val hudState =
                commandBuffer.getComponent(playerEntity, EnergyHudState.type)
                    ?: EnergyHudState().also { commandBuffer.putComponent(playerEntity, EnergyHudState.type, it) }
            val goggles =
                player.inventory
                    ?.armor
                    ?.getItemStack(0.toShort())
                    ?.itemId == CraftoryIds.SQUINT_GOGGLES
            val playerPosition =
                commandBuffer.getComponent(playerEntity, TransformComponent.getComponentType())?.position
            val windowTarget =
                playerPosition?.let { position ->
                    ScorchOvenWindowTargets.forPlayer(world, playerRef.uuid, position) { oven ->
                        val ref = BlockModule.getBlockEntity(world, oven.x, oven.y, oven.z)
                        ref != null && world.chunkStore.store.getComponent(ref, OvenPower.type) != null
                    }
                }
            val target =
                windowTarget
                    ?: if (goggles) {
                        TargetUtil.getTargetBlock(chunk.getReferenceTo(index), 8.0, commandBuffer)
                    } else {
                        null
                    }
            val carrierStability =
                hudState.carrierStability.takeIf { world.tick <= hudState.carrierStabilityUntilTick }
            val machineSnapshot = target?.let { snapshotForTarget(world, it, energy) }
            val snapshot =
                machineSnapshot?.copy(carrierStability = carrierStability.takeIf { goggles })
                    ?: if (goggles) carrierStability?.let(::carrierSnapshot) else null
            if (snapshot == null) {
                hudPort.hide(playerRef, player)
                hudState.lastSnapshot = null
                continue
            }
            if (hudState.lastSnapshot != snapshot) {
                hudState.lastSnapshot = snapshot
                hudPort.showOrUpdate(playerRef, player, snapshot)
            }
        }
    }

    private fun carrierSnapshot(stability: Int) =
        EnergyHudSnapshot(
            itemId = CraftoryIds.GOB_JAR,
            stored = 0,
            capacity = 0,
            gradeRank = 0,
            members = 0,
            supply = 0,
            demand = 0,
            inNetwork = false,
            carrierStability = stability,
        )

    @Suppress("CyclomaticComplexMethod")
    internal fun snapshotForTarget(
        world: World,
        position: Vector3i,
        energy: EnergyAccess,
    ): EnergyHudSnapshot? {
        val origin = BlockFootprints.origin(world.chunkStore.store, position.x, position.y, position.z)
        val id = BlockPos.pack(origin.x, origin.y, origin.z)
        val blockStore = world.chunkStore.store
        val memberRef = BlockModule.getBlockEntity(world, origin.x, origin.y, origin.z)
        val member = memberRef?.let { blockStore.getComponent(it, EnergyMember.type) }
        val jank = memberRef?.let { blockStore.getComponent(it, Jank.type) }
        val ovenPower = memberRef?.let { blockStore.getComponent(it, OvenPower.type) }
        val rattlewheel = memberRef?.let { blockStore.getComponent(it, RattlewheelState.type) }
        val shroomBed = memberRef?.let { blockStore.getComponent(it, ShroomBedState.type) }
        val inNetwork = energy.networkOf(id) >= 0
        if (!inNetwork && jank == null) return null
        val stored = ovenPower?.storedFizz ?: energy.stored(id)
        val capacity = ovenPower?.let { OvenPower.CAPACITY } ?: energy.capacity(id)
        val gradeRank = energy.networkGrade(id)
        val members = energy.networkMembers(id)
        val supply = energy.networkSupply(id)
        val demand = energy.networkDemand(id)
        val memberActivity =
            member?.let {
                MemberActivity(
                    inNetwork = inNetwork,
                    production = it.production,
                    demand = it.demand,
                    delivered = it.delivered,
                )
            }
        return EnergyHudSnapshot(
            itemId = machineItemId(member?.typeId, shroomBed != null, blockItemId(world, origin)),
            stored = stored,
            capacity = capacity,
            gradeRank = gradeRank,
            members = members,
            supply = supply,
            demand = demand,
            inNetwork = inNetwork,
            jank = jank?.value?.roundToInt(),
            status =
                ovenPower?.status ?: machineStatus(
                    jank = jank,
                    rattlewheel =
                        rattlewheel?.let {
                            RattlewheelHud(it.waterFlow, it.windMode, it.fizzPerSecond)
                        },
                    member = memberActivity,
                    stored = stored,
                ),
            rattlewheel =
                rattlewheel?.let {
                    RattlewheelHud(it.waterFlow, it.windMode, it.fizzPerSecond)
                },
            shroomBed =
                shroomBed?.let {
                    ShroomBedHud((it.growth / 10).toInt(), it.stored, it.twitch, it.darknessSource)
                },
        )
    }
}

class CanisterPage private constructor(
    playerRef: PlayerRef,
    private val blockRef: Ref<ChunkStore>,
) : InteractiveCustomUIPage<CanisterPage.Data>(
        playerRef,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
        Data.CODEC,
    ) {
    private var lastSnapshot: EnergyHudSnapshot? = null
    private var lastUpdateTick = Long.MIN_VALUE

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append("Craftory/Pages/Canister.ui")
        commandBuilder.append("#EnergyBar", "Craftory/Widgets/EnergyBar.ui")
        readSnapshot()?.let { render(commandBuilder, it) }
    }

    fun pushLive(
        world: World,
        currentTick: Long,
    ) {
        if (lastUpdateTick != Long.MIN_VALUE && currentTick - lastUpdateTick < 10) return
        if (!blockRef.isValid() || blockRef.store.externalData.world !== world) return
        val snapshot = readSnapshot() ?: return
        if (snapshot == lastSnapshot) return
        val commands = UICommandBuilder()
        render(commands, snapshot)
        sendUpdate(commands, false)
        lastUpdateTick = currentTick
    }

    private fun readSnapshot(): EnergyHudSnapshot? {
        if (!blockRef.isValid()) return null
        val store = blockRef.store
        store.getComponent(blockRef, EnergyMember.type) ?: return null
        val blockState = store.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType()) ?: return null
        val position = Vector3i()
        if (!blockState.fillWorldPos(store, position)) return null
        val energy = CraftoryCore.energyAccess(store.externalData.world)
        val id = BlockPos.pack(position.x, position.y, position.z)
        return EnergyHudSnapshot(
            itemId = CraftoryIds.CANISTER,
            stored = energy.stored(id),
            capacity = energy.capacity(id),
            gradeRank = energy.networkGrade(id),
            members = energy.networkMembers(id),
            supply = energy.networkSupply(id),
            demand = energy.networkDemand(id),
            inNetwork = energy.networkOf(id) >= 0,
        )
    }

    private fun render(
        commands: UICommandBuilder,
        snapshot: EnergyHudSnapshot,
    ) {
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        commands.set("#TitleLabel.Text", Message.translation("server.items.Craftory_Fizz_Canister.name"))
        commands.set("#Energy.Value", progress(snapshot.stored, snapshot.capacity))
        commands.set("#EnergyLabel.Text", "${snapshot.stored} / ${snapshot.capacity} Fizz")
        commands.set("#In.Text", "In: ${snapshot.supply} Fizz/t")
        commands.set("#Out.Text", "Out: ${snapshot.demand} Fizz/t")
        commands.set("#Members.Text", "Network members: ${snapshot.members}")
        commands.set("#Grade.Text", "Grade: ${gradeName(snapshot.gradeRank)}")
    }

    override fun onDismiss(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
    ) {
        activePages.remove(this)
        super.onDismiss(ref, store)
    }

    class Data private constructor() {
        companion object {
            val CODEC: BuilderCodec<Data> = BuilderCodec.builder(Data::class.java, ::Data).build()
        }
    }

    companion object {
        private val activePages = ConcurrentHashMap.newKeySet<CanisterPage>()

        fun tryCreate(
            playerRef: PlayerRef,
            blockRef: Ref<ChunkStore>,
        ): CanisterPage? {
            if (!blockRef.isValid() || blockRef.store.getComponent(blockRef, EnergyMember.type) == null) return null
            return CanisterPage(playerRef, blockRef).also(activePages::add)
        }

        fun tickAll(
            world: World,
            currentTick: Long,
        ) {
            activePages.forEach { it.pushLive(world, currentTick) }
        }
    }
}

internal class CanisterPageRefreshSystem : DelayedEntitySystem<EntityStore>(0.5f) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        CanisterPage.tickAll(store.externalData.world, store.externalData.world.tick)
    }
}

private fun blockItemId(
    world: World,
    position: Vector3i,
): String? {
    val section =
        world.chunkStore
            .getChunkSectionReferenceAtBlock(position.x, position.y, position.z)
            ?.takeIf { it.isValid }
            ?: return null
    val blocks = world.chunkStore.store.getComponent(section, BlockSection.getComponentType()) ?: return null
    return BlockType
        .getAssetMap()
        .getAsset(blocks.get(position.x and 31, position.y and 31, position.z and 31))
        ?.getItem()
        ?.id
}

private fun machineItemId(
    typeId: String?,
    hasShroomBed: Boolean,
    fallback: String?,
): String? =
    when {
        hasShroomBed -> CraftoryIds.SHROOM_BED
        typeId == CraftoryIds.BURNER_TYPE -> CraftoryIds.BURNER
        typeId == CraftoryIds.CANISTER_TYPE -> CraftoryIds.CANISTER
        typeId == CraftoryIds.CRANK_TYPE -> CraftoryIds.CRANK
        typeId == CraftoryIds.GNASHER_TYPE -> CraftoryIds.GNASHER_PIT
        typeId == CraftoryIds.OVEN_TYPE -> CraftoryIds.SCORCH_OVEN
        typeId == CraftoryIds.PIPE_TYPE -> CraftoryIds.PIPE
        typeId == CraftoryIds.RATTLEWHEEL_TYPE -> CraftoryIds.RATTLEWHEEL
        else -> fallback
    }

private fun progress(
    stored: Long,
    capacity: Long,
): Double = if (capacity <= 0) 0.0 else (stored.toDouble() / capacity).coerceIn(0.0, 1.0)

private fun gradeName(rank: Int): String =
    GradeTable.DEFAULT
        .byRank(rank)
        ?.id
        ?.replace('_', ' ')
        ?.replaceFirstChar(Char::uppercase) ?: "Fizz"

internal fun machineName(typeId: String?): String =
    when (typeId) {
        CraftoryIds.BURNER_TYPE -> "Boomshroom Burner"
        CraftoryIds.CANISTER_TYPE -> "Fizz Canister"
        CraftoryIds.CRANK_TYPE -> "Crank"
        CraftoryIds.GNASHER_TYPE -> "Gnasher Pit"
        CraftoryIds.OVEN_TYPE -> "Scorch Oven"
        CraftoryIds.PIPE_TYPE -> "Gutterpipe"
        CraftoryIds.RATTLEWHEEL_TYPE -> "Rattlewheel"
        else -> "Craftory Energy Member"
    }
