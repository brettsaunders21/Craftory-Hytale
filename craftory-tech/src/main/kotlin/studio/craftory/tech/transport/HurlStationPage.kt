package studio.craftory.tech.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.tech.machine.MachineOwner

internal fun openHurlStation(
    world: World,
    block: Ref<ChunkStore>,
    source: Long,
    rider: Ref<EntityStore>,
    commands: CommandBuffer<EntityStore>,
    rename: Boolean,
): Boolean {
    if (world.chunkStore.store.getComponent(block, HurlStation.type) == null) return false
    val playerRef = commands.getComponent(rider, PlayerRef.getComponentType()) ?: return true
    val player = commands.getComponent(rider, Player.getComponentType()) ?: return true
    val allowed =
        if (rename) {
            mayRename(world, block, playerRef)
        } else {
            StationPermissions.mayUse(world, source, playerRef.uuid)
        }
    if (!allowed) {
        playerRef.sendMessage(Message.translation("server.craftory.wrench.foreign-crew"))
        return true
    }
    player.pageManager.openCustomPage(rider, commands.store, HurlStationPage(playerRef, world, block, source, rename))
    return true
}

private fun mayRename(
    world: World,
    block: Ref<ChunkStore>,
    player: PlayerRef,
): Boolean =
    StationPermissions.mayRename(
        world.chunkStore.store
            .getComponent(block, MachineOwner.type)
            ?.placerUuid,
        player.uuid,
        CraftoryCore.crewService().crewOf(player.uuid).members,
        player.hasPermission("hytale.command.craftory.wrench.any"),
    )

private fun stationRef(
    world: World,
    position: Long,
): Ref<ChunkStore>? {
    val pos = BlockPos.unpack(position)
    return BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z)?.takeIf { it.isValid }
}

class HurlStationPage(
    player: PlayerRef,
    private val world: World,
    private val sourceRef: Ref<ChunkStore>,
    private val source: Long,
    private val rename: Boolean,
) : InteractiveCustomUIPage<HurlStationPage.Data>(
        player,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
        Data.CODEC,
    ) {
    private val destinations = mutableSetOf<Long>()

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        val station = world.chunkStore.store.getComponent(sourceRef, HurlStation.type) ?: return
        commandBuilder
            .append("Craftory/Pages/HurlStation.ui")
            .set("#Title.Text", station.displayName(source))
            .set("#Rename.Visible", rename)
            .set("#Picker.Visible", !rename)
            .set("#Name.Value", station.name)
            .set("#Save.Text", Message.translation("server.craftory.hurl.save"))
        if (rename) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Save",
                EventData.of("Action", "rename").append("@Name", "#Name.Value"),
            )
        } else {
            buildDestinations(commandBuilder, eventBuilder)
        }
    }

    private fun buildDestinations(
        commands: UICommandBuilder,
        events: UIEventBuilder,
    ) {
        destinations.clear()
        CraftoryCore.tubeAccess(world).stationsReachableFrom(source).forEach { destination ->
            val block = stationRef(world, destination) ?: return@forEach
            val station = world.chunkStore.store.getComponent(block, HurlStation.type) ?: return@forEach
            if (mayVisit(block)) {
                val index = destinations.size
                destinations.add(destination)
                commands
                    .append("#Destinations", "Craftory/Pages/HurlDestination.ui")
                    .set("#Destinations[$index] #Go.Text", station.displayName(destination))
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#Destinations[$index] #Go",
                    EventData.of("Action", destination.toString()),
                )
            }
        }
        commands
            .set("#Empty.Visible", destinations.isEmpty())
            .set("#Empty.Text", Message.translation("server.craftory.hurl.no-destinations"))
    }

    private fun mayVisit(block: Ref<ChunkStore>): Boolean =
        StationPermissions.mayVisit(
            world.chunkStore.store
                .getComponent(block, MachineOwner.type)
                ?.placerUuid,
            CraftoryCore.crewService().crewOf(playerRef.uuid).members,
        )

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: Data,
    ) {
        world.execute {
            if (!ref.isValid || !sourceRef.isValid) return@execute
            val station = world.chunkStore.store.getComponent(sourceRef, HurlStation.type) ?: return@execute
            if (rename) {
                renameStation(station, data)
            } else {
                val destination = data.action.toLongOrNull() ?: return@execute
                if (destination !in destinations) return@execute
                val block = stationRef(world, destination) ?: return@execute
                if (!mayVisit(block)) return@execute
                val result =
                    HurlRides.start(world, source, destination, ref, playerRef.uuid) {
                        if (it.failure == null) close()
                    }
                if (result.failure == null) {
                    close()
                } else {
                    playerRef.sendMessage(
                        Message.translation(
                            result.failure.messageKey(),
                        ),
                    )
                }
            }
        }
    }

    private fun renameStation(
        station: HurlStation,
        data: Data,
    ) {
        if (data.action != "rename" || !mayRename(world, sourceRef, playerRef)) return
        station.rename(data.name, sourceRef, world.chunkStore.store)
        close()
    }

    class Data {
        var action = ""
        var name = ""

        companion object {
            val CODEC: BuilderCodec<Data> =
                BuilderCodec
                    .builder(Data::class.java, ::Data)
                    .append(KeyedCodec("Action", Codec.STRING), { data, value -> data.action = value }, { it.action })
                    .add()
                    .append(
                        KeyedCodec("@Name", Codec.STRING),
                        { data, value -> data.name = value },
                        { it.name },
                    ).add()
                    .build()
        }
    }
}

internal fun HurlStartFailure.messageKey(): String =
    when (this) {
        HurlStartFailure.INSUFFICIENT_ENERGY -> "server.craftory.hurl.insufficient-energy"
        HurlStartFailure.ALREADY_RIDING -> "server.craftory.hurl.already-riding"
        HurlStartFailure.FORBIDDEN -> "server.craftory.wrench.foreign-crew"
        HurlStartFailure.SPAWN_FAILED -> "server.craftory.hurl.spawn-failed"
        HurlStartFailure.NO_ROUTE -> "server.craftory.hurl.no-route"
        HurlStartFailure.PENDING -> "server.craftory.hurl.warming-up"
    }
