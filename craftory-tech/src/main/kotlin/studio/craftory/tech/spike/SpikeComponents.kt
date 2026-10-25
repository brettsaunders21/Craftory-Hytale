@file:Suppress("MaxLineLength")

package studio.craftory.tech.spike

import com.hypixel.hytale.builtin.adventure.worldevents.component.EventEndEcsEvent
import com.hypixel.hytale.builtin.adventure.worldevents.component.WorldEventSignal
import com.hypixel.hytale.builtin.mounts.NPCMountComponent
import com.hypixel.hytale.builtin.weather.components.WeatherTracker
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentRegistryProxy
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile
import com.hypixel.hytale.server.core.modules.projectile.event.ProjectileLaunchEvent
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import org.joml.Vector3d
import java.util.UUID

internal class SpikeSlowed : Component<EntityStore> {
    var originalSpeed: Float = 0f
    var heldItemRule: Boolean = false

    override fun clone(): Component<EntityStore> =
        SpikeSlowed().also {
            it.originalSpeed = originalSpeed
            it.heldItemRule = heldItemRule
        }

    companion object {
        lateinit var type: ComponentType<EntityStore, SpikeSlowed>
        val CODEC: BuilderCodec<SpikeSlowed> =
            BuilderCodec
                .builder(SpikeSlowed::class.java, ::SpikeSlowed)
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("OriginalSpeed", com.hypixel.hytale.codec.Codec.FLOAT),
                    { component, value -> component.originalSpeed = value },
                    { component -> component.originalSpeed },
                ).add()
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("HeldItemRule", com.hypixel.hytale.codec.Codec.BOOLEAN),
                    { component, value -> component.heldItemRule = value },
                    { component -> component.heldItemRule },
                ).add()
                .build()
    }
}

internal class SpikeHeldSlow : Component<EntityStore> {
    var itemId: String = ""

    override fun clone(): Component<EntityStore> = SpikeHeldSlow().also { it.itemId = itemId }

    companion object {
        lateinit var type: ComponentType<EntityStore, SpikeHeldSlow>
        val CODEC: BuilderCodec<SpikeHeldSlow> =
            BuilderCodec
                .builder(SpikeHeldSlow::class.java, ::SpikeHeldSlow)
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("ItemId", com.hypixel.hytale.codec.Codec.STRING),
                    { component, value -> component.itemId = value },
                    { component -> component.itemId },
                ).add()
                .build()
    }
}

internal class SpikeHudProbe : Component<EntityStore> {
    var startedAtNanos: Long = 0L
    var updates: Long = 0L
    var totalBuildNanos: Long = 0L

    override fun clone(): Component<EntityStore> =
        SpikeHudProbe().also {
            it.startedAtNanos = startedAtNanos
            it.updates = updates
            it.totalBuildNanos = totalBuildNanos
        }

    companion object {
        lateinit var type: ComponentType<EntityStore, SpikeHudProbe>
        val CODEC: BuilderCodec<SpikeHudProbe> =
            BuilderCodec
                .builder(SpikeHudProbe::class.java, ::SpikeHudProbe)
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("StartedAtNanos", com.hypixel.hytale.codec.Codec.LONG),
                    { component, value -> component.startedAtNanos = value },
                    { component -> component.startedAtNanos },
                ).add()
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("Updates", com.hypixel.hytale.codec.Codec.LONG),
                    { component, value -> component.updates = value },
                    { component -> component.updates },
                ).add()
                .append(
                    com.hypixel.hytale.codec
                        .KeyedCodec("TotalBuildNanos", com.hypixel.hytale.codec.Codec.LONG),
                    { component, value -> component.totalBuildNanos = value },
                    { component -> component.totalBuildNanos },
                ).add()
                .build()
    }
}

internal class SpikeWeatherWatch : Resource<EntityStore> {
    var enabled = false
    val lastWeatherByEnvironment = mutableMapOf<Int, Int>()

    override fun clone(): Resource<EntityStore> =
        SpikeWeatherWatch().also {
            it.enabled = enabled
            it.lastWeatherByEnvironment.putAll(lastWeatherByEnvironment)
        }

    companion object {
        lateinit var type: ResourceType<EntityStore, SpikeWeatherWatch>
    }
}

internal class SpikeProjectileWard : Resource<EntityStore> {
    var enabled = false
    var centerX = 0.0
    var centerY = 0.0
    var centerZ = 0.0
    var radius = 8.0
    var intercepted = 0L

    override fun clone(): Resource<EntityStore> =
        SpikeProjectileWard().also {
            it.enabled = enabled
            it.centerX = centerX
            it.centerY = centerY
            it.centerZ = centerZ
            it.radius = radius
            it.intercepted = intercepted
        }

