@file:Suppress("MaxLineLength", "TooManyFunctions", "CyclomaticComplexMethod", "UnusedParameter")

package studio.craftory.tech.spike

import com.hypixel.hytale.builtin.adventure.worldevents.GlobalWorldEventManager
import com.hypixel.hytale.builtin.adventure.worldevents.WorldEventRecord
import com.hypixel.hytale.builtin.adventure.worldevents.action.EventStartAction
import com.hypixel.hytale.builtin.adventure.worldevents.component.WorldEventSignal
import com.hypixel.hytale.builtin.adventure.worldevents.event.WorldEventAsset
import com.hypixel.hytale.builtin.beam.AttachedBeam
import com.hypixel.hytale.builtin.beam.BeamComponent
import com.hypixel.hytale.builtin.beam.asset.Beam
import com.hypixel.hytale.builtin.instances.InstancesPlugin
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition
import com.hypixel.hytale.builtin.mounts.NPCMountComponent
import com.hypixel.hytale.builtin.weather.components.WeatherTracker
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import com.hypixel.hytale.server.npc.NPCPlugin
import org.joml.Vector3d
import studio.craftory.core.api.CraftoryCore
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

object SpikeCommands {
    private val logger = HytaleLogger.get("CraftorySpike")

    fun execute(context: CommandContext): CompletableFuture<Void> {
        val input = context.inputString.substringAfter("spike").trim()
        val args = input.split(Regex("\\s+")).filter(String::isNotBlank)
        if (args.isEmpty()) return usage(context)
        return when (args.first().lowercase()) {
            "lightning" -> {
                withPlayer(context, "lightning") { ref, store -> lightning(context, ref, store) }
            }

            "weather" -> {
                weather(context, args.drop(1))
            }

            "events" -> {
                events(context, args.drop(1))
            }

            "realm" -> {
                realm(context, args.drop(1))
            }

            "slow" -> {
                withPlayer(context, "slow") { ref, store -> slow(context, ref, store, args.drop(1)) }
            }

            "beam" -> {
                if (args.getOrNull(1)?.lowercase() == "list") {
                    val ids =
                        Beam
                            .getAssetMap()
                            .assetMap.keys
                            .sorted()
                    respond(context, "beam", "assets=${ids.size} ${ids.take(40).joinToString()}")
                } else {
                    withPlayer(context, "beam") { ref, store -> beam(context, ref, store, args.drop(1)) }
                }
            }

            "projectile" -> {
                withPlayer(context, "projectile") { ref, store ->
                    projectile(context, ref, store, args.drop(1))
                }
            }

            "claims" -> {
                claims(context, args.drop(1))
            }

            "mount" -> {
                withPlayer(context, "mount") { ref, store -> mount(context, ref, store) }
            }

            "hud" -> {
                withPlayer(context, "hud") { ref, store -> hud(context, ref, store, args.drop(1)) }
            }

            else -> {
                usage(context)
            }
        }
    }

    private fun withPlayer(
        context: CommandContext,
        name: String,
        action: (Ref<EntityStore>, Store<EntityStore>) -> Unit,
    ): CompletableFuture<Void> {
        val ref = context.senderAsPlayerRef()
        if (ref == null) return respond(context, name, "requires a player sender")
        val store = ref.store
        return CompletableFuture.runAsync({ action(ref, store) }, store.externalData.world)
    }

