package studio.craftory.tech.well

import com.hypixel.hytale.builtin.mounts.MountedComponent
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.AddReason
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.RefSystem
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.NotificationUtil
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.mob.CraftoryMobs
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.machine.EnergyHudState
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

private const val READOUT_INTERVAL_TICKS = 100L
private val LOGGER = Logger.getLogger(JarCarrierSystem::class.java.name)
private val loggedBurstFailures = ConcurrentHashMap.newKeySet<java.util.UUID>()

private object BurstFailureLog {
    fun first(player: java.util.UUID): Boolean = loggedBurstFailures.add(player)

    fun clear(player: java.util.UUID) {
        loggedBurstFailures.remove(player)
    }
}

class JarCarrier(
    var nextAmbushTick: Long = 0,
    var lastReadoutTick: Long = 0,
    var originalBaseSpeed: Float = 1.0f,
    var lowestStability: Double = 100.0,
) : Component<EntityStore> {
    override fun clone(): Component<EntityStore> =
        JarCarrier(
            nextAmbushTick,
            lastReadoutTick,
            originalBaseSpeed,
            lowestStability,
        )

    companion object {
        lateinit var type: ComponentType<EntityStore, JarCarrier>
        val CODEC: BuilderCodec<JarCarrier> =
            BuilderCodec
                .builder(JarCarrier::class.java, ::JarCarrier)
                .append(KeyedCodec("NextAmbushTick", Codec.LONG), { state, value -> state.nextAmbushTick = value }, {
                    it.nextAmbushTick
                })
                .add()
                .append(KeyedCodec("LastReadoutTick", Codec.LONG), { state, value -> state.lastReadoutTick = value }, {
                    it.lastReadoutTick
                })
                .add()
                .append(
                    KeyedCodec(
                        "OriginalBaseSpeed",
                        Codec.FLOAT,
                    ),
                    { state, value -> state.originalBaseSpeed = value },
                    {
                        it.originalBaseSpeed
                    },
                ).add()
                .append(
                    KeyedCodec("LowestStability", Codec.DOUBLE),
                    { state, value -> state.lowestStability = value },
                    { it.lowestStability },
                ).add()
                .build()
    }
}