    companion object {
        lateinit var type: ResourceType<EntityStore, SpikeProjectileWard>
    }
}

internal class SpikeClaimGuard : Resource<EntityStore> {
    var enabled = false
    var centerX = 0.0
    var centerY = 0.0
    var centerZ = 0.0
    var ownerCrewId: UUID? = null

    override fun clone(): Resource<EntityStore> =
        SpikeClaimGuard().also {
            it.enabled = enabled
            it.centerX = centerX
            it.centerY = centerY
            it.centerZ = centerZ
            it.ownerCrewId = ownerCrewId
        }

    companion object {
        lateinit var type: ResourceType<EntityStore, SpikeClaimGuard>
    }
}

internal class SpikeHud(
    playerRef: PlayerRef,
) : CustomUIHud(playerRef, "Craftory_Spike_Hud", 10) {
    override fun build(commandBuilder: UICommandBuilder) {
        commandBuilder.append("Craftory/Hud/SpikeHud.ui")
    }

    fun render(
        tick: Long,
        elapsedMillis: Long,
    ) {
        update(false, UICommandBuilder().set("#Spike.Text", "Spike HUD · tick $tick · ${elapsedMillis}ms"))
    }
}

internal object SpikeComponents {
    fun register(registry: ComponentRegistryProxy<EntityStore>) {
        SpikeSlowed.type = registry.registerComponent(SpikeSlowed::class.java, "CraftorySpikeSlowed", SpikeSlowed.CODEC)
        SpikeHeldSlow.type =
            registry.registerComponent(SpikeHeldSlow::class.java, "CraftorySpikeHeldSlow", SpikeHeldSlow.CODEC)
        SpikeHudProbe.type =
            registry.registerComponent(SpikeHudProbe::class.java, "CraftorySpikeHudProbe", SpikeHudProbe.CODEC)
        SpikeWeatherWatch.type = registry.registerResource(SpikeWeatherWatch::class.java, ::SpikeWeatherWatch)
        SpikeProjectileWard.type = registry.registerResource(SpikeProjectileWard::class.java, ::SpikeProjectileWard)
        SpikeClaimGuard.type = registry.registerResource(SpikeClaimGuard::class.java, ::SpikeClaimGuard)
    }

    fun registerSystems(registry: ComponentRegistryProxy<EntityStore>) {
        registry.registerSystem(SpikeWeatherWatchSystem())
        registry.registerSystem(SpikeHeldSlowSystem())
        registry.registerSystem(SpikeProjectileWardSystem())
        registry.registerSystem(SpikeProjectileLaunchSystem())
        registry.registerSystem(SpikeClaimGuardSystem())
        registry.registerSystem(SpikeWorldEventEndLogSystem())
        registry.registerSystem(SpikeWorldSignalLogSystem())
        registry.registerSystem(SpikeHudSystem())
    }
}

private class SpikeWeatherWatchSystem : TickingSystem<EntityStore>() {
    override fun tick(
        dt: Float,
        systemIndex: Int,
        store: Store<EntityStore>,
    ) {
        val watch = store.getResource(SpikeWeatherWatch.type)
        if (!watch.enabled || store.externalData.world.tick % 20L != 0L) return
        val weather = store.getResource(WeatherResource.getResourceType())
        weather.environmentWeather.int2IntEntrySet().forEach { entry ->
            val env = entry.intKey
            val index = entry.intValue
            val previous = watch.lastWeatherByEnvironment.put(env, index)
            if (previous != null && previous != index) {
                val id =
                    com.hypixel.hytale.server.core.asset.type.weather.config.Weather
                        .getAssetMap()
                        .getAsset(index)
                        ?.id
                        ?: "unknown"
                HytaleLogger.get("CraftorySpike").atInfo().log("weather watch environment=$env index=$index id=$id")
            }
        }
    }
}

private class SpikeHeldSlowSystem : EntityTickingSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(SpikeHeldSlow.type, PlayerRef.getComponentType(), Player.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        if (store.externalData.world.tick % 20L != 0L) return
        val ref = chunk.getReferenceTo(index)
        val held = commandBuffer.getComponent(ref, SpikeHeldSlow.type) ?: return
        val item = InventoryComponent.getItemInHand(commandBuffer, ref)?.itemId
        val slowed = commandBuffer.getComponent(ref, SpikeSlowed.type)
        if (item == held.itemId && slowed == null) {
            applySlow(ref, commandBuffer, heldItemRule = true)
        } else if (item != held.itemId && slowed?.heldItemRule == true) {
            restoreHeldSlow(ref, commandBuffer)
        }
    }
}

