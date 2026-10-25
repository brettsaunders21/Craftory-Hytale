package studio.craftory.tech.machine

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
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
import studio.craftory.core.api.energy.GradeTable
import studio.craftory.tech.CraftoryIds
import java.util.concurrent.ConcurrentHashMap

data class EnergyHudSnapshot(
    val name: String,
    val stored: Long,
    val capacity: Long,
    val grade: String,
    val members: Int,
    val flow: Long,
    val supply: Long,
    val demand: Long,
)

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
                .set("#Flow.Text", "Net flow: ${snapshot.flow} Fizz/t"),
        )
    }
}

internal class JunkPowerHudSystem : EntityTickingSystem<EntityStore>() {
    private val hudPort: EnergyHudPort = CustomUiEnergyHudPort()

    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType(), Player.getComponentType())

    @Suppress("LoopWithTooManyJumpStatements")
    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        if (world.tick % HUD_REFRESH_TICKS != 0L) return
        val energy = CraftoryCore.energyAccess(world)
        for (index in 0 until chunk.size()) {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: continue
            val player = chunk.getComponent(index, Player.getComponentType()) ?: continue
            val armor = player.inventory?.armor
            val goggles = armor?.getItemStack(HEAD_ARMOR_SLOT)?.itemId == CraftoryIds.SQUINT_GOGGLES
            if (!goggles) {
                hudPort.hide(playerRef, player)
                continue
            }
            val target =
                TargetUtil.getTargetBlock(
                    chunk.getReferenceTo(index),
                    HUD_TARGET_DISTANCE,
                    commandBuffer,
                )
            if (target == null) {
                hudPort.hide(playerRef, player)
                continue
            }
            val id = BlockPos.pack(target.x, target.y, target.z)
            if (energy.networkOf(id) < 0) {
                hudPort.hide(playerRef, player)
                continue
            }
            val state = BlockModule.getComponent(JunkPowerState.type, world, target.x, target.y, target.z)
            hudPort.showOrUpdate(
                playerRef,
                player,
                EnergyHudSnapshot(
                    machineName(state?.machine),
                    energy.stored(id),
                    energy.capacity(id),
                    gradeName(energy.networkGrade(id)),
                    energy.networkMembers(id),
                    energy.networkFlow(id),
                    energy.networkSupply(id),
                    energy.networkDemand(id),
                ),
            )
        }
    }

    companion object {
        private const val HUD_REFRESH_TICKS = 10L
        private const val HUD_TARGET_DISTANCE = 8.0
        private const val HEAD_ARMOR_SLOT = 0.toShort()
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
    private var lastUpdateTick = 0L

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append("Craftory/Pages/Canister.ui")
        commandBuilder.append("#EnergyBar", "Craftory/Widgets/EnergyBar.ui")
        readSnapshot()?.let { render(commandBuilder, it, force = true) }
    }

    fun pushLive(
        world: World,
        currentTick: Long,
    ) {
        if (currentTick - lastUpdateTick < UI_REFRESH_TICKS) return
        if (!blockRef.isValid() || blockRef.store.externalData.world !== world) return
        val snapshot = readSnapshot() ?: return
        if (snapshot == lastSnapshot) return
        val commandBuilder = UICommandBuilder()
        render(commandBuilder, snapshot, force = false)
        sendUpdate(commandBuilder, false)
        lastUpdateTick = currentTick
    }

    private fun readSnapshot(): EnergyHudSnapshot? {
        if (!blockRef.isValid()) return null
        val blockStore = blockRef.store
        val blockState = blockStore.getComponent(blockRef, BlockModule.BlockStateInfo.getComponentType()) ?: return null
        val position = Vector3i()
        if (!blockState.fillWorldPos(blockStore, position)) return null
        val state = blockStore.getComponent(blockRef, JunkPowerState.type) ?: return null
        if (state.machine != "Canister") return null
        val world = blockStore.externalData.world
        val id = BlockPos.pack(position.x, position.y, position.z)
        val energy = CraftoryCore.energyAccess(world)
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
        commandBuilder: UICommandBuilder,
        snapshot: EnergyHudSnapshot,
        force: Boolean,
    ) {
        if (!force && snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        commandBuilder.set("#Title.Text", snapshot.name)
        commandBuilder.set("#Energy.Value", progress(snapshot.stored, snapshot.capacity))
        commandBuilder.set("#EnergyLabel.Text", "${snapshot.stored} / ${snapshot.capacity} Fizz")
        commandBuilder.set("#In.Text", "In: ${snapshot.demand} Fizz/t")
        commandBuilder.set("#Out.Text", "Out: ${snapshot.supply} Fizz/t")
        commandBuilder.set("#Members.Text", "Network members: ${snapshot.members}")
        commandBuilder.set("#Grade.Text", "Grade: ${snapshot.grade}")
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
        private const val UI_REFRESH_TICKS = 10L
        private val activePages = ConcurrentHashMap.newKeySet<CanisterPage>()

        fun tryCreate(
            playerRef: PlayerRef,
            blockRef: Ref<ChunkStore>,
        ): CanisterPage? {
            if (!blockRef.isValid()) return null
            val state = blockRef.store.getComponent(blockRef, JunkPowerState.type) ?: return null
            if (state.machine != "Canister") return null
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

private fun machineName(machine: String?): String =
    when (machine) {
        "Burner" -> "Boomshroom Burner"
        "Canister" -> "Fizz Canister"
        "Crank" -> "Crank"
        "GnasherPit" -> "Gnasher Pit"
        "ScorchOven" -> "Scorch Oven"
        "Gutterpipe" -> "Gutterpipe"
        else -> "Craftory Energy Member"
    }