internal class JarCarrierSystem : DelayedEntitySystem<EntityStore>(1.0f) {
    override fun getQuery(): Query<EntityStore> =
        Query.and(PlayerRef.getComponentType(), TransformComponent.getComponentType())

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val tick = world.tick
        for (index in 0 until chunk.size()) {
            val ref = chunk.getReferenceTo(index)
            if (WellSelfTestExclusions.isEntity(ref)) continue
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType())
            val transform = chunk.getComponent(index, TransformComponent.getComponentType())
            if (playerRef != null && transform != null) {
                processPlayer(ref, playerRef, transform.position, store, world, tick)
            }
        }
    }

    internal fun processPlayer(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        position: Vector3d,
        store: Store<EntityStore>,
        world: World,
        tick: Long,
    ) {
        val inventory = InventoryComponent.getCombined(store, ref, *InventoryComponent.EVERYTHING)
        val existing = store.getComponent(ref, JarCarrier.type)
        val mounted = store.getComponent(ref, MountedComponent.getComponentType()) != null
        val jars = filledJarSlots(inventory)
        if (jars.isEmpty()) {
            if (existing != null) removeCarrier(ref, playerRef, position, store, existing)
            return
        }
        val carrier = existing ?: createCarrier(ref, store, tick)
        var lowestStability = 100.0
        val weatherKind = weatherKindAt(world, position)
        jars.forEach { (slot, item) ->
            val stability = (item.getFromMetadataOrNull(GobJarMetadata.STABILITY) ?: 100.0).coerceIn(0.0, 100.0)
            val temperament =
                Temperament.entries.firstOrNull {
                    it.id == item.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT)
                } ?: Temperament.SPARK
            var nextStability = JarCarrierRules.drainForSecond(stability, temperament, weatherKind)
            if (JarCarrierRules.shouldBurst(nextStability)) {
                if (spawnBurstWell(world, position, item)) {
                    check(JarCarrierRules.burstResult(nextStability, true) == JarCarrierRules.BurstResult.CONSUME)
                    inventory.setItemStackForSlot(slot, ItemStack.EMPTY)
                    BurstFailureLog.clear(playerRef.uuid)
                    clearCarrierReadout(playerRef, position, store)
                    knockBack(ref, store, position)
                    return@forEach
                }
                check(JarCarrierRules.burstResult(nextStability, false) == JarCarrierRules.BurstResult.RETRY_AT_ONE)
                nextStability = 1.0
                if (BurstFailureLog.first(playerRef.uuid)) {
                    LOGGER.warning("Gob-Jar burst Well spawn failed; retaining jar at stability 1 for retry")
                }
            }
            if (nextStability != stability) {
                inventory.setItemStackForSlot(slot, item.withMetadata(GobJarMetadata.STABILITY, nextStability))
            }
            lowestStability = minOf(lowestStability, nextStability)
        }
        if (filledJarSlots(inventory).isEmpty()) {
            removeCarrier(ref, playerRef, position, store, carrier)
            return
        }
        carrier.lowestStability = lowestStability
        if (mounted) restoreMovement(playerRef, ref, store, carrier) else applySlowdown(playerRef, ref, store, carrier)
        updateAmbush(world, position, playerRef, carrier, tick)
        updateReadout(playerRef, position, carrier, store, tick)
    }

    private fun filledJarSlots(
        inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
    ): List<Pair<Short, ItemStack>> =
        (0 until inventory.capacity.toInt()).mapNotNull { slot ->
            val item = inventory.getItemStack(slot.toShort())
            if (item?.itemId == CraftoryIds.GOB_JAR_FILLED) slot.toShort() to item else null
        }

    private fun createCarrier(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        tick: Long,
    ): JarCarrier {
        val movement = store.getComponent(ref, MovementManager.getComponentType())
        val carrier =
            JarCarrier(
                nextAmbushTick = tick + AMBUSH_INTERVAL_TICKS,
                originalBaseSpeed = movement?.settings?.baseSpeed ?: 1.0f,
            )
        store.putComponent(ref, JarCarrier.type, carrier)
        return carrier
    }

    private fun updateAmbush(
        world: World,
        position: Vector3d,
        playerRef: PlayerRef,
        carrier: JarCarrier,
        tick: Long,
    ) {
        if (tick < carrier.nextAmbushTick) return
        carrier.nextAmbushTick = tick + AMBUSH_INTERVAL_TICKS
        if (JarCarrierRules.ambushRoll(WellRuntimeConfig.current.jarAmbushChance, Random)) {
            playerRef.reference?.let { spawnPoachers(world, position, it) }
        }
    }

    private fun updateReadout(
        playerRef: PlayerRef,
        position: Vector3d,
        carrier: JarCarrier,
        store: Store<EntityStore>,
        tick: Long,
    ) {
        if (tick - carrier.lastReadoutTick < READOUT_INTERVAL_TICKS) return
        carrier.lastReadoutTick = tick
        showReadout(playerRef, position, carrier, store)
    }

    private fun removeCarrier(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        position: Vector3d,
        store: Store<EntityStore>,
        carrier: JarCarrier,
    ) {
        store.removeComponent(ref, JarCarrier.type)
        BurstFailureLog.clear(playerRef.uuid)
        restoreMovement(playerRef, ref, store, carrier)
        clearCarrierReadout(playerRef, position, store)
    }

    companion object {
        private const val AMBUSH_INTERVAL_TICKS = 1200L
    }
}

private const val CREW_READOUT_RADIUS_SQUARED = 32.0 * 32.0

private fun weatherKindAt(
    world: World,
    position: Vector3d,
): WeatherKind {
    val blockPos = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    val section =
        world.chunkStore.getChunkSectionReferenceAtBlock(blockPos.x, blockPos.y, blockPos.z) ?: return WeatherKind.OTHER
    val sectionData =
        world.chunkStore.store.getComponent(section, EnvironmentSection.getComponentType()) ?: return WeatherKind.OTHER
    val environment =
        sectionData.get(blockPos.x and 31, blockPos.y and 31, blockPos.z and 31)
            ?: return WeatherKind.OTHER
    val weatherResource = world.entityStore.store.getResource(WeatherResource.getResourceType())
    val weatherIndex = weatherResource.getEffectiveWeatherIndex(environment)
    val id =
        Weather
            .getAssetMap()
            .getAsset(weatherIndex)
            ?.id
            .orEmpty()
    return StormHuntRules.weatherKind(id)
}