internal fun applySlow(
    ref: Ref<EntityStore>,
    accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
    heldItemRule: Boolean = false,
) {
    val movement = accessor.getComponent(ref, MovementManager.getComponentType()) ?: return
    val playerRef = accessor.getComponent(ref, PlayerRef.getComponentType()) ?: return
    val slowed = accessor.addComponent(ref, SpikeSlowed.type)
    slowed.originalSpeed = movement.settings.baseSpeed
    slowed.heldItemRule = heldItemRule
    movement.settings.baseSpeed = slowed.originalSpeed * 0.5f
    movement.update(playerRef.packetHandler)
}

internal fun restoreSpeed(
    ref: Ref<EntityStore>,
    accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
) {
    val playerRef = accessor.getComponent(ref, PlayerRef.getComponentType()) ?: return
    val movement = accessor.getComponent(ref, MovementManager.getComponentType()) ?: return
    movement.resetDefaultsAndUpdate(ref, accessor)
    accessor.removeComponent(ref, SpikeSlowed.type)
    accessor.removeComponent(ref, SpikeHeldSlow.type)
    movement.update(playerRef.packetHandler)
}

private fun restoreHeldSlow(
    ref: Ref<EntityStore>,
    accessor: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
) {
    val movement = accessor.getComponent(ref, MovementManager.getComponentType()) ?: return
    movement.resetDefaultsAndUpdate(ref, accessor)
    accessor.removeComponent(ref, SpikeSlowed.type)
}

private class SpikeProjectileWardSystem : EntityTickingSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(Projectile.getComponentType(), TransformComponent.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val ward = store.getResource(SpikeProjectileWard.type)
        if (!ward.enabled) return
        val position = chunk.getComponent(index, TransformComponent.getComponentType())?.position ?: return
        val dx = position.x - ward.centerX
        val dy = position.y - ward.centerY
        val dz = position.z - ward.centerZ
        if (dx * dx + dy * dy + dz * dz > ward.radius * ward.radius) return
        commandBuffer.removeEntity(chunk.getReferenceTo(index), RemoveReason.REMOVE)
        ward.intercepted++
    }
}

private class SpikeProjectileLaunchSystem :
    EntityEventSystem<EntityStore, ProjectileLaunchEvent>(ProjectileLaunchEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.any()

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: ProjectileLaunchEvent,
    ) {
        HytaleLogger.get("CraftorySpike").atInfo().log("projectile launch observed event=$event")
    }
}

private class SpikeClaimGuardSystem : EntityEventSystem<EntityStore, DamageBlockEvent>(DamageBlockEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: DamageBlockEvent,
    ) {
        val guard = store.getResource(SpikeClaimGuard.type)
        if (!guard.enabled) return
        val position = event.targetBlock
        val dx = position.x + 0.5 - guard.centerX
        val dy = position.y + 0.5 - guard.centerY
        val dz = position.z + 0.5 - guard.centerZ
        if (dx * dx + dy * dy + dz * dz > 64.0) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val crewId =
            studio.craftory.core.api.CraftoryCore
                .crewService()
                .crewOf(player.uuid)
                ?.id
        if (crewId == guard.ownerCrewId) return
        event.isCancelled = true
    }
}

private class SpikeWorldEventEndLogSystem :
    EntityEventSystem<EntityStore, EventEndEcsEvent>(EventEndEcsEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.any()

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: EventEndEcsEvent,
    ) {
        val ended = event.worldEvent
        HytaleLogger.get("CraftorySpike").atInfo().log(
            "world event ended asset=${ended.asset} id=${ended.uuid} reason=${event.reason}",
        )
    }
}

private class SpikeWorldSignalLogSystem :
    EntityEventSystem<EntityStore, WorldEventSignal>(WorldEventSignal::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.any()

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: WorldEventSignal,
    ) {
        HytaleLogger.get("CraftorySpike").atInfo().log(
            "world event signal eventId=${event.eventId()} signalId=${event.signalId()}",
        )
    }
}

private class SpikeHudSystem : EntityTickingSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(SpikeHudProbe.type, PlayerRef.getComponentType(), Player.getComponentType())

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val probe = chunk.getComponent(index, SpikeHudProbe.type) ?: return
        val player = chunk.getComponent(index, Player.getComponentType()) ?: return
        val playerRef = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val start = System.nanoTime()
        val hud = player.hudManager.getCustomHud("Craftory_Spike_Hud") as? SpikeHud ?: return
        hud.render(store.externalData.world.tick, (start - probe.startedAtNanos) / 1_000_000)
        probe.totalBuildNanos += System.nanoTime() - start
        probe.updates++
    }
}