    private fun lightning(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
    ) {
        val block = TargetUtil.getTargetBlock(ref, 32.0, store)
        if (block == null) return respondNow(context, "lightning", "look at a block within 32 blocks")
        val particle =
            ParticleSystem.getAssetMap().assetMap.keys.firstOrNull {
                matches(it, "lightning", "thunder", "spark", "electric")
            }
        val sound =
            SoundEvent
                .getAssetMap()
                .assetMap.keys
                .firstOrNull { matches(it, "thunder", "zap", "electric") }
        val position = Vector3d(block.x + 0.5, block.y + 1.0, block.z + 0.5)
        if (particle != null) ParticleUtil.spawnParticleEffect(particle, position, store)
        if (sound != null) {
            val soundIndex = SoundEvent.getAssetMap().getIndex(sound)
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, store)
        }
        val player = store.getComponent(ref, PlayerRef.getComponentType())
        val tracker = store.getComponent(ref, WeatherTracker.getComponentType())
        val weatherId = tracker?.weatherIndex?.let { Weather.getAssetMap().getAsset(it)?.id } ?: "unknown"
        val storm = weatherId.contains("Thunder", true) || weatherId.contains("Storm", true)
        respondNow(
            context,
            "lightning",
            "0.7.4 has no server lightning API; particle=${particle ?: "none"} sound=${sound ?: "none"} " +
                "weather=$weatherId storm=$storm player=${player?.uuid ?: "unknown"}",
        )
    }

    @Suppress("LongMethod")
    private fun weather(
        context: CommandContext,
        args: List<String>,
    ): CompletableFuture<Void> {
        val ref = context.senderAsPlayerRef()
        if (args.firstOrNull()?.lowercase() == "assets") {
            val ids =
                Weather
                    .getAssetMap()
                    .assetMap.keys
                    .sorted()
            return respond(context, "weather", "assets=${ids.size} [${ids.take(40).joinToString()}]")
        }
        if (args.isEmpty()) {
            val playerRef = ref ?: return respond(context, "weather", "requires a player to inspect world weather")
            return withWorld(context, playerRef) { store ->
                val weather = store.getResource(WeatherResource.getResourceType())
                val tracker = store.getComponent(playerRef, WeatherTracker.getComponentType())
                val envId = tracker?.environmentId
                val environmentIndex =
                    if (envId != null) weather.getWeatherIndexForEnvironment(envId) else -1
                val effectiveIndex = if (envId != null) weather.getEffectiveWeatherIndex(envId) else -1
                respondNow(
                    context,
                    "weather",
                    "forced=${weather.forcedWeatherIndex.let(::weatherId)} " +
                        "environment=${envId?.let { "$it:${weatherId(environmentIndex)}" } ?: "unavailable"} " +
                        "tracker=${tracker?.weatherIndex?.let(::weatherId) ?: "unavailable"} " +
                        "effective=${weatherId(effectiveIndex)} assets=${Weather.getAssetMap().assetCount}",
                )
            }
        }
        return when (args[0].lowercase()) {
            "local" -> {
                val playerRef = ref ?: return respond(context, "weather", "requires a player for local override")
                val id = args.getOrNull(1) ?: return respond(context, "weather", "usage: weather local <WeatherId>")
                withWorld(context, playerRef) { worldStore ->
                    val index = Weather.getAssetMap().getIndex(id)
                    val tracker = worldStore.getComponent(playerRef, WeatherTracker.getComponentType())
                    if (index < 0 || tracker == null) {
                        respondNow(context, "weather", "unknown weather or tracker unavailable: $id")
                    } else {
                        val claimed = tracker.tryClaimOverride(playerRef, index)
                        respondNow(context, "weather", "local override $id claimed=$claimed")
                    }
                }
            }

            "clear" -> {
                val playerRef = ref ?: return respond(context, "weather", "requires a player to clear override")
                withWorld(context, playerRef) { worldStore ->
                    val tracker = worldStore.getComponent(playerRef, WeatherTracker.getComponentType())
                    tracker?.releaseOverride(playerRef)
                    respondNow(context, "weather", "local override released")
                }
            }

            "watch" -> {
                val playerRef = ref ?: return respond(context, "weather", "requires a player to toggle watch")
                val enabled =
                    when (args.getOrNull(1)?.lowercase()) {
                        "on" -> true
                        "off" -> false
                        else -> return respond(context, "weather", "usage: weather watch on|off")
                    }
                withWorld(context, playerRef) { worldStore ->
                    val watch = worldStore.getResource(SpikeWeatherWatch.type)
                    watch.enabled = enabled
                    if (!enabled) watch.lastWeatherByEnvironment.clear()
                    respondNow(context, "weather", "watch ${if (enabled) "on" else "off"}")
                }
            }

            else -> {
                respond(context, "weather", "usage: weather [local <WeatherId>|clear|watch on|off]")
            }
        }
    }

    private fun events(
        context: CommandContext,
        args: List<String>,
    ): CompletableFuture<Void> {
        if (args.isEmpty() || args[0].lowercase() == "list") {
            val ids =
                WorldEventAsset.ASSET_MAP.assetMap.keys
                    .sorted()
            val vanilla = ids.filter { matches(it, "goblin", "breach", "coffer", "bloodmoon", "blood_moon") }
            return respond(
                context,
                "events",
                "assets=${ids.size} [${ids.take(40).joinToString()}] " +
                    "active=${GlobalWorldEventManager.INSTANCE.activeIds().joinToString(
                        prefix = "[",
                        postfix = "]",
                    )} " +
                    "candidateVanilla=${vanilla.ifEmpty { listOf("none found by ID") }} " +
                    "MoonPhaseChangeEvent is a separate moon-phase event",
            )
        }
        return when (args[0].lowercase()) {
            "start" -> {
                val ref =
                    context.senderAsPlayerRef()
                        ?: return respond(context, "events", "requires a player to start a world event")
                val assetId = args.getOrNull(1) ?: return respond(context, "events", "usage: events start <assetId>")
                withWorld(context, ref) { store ->
                    val event = EventStartAction.startEvent(store, WorldEventRecord(UUID.randomUUID(), assetId, true))
                    respondNow(context, "events", "start asset=$assetId id=${event?.uuid ?: "rejected"}")
                }
            }

            "signal" -> {
                val ref =
                    context.senderAsPlayerRef()
                        ?: return respond(context, "events", "requires a player to signal")
                val id =
                    args.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return respond(context, "events", "usage: events signal <eventUuid> <signalId>")
                val signalId =
                    args.getOrNull(2)
                        ?: return respond(context, "events", "usage: events signal <eventUuid> <signalId>")
                withWorld(context, ref) { store ->
                    store.invoke(ref, WorldEventSignal(id, signalId))
                    respondNow(context, "events", "signal sent id=$id signal=$signalId")
                }
            }

            else -> {
                respond(context, "events", "usage: events [list|start <assetId>|signal <eventUuid> <signalId>]")
            }
        }
    }

    private fun realm(
        context: CommandContext,
        args: List<String>,
    ): CompletableFuture<Void> {
        val action = args.firstOrNull()?.lowercase() ?: "status"
        if (action == "list") {
            return respond(context, "realm", "worlds=${Universe.get().worlds.keys.sorted().joinToString()}")
        }
        if (action == "selftest") return respond(context, "realm", "round-trip is included in craftory selftest")
        val ref = context.senderAsPlayerRef() ?: return respond(context, "realm", "requires a player")
        return withWorld(context, ref) { store ->
            when (action) {
                "create" -> {
                    createRealm(context, ref, store)
                }

                "leave" -> {
                    InstancesPlugin.exitInstance(ref, store)
                    respondNow(context, "realm", "exit requested")
                }

                "status" -> {
                    val world = store.externalData.world
                    val worlds =
                        Universe
                            .get()
                            .worlds.keys
                            .sorted()
                            .joinToString()
                    respondNow(context, "realm", "world=${world.name} worlds=$worlds")
                }

                else -> {
                    respondNow(context, "realm", "usage: realm create|leave|list|status")
                }
            }
        }
    }

    private fun createRealm(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
    ) {
        val player =
            store.getComponent(ref, PlayerRef.getComponentType())
                ?: return respondNow(context, "realm", "player data unavailable")
        val crewId =
            CraftoryCore
                .crewService()
                .crewOf(player.uuid)
                ?.id
                ?.toString()
                ?: return respondNow(context, "realm", "player has no crew")
        val asset =
            InstancesPlugin
                .get()
                .getInstanceAssets()
                .sorted()
                .firstOrNull()
                ?: return respondNow(context, "realm", "no vanilla instance assets are loaded")
        val name = "craftory_spike_${crewId.take(8)}"
        val transform =
            store.getComponent(ref, TransformComponent.getComponentType())?.transform
                ?: return respondNow(context, "realm", "player transform unavailable")
        val world = store.externalData.world
        val instanceFuture =
            InstancesPlugin.get().spawnInstance(
                asset,
                name,
                world,
                transform,
                Consumer { config ->
                    InstanceWorldConfig.ensureAndGet(config).setRemovalConditions(WorldEmptyCondition(5.0))
                },
            )
        InstancesPlugin.teleportPlayerToLoadingInstance(ref, store, instanceFuture, null)
        instanceFuture.whenComplete { instance, failure ->
            world.execute {
                if (failure != null) {
                    respondNow(context, "realm", "create failed: ${failure.message}")
                } else {
                    respondNow(context, "realm", "created world=${instance.name} asset=$asset")
                }
            }
        }
    }

    private fun slow(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        args: List<String>,
    ) {
        val action = args.firstOrNull()?.lowercase()
        when (action) {
            "on" -> {
                val slowed = store.getComponent(ref, SpikeSlowed.type)
                if (slowed == null) {
                    val movement =
                        store.getComponent(ref, MovementManager.getComponentType())
                            ?: return respondNow(context, "slow", "movement manager unavailable")
                    val player =
                        store.getComponent(ref, PlayerRef.getComponentType())
                            ?: return respondNow(context, "slow", "player ref unavailable")
                    val newSlowed = store.addComponent(ref, SpikeSlowed.type)
                    newSlowed.originalSpeed = movement.settings.baseSpeed
                    newSlowed.manual = true
                    movement.settings.baseSpeed = newSlowed.originalSpeed * 0.5f
                    movement.update(player.packetHandler)
                } else {
                    slowed.manual = true
                }
                val speed = store.getComponent(ref, MovementManager.getComponentType())?.settings?.baseSpeed
                respondNow(context, "slow", "manual slow on speed=${speed ?: "unavailable"}")
            }

            "off" -> {
                val heldRule = store.getComponent(ref, SpikeHeldSlow.type)
                if (heldRule != null) store.removeComponent(ref, SpikeHeldSlow.type)
                val slowed =
                    store.getComponent(ref, SpikeSlowed.type)
                        ?: return respondNow(
                            context,
                            "slow",
                            if (heldRule != null) "held-item rule removed" else "not slowed",
                        )
                slowed.manual = false
                slowed.held = false
                restoreSpeedIfUnused(ref, store, slowed)
                val speed = store.getComponent(ref, MovementManager.getComponentType())?.settings?.baseSpeed
                respondNow(context, "slow", "restored speed=${speed ?: "unavailable"}")
            }

            "held" -> {
                val id = args.getOrNull(1) ?: return respondNow(context, "slow", "usage: slow held <itemId>")
                val held = store.getComponent(ref, SpikeHeldSlow.type) ?: store.addComponent(ref, SpikeHeldSlow.type)
                held.itemId = id
                respondNow(context, "slow", "held-item rule set to $id (checked every 20 ticks)")
            }

            else -> {
                respondNow(context, "slow", "usage: slow on|off|held <itemId>")
            }
        }
    }

    private fun beam(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        args: List<String>,
    ) {
        val ids =
            Beam
                .getAssetMap()
                .assetMap.keys
                .sorted()
        if (args.firstOrNull() == "list") {
            return respondNow(context, "beam", "assets=${ids.size} ${ids.take(40).joinToString()}")
        }
        val seconds = args.firstOrNull()?.toFloatOrNull()?.coerceIn(1f, 30f) ?: 5f
        val target =
            TargetUtil.getTargetBlock(ref, 32.0, store)
                ?: return respondNow(context, "beam", "look at a block within 32 blocks; assets=${ids.size}")
        val index = Beam.getAssetMap().getIndex(ids.firstOrNull() ?: "")
        if (index < 0) return respondNow(context, "beam", "no beam assets are loaded")
        val position =
            store.getComponent(ref, TransformComponent.getComponentType())?.position
                ?: return respondNow(context, "beam", "player transform unavailable")
        val eye = Vector3d(position.x, position.y + 1.6, position.z)
        val beamRef =
            BeamComponent.spawn(
                store,
                eye,
                seconds,
                AttachedBeam.toBlock(index, 1f, null, target.x, target.y, target.z),
            )
        respondNow(context, "beam", "spawned ref=${beamRef.index} asset=${ids.firstOrNull()} lifetime=${seconds}s")
    }

    private fun projectile(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        args: List<String>,
    ) {
        if (args.firstOrNull()?.lowercase() != "ward") {
            return respondNow(context, "projectile", "usage: projectile ward on|off")
        }
        val ward = store.getResource(SpikeProjectileWard.type)
        when (args.getOrNull(1)?.lowercase()) {
            "on" -> {
                val position =
                    store.getComponent(ref, TransformComponent.getComponentType())?.position
                        ?: return respondNow(context, "projectile", "player transform unavailable")
                ward.centerX = position.x
                ward.centerY = position.y
                ward.centerZ = position.z
                ward.intercepted = 0
                ward.enabled = true
                respondNow(
                    context,
                    "projectile",
                    "ward on center=${ward.centerX},${ward.centerY},${ward.centerZ} radius=${ward.radius}",
                )
            }

            "off" -> {
                ward.enabled = false
                respondNow(context, "projectile", "ward off intercepted=${ward.intercepted}")
            }

            else -> {
                respondNow(context, "projectile", "usage: projectile ward on|off")
            }
        }
    }

    private fun claims(
        context: CommandContext,
        args: List<String>,
    ): CompletableFuture<Void> {
        if (args.firstOrNull()?.lowercase() != "guard") {
            val names =
                com.hypixel.hytale.server.core.plugin.PluginManager
                    .get()
                    .getPlugins()
                    .map { it.name }
                    .filter { matches(it, "claim", "protect") }
            return respond(
                context,
                "claims",
                "matching plugins=${names.ifEmpty { listOf("none") }}; no claim API in 0.7.4",
            )
        }
        val ref = context.senderAsPlayerRef() ?: return respond(context, "claims", "guard requires a player")
        val enabled =
            when (args.getOrNull(1)?.lowercase()) {
                "on" -> true
                "off" -> false
                else -> return respond(context, "claims", "usage: claims guard on|off")
            }
        return withWorld(context, ref) { store ->
            val guard = store.getResource(SpikeClaimGuard.type)
            if (enabled) {
                val player =
                    store.getComponent(ref, PlayerRef.getComponentType())
                        ?: return@withWorld respondNow(context, "claims", "player ref unavailable")
                val ownerCrew = CraftoryCore.crewService().crewOf(player.uuid)
                if (ownerCrew.solo) return@withWorld respondNow(context, "claims", "player has no crew")
                val ownerCrewId = ownerCrew.id
                val position =
                    store.getComponent(ref, TransformComponent.getComponentType())?.position
                        ?: return@withWorld respondNow(context, "claims", "player transform unavailable")
                guard.centerX = position.x
                guard.centerY = position.y
                guard.centerZ = position.z
                guard.ownerCrewId = ownerCrewId
            }
            guard.enabled = enabled
            respondNow(context, "claims", "guard ${if (enabled) "on" else "off"} owner=${guard.ownerCrewId} radius=8")
        }
    }

    private fun mount(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
    ) {
        val candidates = NPCPlugin.get().getRoleTemplateNames(true)
        val role =
            candidates.firstOrNull { matches(it, "fly", "bird", "dragon", "bat") }
                ?: return respondNow(context, "mount", "no role name suggests flight; tried names=${candidates.size}")
        val transform =
            store.getComponent(ref, TransformComponent.getComponentType())
                ?: return respondNow(context, "mount", "player transform unavailable")
        val playerRef =
            store.getComponent(ref, PlayerRef.getComponentType())
                ?: return respondNow(context, "mount", "player ref unavailable")
        val spawned =
            NPCPlugin.get().spawnNPC(
                store,
                role,
                null,
                Vector3d(transform.position.x, transform.position.y + 1.0, transform.position.z),
                transform.rotation,
            )
        val mountRef =
            spawned?.first()
                ?: return respondNow(context, "mount", "could not spawn candidate role=$role")
        val mount = store.ensureAndGetComponent(mountRef, NPCMountComponent.getComponentType())
        mount.originalRoleIndex = NPCPlugin.get().getIndex(role)
        mount.ownerPlayerRef = playerRef
        mount.setAnchor(0f, 1f, 0f)
        respondNow(
            context,
            "mount",
            "spawned role=$role ref=${mountRef.index}; server-side mount ownership set; " +
                "client flight control unverified",
        )
    }

    private fun hud(
        context: CommandContext,
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        args: List<String>,
    ) {
        val action = args.firstOrNull()?.lowercase()
        val player =
            store.getComponent(ref, Player.getComponentType())
                ?: return respondNow(context, "hud", "player unavailable")
        val playerRef =
            store.getComponent(ref, PlayerRef.getComponentType())
                ?: return respondNow(context, "hud", "player ref unavailable")
        when (action) {
            "start" -> {
                val probe = store.getComponent(ref, SpikeHudProbe.type) ?: store.addComponent(ref, SpikeHudProbe.type)
                probe.startedAtNanos = System.nanoTime()
                probe.updates = 0L
                probe.totalBuildNanos = 0L
                val hud =
                    player.hudManager.getCustomHud("Craftory_Spike_Hud") as? SpikeHud
                        ?: SpikeHud(playerRef).also { player.hudManager.addCustomHud(playerRef, it) }
                hud.render(store.externalData.world.tick, 0L)
                respondNow(context, "hud", "started; client RTT cannot be measured headless")
            }

            "stop" -> {
                val probe = store.getComponent(ref, SpikeHudProbe.type)
                player.hudManager.removeCustomHud(playerRef, "Craftory_Spike_Hud")
                if (probe == null) return respondNow(context, "hud", "not running")
                val elapsed = (System.nanoTime() - probe.startedAtNanos).coerceAtLeast(1L)
                val rate = probe.updates * 1_000_000_000.0 / elapsed
                val average = if (probe.updates == 0L) 0.0 else probe.totalBuildNanos.toDouble() / probe.updates
                store.removeComponent(ref, SpikeHudProbe.type)
                respondNow(
                    context,
                    "hud",
                    "stopped updates=${probe.updates} " +
                        "updatesPerSecond=${"%.2f".format(rate)} " +
                        "averageUpdateNanos=${"%.1f".format(average)} RTT=unmeasurable headless",
                )
            }

            else -> {
                respondNow(context, "hud", "usage: hud start|stop")
            }
        }
    }

    private fun withWorld(
        context: CommandContext,
        ref: Ref<EntityStore>,
        action: (Store<EntityStore>) -> Unit,
    ): CompletableFuture<Void> {
        val store = ref.store
        return CompletableFuture.runAsync({ action(store) }, store.externalData.world)
    }

    private fun weatherId(index: Int): String = Weather.getAssetMap().getAsset(index)?.id ?: "index:$index"

    private fun matches(
        value: String,
        vararg terms: String,
    ): Boolean = terms.any { value.contains(it, true) }

    private fun usage(context: CommandContext): CompletableFuture<Void> {
        val line =
            "[spike command] usage: " +
                "/craftory spike <lightning|weather|events|realm|slow|beam|projectile|claims|mount|hud>"
        context.sendMessage(Message.translation("server.craftory.spike.help"))
        logger.atInfo().log(line)
        return CompletableFuture.completedFuture(null)
    }

    private fun respond(
        context: CommandContext,
        name: String,
        text: String,
    ): CompletableFuture<Void> {
        respondNow(context, name, text)
        return CompletableFuture.completedFuture(null)
    }

    private fun respondNow(
        context: CommandContext,
        name: String,
        text: String,
    ) {
        val line = "[spike $name] $text"
        context.sendMessage(Message.raw(line))
        logger.atInfo().log(line)
    }
}