internal fun applySlowdown(
    playerRef: PlayerRef,
    ref: Ref<EntityStore>,
    store: Store<EntityStore>,
    carrier: JarCarrier,
) {
    val movement = store.getComponent(ref, MovementManager.getComponentType()) ?: return
    val speed = JarCarrierRules.slowedSpeed(carrier.originalBaseSpeed)
    if (movement.settings.baseSpeed != speed) {
        movement.settings.baseSpeed = speed
        playerRef.packetHandler?.let(movement::update)
    }
}

internal fun restoreMovement(
    playerRef: PlayerRef,
    ref: Ref<EntityStore>,
    store: Store<EntityStore>,
    carrier: JarCarrier,
) {
    val movement = store.getComponent(ref, MovementManager.getComponentType()) ?: return
    if (movement.settings.baseSpeed != carrier.originalBaseSpeed) {
        movement.settings.baseSpeed = carrier.originalBaseSpeed
        playerRef.packetHandler?.let(movement::update)
    }
}

private fun showReadout(
    carrierRef: PlayerRef,
    position: Vector3d,
    carrier: JarCarrier,
    store: Store<EntityStore>,
) {
    val crews = CraftoryCore.crewService()
    val crewId = crews.crewOf(carrierRef.uuid).id
    val worldUuid = store.externalData.world.worldConfig.uuid
    Universe.get().getPlayers().forEach { playerRef ->
        if (playerRef.worldUuid != worldUuid) return@forEach
        val dx = playerRef.transform.position.x - position.x
        val dy = playerRef.transform.position.y - position.y
        val dz = playerRef.transform.position.z - position.z
        val eligible =
            dx * dx + dy * dy + dz * dz <= CREW_READOUT_RADIUS_SQUARED &&
                (playerRef.uuid == carrierRef.uuid || crews.crewOf(playerRef.uuid).id == crewId)
        if (playerRef.uuid == carrierRef.uuid) {
            NotificationUtil.sendNotification(
                playerRef.packetHandler,
                Message.raw("Gob-Jar stability: ${carrier.lowestStability.toInt()}%"),
                NotificationStyle.Default,
            )
            return@forEach
        }
        val entity = playerRef.reference ?: return@forEach
        val player = store.getComponent(entity, Player.getComponentType()) ?: return@forEach
        val goggles =
            player.inventory
                ?.armor
                ?.getItemStack(0.toShort())
                ?.itemId == CraftoryIds.SQUINT_GOGGLES
        if (eligible && goggles) {
            val readout = store.getComponent(entity, EnergyHudState.type) ?: return@forEach
            readout.carrierStability = carrier.lowestStability.toInt()
            readout.carrierStabilityUntilTick = store.externalData.world.tick + READOUT_INTERVAL_TICKS
        }
    }
}

internal fun clearCarrierReadout(
    carrierRef: PlayerRef,
    position: Vector3d,
    store: Store<EntityStore>,
) {
    val crews = CraftoryCore.crewService()
    val crewId = crews.crewOf(carrierRef.uuid).id
    val worldUuid = store.externalData.world.worldConfig.uuid
    Universe.get().getPlayers().forEach { playerRef ->
        if (playerRef.worldUuid != worldUuid) return@forEach
        val dx = playerRef.transform.position.x - position.x
        val dy = playerRef.transform.position.y - position.y
        val dz = playerRef.transform.position.z - position.z
        if (dx * dx + dy * dy + dz * dz > CREW_READOUT_RADIUS_SQUARED ||
            crews.crewOf(playerRef.uuid).id != crewId
        ) {
            return@forEach
        }
        val entity = playerRef.reference ?: return@forEach
        val readout = store.getComponent(entity, EnergyHudState.type) ?: return@forEach
        readout.carrierStability = null
        readout.carrierStabilityUntilTick = 0
    }
}

private fun spawnPoachers(
    world: World,
    position: Vector3d,
    targetRef: Ref<EntityStore>,
) {
    repeat(2) {
        val angle = Random.nextDouble() * Math.PI * 2.0
        val distance = 16.0 + Random.nextDouble() * 8.0
        val target =
            Vector3d(
                position.x + kotlin.math.cos(angle) * distance,
                position.y,
                position.z + kotlin.math.sin(angle) * distance,
            )
        val poacher =
            CraftoryMobs.spawn(
                world,
                CraftoryIds.GOBLIN_POACHER,
                target,
                BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt()),
            ) ?: return@repeat
        world.entityStore.store
            .getComponent(poacher, NPCEntity.getComponentType()!!)
            ?.onFlockSetTarget("LockedTarget", targetRef)
    }
}

