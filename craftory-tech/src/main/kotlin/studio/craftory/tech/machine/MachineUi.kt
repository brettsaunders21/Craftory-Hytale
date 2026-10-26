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
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import org.joml.Vector3i
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyAccess
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.jank.Jank
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class EnergyHudSnapshot(
    val name: String,
    val stored: Long,
    val capacity: Long,
    val grade: String,
    val members: Int,
    val flow: Long,
    val supply: Long,
    val demand: Long,
    val jank: Int? = null,
    val status: String? = null,
    val carrierStability: Int? = null,
)

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
        commandBuilder.append("#EnergyBar", "Craftory/Widgets/EnergyBar.ui")
    }

    fun render(snapshot: EnergyHudSnapshot) {
        if (lastSnapshot == snapshot) return
        lastSnapshot = snapshot
        update(
            false,
            UICommandBuilder()
                .set("#Title.Text", snapshot.name)
                .set("#Energy.Value", progress(snapshot.stored, snapshot.capacity))
                .set("#EnergyLabel.Text", "${snapshot.stored} / ${snapshot.capacity} Fizz")
                .set("#Grade.Text", "Grade: ${snapshot.grade}")
                .set("#Members.Text", "Members: ${snapshot.members}")
                .set("#Flow.Text", "Net flow: ${snapshot.flow} Fizz/t")
                .set("#Jank.Text", snapshot.jank?.let { "Jank: $it%" }.orEmpty())
                .set("#Status.Text", snapshot.status.orEmpty())
                .set("#JarStability.Text", snapshot.carrierStability?.let { "Gob-Jar stability: $it%" }.orEmpty()),
        )
    }
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
                    ?: commandBuffer.addComponent(playerEntity, EnergyHudState.type)
            val goggles =
                player.inventory
                    ?.armor
                    ?.getItemStack(0.toShort())
                    ?.itemId == CraftoryIds.SQUINT_GOGGLES
            val target =
                if (goggles) {
                    TargetUtil.getTargetBlock(chunk.getReferenceTo(index), 8.0, commandBuffer)
                } else {
                    null
                }
            val carrierStability =
                hudState.carrierStability.takeIf { world.tick <= hudState.carrierStabilityUntilTick }
            val snapshot =
                if (goggles) {
                    val machineSnapshot = target?.let { snapshotForTarget(world, it, energy) }
                    machineSnapshot?.copy(carrierStability = carrierStability)
                        ?: carrierStability?.let(::carrierSnapshot)
                } else {
                    null
                }
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
            "Jar carrier",
            0,
            0,
            "—",
            0,
            0,
            0,
            0,
            carrierStability = stability,
        )

    private fun snapshotForTarget(
        world: World,
        position: Vector3i,
        energy: EnergyAccess,
    ): EnergyHudSnapshot? {
        val id = BlockPos.pack(position.x, position.y, position.z)
        val blockStore = world.chunkStore.store
        val memberRef = BlockModule.getBlockEntity(world, position.x, position.y, position.z)
        val member = memberRef?.let { blockStore.getComponent(it, EnergyMember.type) }
        val jank = memberRef?.let { blockStore.getComponent(it, Jank.type) }
        val rattlewheel = memberRef?.let { blockStore.getComponent(it, RattlewheelState.type) }
        val shroomBed = memberRef?.let { blockStore.getComponent(it, ShroomBedState.type) }
        if (energy.networkOf(id) < 0 && jank == null) return null
        return EnergyHudSnapshot(
            if (shroomBed != null) "Shroom Bed" else machineName(member?.typeId),
            energy.stored(id),
            energy.capacity(id),
            gradeName(energy.networkGrade(id)),
            energy.networkMembers(id),
            energy.networkFlow(id),
            energy.networkSupply(id),
            energy.networkDemand(id),
            jank?.value?.roundToInt(),
            listOfNotNull(machineCondition(jank), machineStatus(rattlewheel, shroomBed))
                .joinToString("\n")
                .ifEmpty { null },
        )
    }

    private fun machineStatus(
        rattlewheel: RattlewheelState?,
        shroomBed: ShroomBedState?,
    ): String? =
        when {
            rattlewheel != null -> {
                "Water flow: ${rattlewheel.waterFlow}  Wind: ${rattlewheel.windMode}\n" +
                    "Output: ${rattlewheel.fizzPerSecond} Fizz/s"
            }

            shroomBed != null -> {
                "Growth: ${shroomBed.growth.toInt()}/1000  Stored: ${shroomBed.stored}\n" +
                    "Twitch: ${shroomBed.twitch}/100  Darkness: ${shroomBed.darknessSource}"
            }

            else -> {
                null
            }
        }

    private fun machineCondition(jank: Jank?): String? =
        when {
            jank?.stolen == true -> "Stolen"
            (jank?.stallTicks ?: 0) > 0 -> "Stalled"
            else -> null
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
            "Fizz Canister",
            energy.stored(id),
            energy.capacity(id),
            gradeName(energy.networkGrade(id)),
            energy.networkMembers(id),
            energy.networkFlow(id),
            energy.networkSupply(id),
            energy.networkDemand(id),
        )
    }

    private fun render(
        commands: UICommandBuilder,
        snapshot: EnergyHudSnapshot,
    ) {
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        commands.set("#Title.Text", snapshot.name)
        commands.set("#Energy.Value", progress(snapshot.stored, snapshot.capacity))
        commands.set("#EnergyLabel.Text", "${snapshot.stored} / ${snapshot.capacity} Fizz")
        commands.set("#In.Text", "In: ${snapshot.supply} Fizz/t")
        commands.set("#Out.Text", "Out: ${snapshot.demand} Fizz/t")
        commands.set("#Members.Text", "Network members: ${snapshot.members}")
        commands.set("#Grade.Text", "Grade: ${snapshot.grade}")
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
