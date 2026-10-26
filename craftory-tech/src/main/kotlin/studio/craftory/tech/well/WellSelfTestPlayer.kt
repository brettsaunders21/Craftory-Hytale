package studio.craftory.tech.well

import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.protocol.NetworkChannel
import com.hypixel.hytale.protocol.ToServerPacket
import com.hypixel.hytale.protocol.io.ChannelConnection
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.io.PacketHandler
import com.hypixel.hytale.server.core.io.ProtocolVersion
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.UUID

internal object WellSelfTestPlayer {
    fun createCarrierSelfTestPlayer(
        world: World,
        position: BlockPos,
    ): PlayerRef {
        val playerRef = createInWorldWithoutInteractionManager(world, position)
        val entity = checkNotNull(playerRef.reference) { "Carrier selftest player reference is unavailable" }
        world.entityStore.store.putComponent(entity, HeadRotation.getComponentType(), HeadRotation())
        return playerRef
    }

    fun createInWorldWithoutInteractionManager(
        world: World,
        position: BlockPos,
    ): PlayerRef {
        val playerRef = createInWorld(world, position)
        val entity = checkNotNull(playerRef.reference) { "Selftest player reference is unavailable" }
        world.entityStore.store.removeComponent(
            entity,
            InteractionModule.get().getInteractionManagerComponent(),
        )
        return playerRef
    }

    fun createInWorld(
        world: World,
        position: BlockPos,
    ): PlayerRef {
        val holder = EntityStore.REGISTRY.newHolder()
        val playerRef = createRef(holder, "CraftoryWellSelfTest")
        holder.addComponent(PlayerRef.getComponentType(), playerRef)
        val player = Player()
        player.init(playerRef.uuid, playerRef)
        holder.addComponent(Player.getComponentType(), player)
        holder.addComponent(
            EntityTrackerSystems.EntityViewer.getComponentType(),
            EntityTrackerSystems.EntityViewer(128, playerRef.packetHandler),
        )
        holder.addComponent(
            TransformComponent.getComponentType(),
            TransformComponent(Vector3d(position.x + 0.5, position.y.toDouble(), position.z + 0.5), Rotation3f()),
        )
        holder.addComponent(MovementManager.getComponentType(), createMovementManager())
        holder.addComponent(
            InventoryComponent.Storage.getComponentType(),
            InventoryComponent.Storage(InventoryComponent.DEFAULT_STORAGE_CAPACITY),
        )
        val entity = world.entityStore.store.addEntity(holder, AddReason.SPAWN)
        check(entity != null) { "Well selftest player did not spawn" }
        Player.initGameMode(entity, world.entityStore.store)
        world.entityStore.store
            .getComponent(entity, TransformComponent.getComponentType())
            ?.teleportPosition(Vector3d(position.x + 0.5, position.y.toDouble(), position.z + 0.5))
        return playerRef
    }

    fun createDetached(): PlayerRef = createRef(EntityStore.REGISTRY.newHolder(), "CraftoryForeignSelfTest")

    private fun createRef(
        holder: Holder<EntityStore>,
        username: String,
    ): PlayerRef =
        PlayerRef(
            holder,
            UUID.randomUUID(),
            username,
            "en-US",
            TestPacketHandler(),
            ChunkTracker(),
        )

    private fun createMovementManager(): MovementManager =
        MovementManager().apply {
            setDefaultSettings(
                MovementConfig.DEFAULT_MOVEMENT,
                PhysicsValues.getDefault(),
                GameMode.Adventure,
            )
            applyDefaultSettings()
        }

    private fun testConnection(): ChannelConnection =
        Proxy.newProxyInstance(
            ChannelConnection::class.java.classLoader,
            arrayOf(ChannelConnection::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "isActive", "isWritable" -> {
                    false
                }

                "remoteAddress" -> {
                    InetSocketAddress("127.0.0.1", 0)
                }

                "formatRemoteAddress" -> {
                    "selftest"
                }

                "getSniHostname" -> {
                    ""
                }

                "isFromSameOrigin" -> {
                    false
                }

                "getPacketStatsRecorder" -> {
                    null
                }

                "toString" -> {
                    "CraftorySelfTestChannel"
                }

                "hashCode" -> {
                    System.identityHashCode(proxy)
                }

                "equals" -> {
                    proxy === arguments?.firstOrNull()
                }

                else -> {
                    null
                }
            }
        } as ChannelConnection

    private class TestPacketHandler : PacketHandler(testConnection(), ProtocolVersion(0)) {
        init {
            NetworkChannel.values().forEach { setChannel(it, getChannel()) }
        }

        override fun getIdentifier(): String = "CraftorySelfTest"

        override fun accept(packet: ToServerPacket) = Unit
    }
}