private fun spawnBurstWell(
    world: World,
    position: Vector3d,
    jar: ItemStack,
): Boolean {
    val origin = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    val surface =
        WellBirths.findSurface(world, origin.x, origin.z, origin.y)
            ?: return false
    val temperament =
        Temperament.entries.firstOrNull { it.id == jar.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) }
            ?: Temperament.SPARK
    val strength =
        WellStrength.entries.firstOrNull { it.name == jar.getFromMetadataOrNull(GobJarMetadata.STRENGTH) }
            ?: WellStrength.FLICKER
    return CraftoryCore.wells().spawn(world, surface, temperament, strength, WellState.WILD) != null
}

private fun knockBack(
    ref: Ref<EntityStore>,
    store: Store<EntityStore>,
    position: Vector3d,
) {
    val transform = store.getComponent(ref, TransformComponent.getComponentType()) ?: return
    val velocity = store.getComponent(ref, Velocity.getComponentType()) ?: return
    val dx = transform.position.x - position.x
    val dz = transform.position.z - position.z
    val length = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
    velocity.addVelocity(dx / length * 0.8, 0.4, dz / length * 0.8)
}

internal class JarCarrierDamageSystem : DamageEventSystem() {
    override fun getQuery(): Query<EntityStore> = JarCarrier.type

    override fun getGroup() =
        com.hypixel.hytale.server.core.modules.entity.damage.DamageModule
            .get()
            .filterDamageGroup

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: Damage,
    ) {
        if (event.isCancelled || event.amount <= 0f) return
        val ref = chunk.getReferenceTo(index)
        val inventory = InventoryComponent.getCombined(store, ref, *InventoryComponent.EVERYTHING)
        for (slot in 0 until inventory.capacity.toInt()) {
            val item = inventory.getItemStack(slot.toShort())
            if (item?.itemId != CraftoryIds.GOB_JAR_FILLED) continue
            val stability = (item.getFromMetadataOrNull(GobJarMetadata.STABILITY) ?: 100.0).coerceIn(0.0, 100.0)
            val reduced = (stability - 10.0).coerceAtLeast(0.0)
            if (reduced != stability) {
                inventory.setItemStackForSlot(slot.toShort(), item.withMetadata(GobJarMetadata.STABILITY, reduced))
            }
        }
    }
}

internal class JarCarrierDropSystem : EntityEventSystem<EntityStore, DropItemEvent>(DropItemEvent::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType(), JarCarrier.type)

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: DropItemEvent,
    ) {
        val carrier = chunk.getComponent(index, JarCarrier.type) ?: return
        val drop = event as? DropItemEvent.Drop ?: return
        if (drop.itemStack.itemId != CraftoryIds.GOB_JAR_FILLED) return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: return
        clearCarrierReadout(player, transform.position, store)
    }
}

internal class JarCarrierSeatLifecycle : com.hypixel.hytale.component.system.RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(PlayerRef.getComponentType(), JarCarrier.type, MountedComponent.getComponentType())

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: com.hypixel.hytale.component.AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val carrier = store.getComponent(ref, JarCarrier.type)
        val player = store.getComponent(ref, PlayerRef.getComponentType()) ?: return
        if (carrier != null) restoreMovement(player, ref, store, carrier)
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: com.hypixel.hytale.component.RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val carrier = commandBuffer.getComponent(ref, JarCarrier.type) ?: return
        val player = commandBuffer.getComponent(ref, PlayerRef.getComponentType()) ?: return
        if (commandBuffer.getComponent(ref, MountedComponent.getComponentType()) == null) {
            applySlowdown(player, ref, store, carrier)
        }
    }
}

internal class JarCarrierSaveLifecycle : RefSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> = JarCarrier.type

    override fun onEntityAdded(
        ref: Ref<EntityStore>,
        reason: AddReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) = Unit

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        if (reason != RemoveReason.UNLOAD && reason != RemoveReason.REMOVE) return
        val carrier = commandBuffer.getComponent(ref, JarCarrier.type) ?: return
        val player = commandBuffer.getComponent(ref, PlayerRef.getComponentType()) ?: return
        val position = commandBuffer.getComponent(ref, TransformComponent.getComponentType())?.position ?: return
        restoreMovement(player, ref, store, carrier)
        clearCarrierReadout(player, position, store)
    }
}
