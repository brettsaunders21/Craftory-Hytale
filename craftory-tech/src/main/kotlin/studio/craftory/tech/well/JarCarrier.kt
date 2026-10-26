package studio.craftory.tech.well

import com.hypixel.hytale.builtin.mounts.MountedComponent
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
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
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

private const val READOUT_INTERVAL_TICKS = 100L

class JarCarrier(
    var stability: Double = 100.0,
    var temperamentId: String = Temperament.SPARK.id,
    var strengthId: String = WellStrength.FLICKER.name,
    var taste: String = "",
    var wellId: String = "",
    var lastTick: Long = 0,
    var nextAmbushTick: Long = 0,
    var lastReadoutTick: Long = 0,
    var originalBaseSpeed: Float = 1.0f,
) : Component<EntityStore> {
    override fun clone(): Component<EntityStore> =
        JarCarrier(
            stability,
            temperamentId,
            strengthId,
            taste,
            wellId,
            lastTick,
            nextAmbushTick,
            lastReadoutTick,
            originalBaseSpeed,
        )

    companion object {
        lateinit var type: ComponentType<EntityStore, JarCarrier>
        val CODEC: BuilderCodec<JarCarrier> =
            BuilderCodec
                .builder(JarCarrier::class.java, ::JarCarrier)
                .append(
                    KeyedCodec("Stability", Codec.DOUBLE),
                    { state, value -> state.stability = value },
                    { it.stability },
                ).add()
                .append(KeyedCodec("TemperamentId", Codec.STRING), { state, value -> state.temperamentId = value }, {
                    it.temperamentId
                })
                .add()
                .append(
                    KeyedCodec("StrengthId", Codec.STRING),
                    { state, value -> state.strengthId = value },
                    { it.strengthId },
                ).add()
                .append(KeyedCodec("Taste", Codec.STRING), { state, value -> state.taste = value }, { it.taste })
                .add()
                .append(KeyedCodec("WellId", Codec.STRING), { state, value -> state.wellId = value }, { it.wellId })
                .add()
                .append(KeyedCodec("LastTick", Codec.LONG), { state, value -> state.lastTick = value }, { it.lastTick })
                .add()
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

    private fun processPlayer(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        position: Vector3d,
        store: Store<EntityStore>,
        world: World,
        tick: Long,
    ) {
        val inventory = InventoryComponent.getCombined(store, ref, *InventoryComponent.EVERYTHING)
        val jar = findFilledJar(inventory)
        val existing = store.getComponent(ref, JarCarrier.type)
        if (jar == null) {
            if (existing != null) removeCarrier(ref, playerRef, store, inventory, existing)
            return
        }
        val carrier = existing ?: createCarrier(ref, jar.second, store, tick)
        updateCarrier(ref, playerRef, position, store, world, tick, inventory, jar.first, carrier)
    }

    private fun findFilledJar(
        inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
    ): Pair<Short, ItemStack>? =
        (0 until inventory.capacity.toInt()).firstNotNullOfOrNull { slot ->
            val item = inventory.getItemStack(slot.toShort())
            if (item?.itemId == CraftoryIds.GOB_JAR_FILLED) slot.toShort() to item else null
        }

    private fun createCarrier(
        ref: Ref<EntityStore>,
        jar: ItemStack,
        store: Store<EntityStore>,
        tick: Long,
    ): JarCarrier {
        val movement = store.getComponent(ref, MovementManager.getComponentType())
        val carrier =
            JarCarrier(
                stability =
                    (jar.getFromMetadataOrNull(GobJarMetadata.STABILITY) ?: 100.0).coerceIn(0.0, 100.0),
                temperamentId = jar.getFromMetadataOrNull(GobJarMetadata.TEMPERAMENT) ?: Temperament.SPARK.id,
                strengthId = jar.getFromMetadataOrNull(GobJarMetadata.STRENGTH) ?: WellStrength.FLICKER.name,
                taste = jar.getFromMetadataOrNull(GobJarMetadata.TASTE) ?: "",
                wellId = jar.getFromMetadataOrNull(GobJarMetadata.WELL_ID) ?: "",
                lastTick = tick,
                nextAmbushTick = tick + AMBUSH_INTERVAL_TICKS,
                originalBaseSpeed = movement?.settings?.baseSpeed ?: 1.0f,
            )
        store.putComponent(ref, JarCarrier.type, carrier)
        return carrier
    }

    private fun updateCarrier(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        position: Vector3d,
        store: Store<EntityStore>,
        world: World,
        tick: Long,
        inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
        jarSlot: Short,
        carrier: JarCarrier,
    ) {
        val elapsed = (tick - carrier.lastTick).coerceAtLeast(0L) / 20.0
        carrier.lastTick = tick
        drainStability(carrier, world, position, elapsed)
        applySlowdown(playerRef, ref, store, carrier)
        updateAmbush(world, position, playerRef, carrier, tick)
        updateReadout(playerRef, position, carrier, store, tick)
        if (JarCarrierRules.shouldBurst(carrier.stability)) {
            burst(ref, playerRef, position, store, world, inventory, jarSlot, carrier)
        }
    }

    private fun drainStability(
        carrier: JarCarrier,
        world: World,
        position: Vector3d,
        elapsed: Double,
    ) {
        val temperament = Temperament.entries.firstOrNull { it.id == carrier.temperamentId } ?: Temperament.SPARK
        val weatherSeconds = if (dislikedWeather(world, position, temperament)) elapsed else 0.0
        carrier.stability = JarCarrierRules.stabilityAfter(carrier.stability, elapsed, 0, weatherSeconds)
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

    private fun burst(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        position: Vector3d,
        store: Store<EntityStore>,
        world: World,
        inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
        jarSlot: Short,
        carrier: JarCarrier,
    ) {
        inventory.setItemStackForSlot(jarSlot, ItemStack.EMPTY)
        spawnBurstWell(world, position, carrier)
        knockBack(ref, store, position)
        removeCarrier(ref, playerRef, store, inventory, carrier)
    }

    private fun removeCarrier(
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        store: Store<EntityStore>,
        inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
        carrier: JarCarrier,
    ) {
        flushCarrierMetadata(inventory, carrier)
        store.removeComponent(ref, JarCarrier.type)
        val movement = store.getComponent(ref, MovementManager.getComponentType()) ?: return
        movement.settings.baseSpeed = carrier.originalBaseSpeed
        movement.update(playerRef.packetHandler)
    }

    companion object {
        private const val AMBUSH_INTERVAL_TICKS = 1200L
    }
}

private const val CREW_READOUT_RADIUS_SQUARED = 32.0 * 32.0

private fun dislikedWeather(
    world: World,
    position: Vector3d,
    temperament: Temperament,
): Boolean {
    val blockPos = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    val section =
        world.chunkStore.getChunkSectionReferenceAtBlock(blockPos.x, blockPos.y, blockPos.z) ?: return false
    val sectionData =
        world.chunkStore.store.getComponent(section, EnvironmentSection.getComponentType()) ?: return false
    val environment = sectionData.get(blockPos.x and 31, blockPos.y and 31, blockPos.z and 31) ?: return false
    val weatherResource = world.entityStore.store.getResource(WeatherResource.getResourceType())
    val weatherIndex = weatherResource.getEffectiveWeatherIndex(environment)
    val id =
        Weather
            .getAssetMap()
            .getAsset(weatherIndex)
            ?.id
            .orEmpty()
    return StormHuntRules.dislikesWeather(temperament, StormHuntRules.weatherKind(id))
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
        movement.update(playerRef.packetHandler)
    }
}

internal fun flushCarrierMetadata(
    inventory: com.hypixel.hytale.server.core.inventory.container.ItemContainer,
    carrier: JarCarrier,
) {
    for (slot in 0 until inventory.capacity.toInt()) {
        val item = inventory.getItemStack(slot.toShort())
        if (item?.itemId == CraftoryIds.GOB_JAR_FILLED) {
            inventory.setItemStackForSlot(slot.toShort(), metadataStack(item, carrier))
        }
    }
}

internal fun metadataStack(
    item: ItemStack,
    carrier: JarCarrier,
): ItemStack =
    item
        .withMetadata(GobJarMetadata.STABILITY, carrier.stability)
        .withMetadata(GobJarMetadata.TEMPERAMENT, carrier.temperamentId)
        .withMetadata(GobJarMetadata.STRENGTH, carrier.strengthId)
        .withMetadata(GobJarMetadata.TASTE, carrier.taste)
        .withMetadata(GobJarMetadata.WELL_ID, carrier.wellId)

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
                Message.raw("Gob-Jar stability: ${carrier.stability.toInt()}%"),
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
            readout.carrierStability = carrier.stability.toInt()
            readout.carrierStabilityUntilTick = store.externalData.world.tick + READOUT_INTERVAL_TICKS
        }
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
    carrier: JarCarrier,
) {
    val origin = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
    val surface =
        WellBirths.findSurface(world, origin.x, origin.z, origin.y)
            ?: return
    val temperament = Temperament.entries.firstOrNull { it.id == carrier.temperamentId } ?: Temperament.SPARK
    val strength = WellStrength.entries.firstOrNull { it.name == carrier.strengthId } ?: WellStrength.FLICKER
    CraftoryCore.wells().spawn(world, surface, temperament, strength, WellState.WILD)
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
        chunk.getComponent(index, JarCarrier.type)?.let { it.stability = (it.stability - 10.0).coerceAtLeast(0.0) }
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
        val item = drop.itemStack
        if (item.itemId == CraftoryIds.GOB_JAR_FILLED) drop.itemStack = metadataStack(item, carrier)
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
        store.getComponent(ref, JarCarrier.type)?.let { carrier ->
            flushCarrierForSeat(ref, carrier, store)
        }
        commandBuffer.tryRemoveComponent(ref, JarCarrier.type)
        store.getComponent(ref, MovementManager.getComponentType())?.resetDefaultsAndUpdate(ref, store)
    }

    override fun onEntityRemove(
        ref: Ref<EntityStore>,
        reason: com.hypixel.hytale.component.RemoveReason,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) = Unit

    private fun flushCarrierForSeat(
        ref: Ref<EntityStore>,
        carrier: JarCarrier,
        store: Store<EntityStore>,
    ) {
        val inventory = InventoryComponent.getCombined(store, ref, *InventoryComponent.EVERYTHING)
        flushCarrierMetadata(inventory, carrier)
    }
}
