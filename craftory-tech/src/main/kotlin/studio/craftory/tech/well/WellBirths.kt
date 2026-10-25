package studio.craftory.tech.well

import com.hypixel.hytale.builtin.weather.components.WeatherTracker
import com.hypixel.hytale.builtin.weather.resources.WeatherResource
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.ComponentAccessor
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.protocol.BlockMaterial
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.Config
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

object WellBirths {
    fun forceStormcall(
        world: World,
        playerOrPos: BlockPos,
        random: Random = Random.Default,
    ): Ref<ChunkStore>? {
        world.debugAssertInTickingThread()
        val strength = WellBirthRules(random).flickerStrength()
        return CraftoryCore.wells().spawn(world, playerOrPos, Temperament.SPARK, strength, WellState.WILD)
    }

    internal fun spawnStormcall(
        world: World,
        position: BlockPos,
        rules: WellBirthRules,
        random: Random,
    ): Ref<ChunkStore>? =
        spawnNear(
            world,
            position,
            Temperament.SPARK,
            rules.flickerStrength(),
            rules.surfaceOffset(),
            random,
        )

    internal fun spawnNear(
        world: World,
        position: BlockPos,
        temperament: Temperament,
        strength: WellStrength,
        offset: Int,
        random: Random,
    ): Ref<ChunkStore>? {
        val angle = random.nextDouble() * Math.PI * 2
        val x = position.x + (cos(angle) * offset).toInt()
        val z = position.z + (sin(angle) * offset).toInt()
        val target = findSurface(world, x, z, position.y) ?: return null
        return CraftoryCore.wells().spawn(world, target, temperament, strength, WellState.WILD)
    }

    private fun findSurface(
        world: World,
        x: Int,
        z: Int,
        originY: Int,
    ): BlockPos? {
        val heightmap =
            world.chunkStore.getChunkComponent(
                ChunkUtil.indexChunkFromBlock(x, z),
                HeightmapColumn.getComponentType(),
            )
        val surfaceY = heightmap?.getCollisionHeight(x and 31, z and 31)
        if (surfaceY != null && surfaceY != HeightmapColumn.NO_HEIGHT) {
            val target = BlockPos(x, surfaceY + 1, z)
            if (isSurface(world, target)) return target
        }
        for (y in originY + 24 downTo originY - 32) {
            val target = BlockPos(x, y, z)
            if (isSurface(world, target)) return target
        }
        return null
    }

    private fun isSurface(
        world: World,
        target: BlockPos,
    ): Boolean {
        val targetIndex = blockIndexAt(world, target.x, target.y, target.z)
        val belowIndex = blockIndexAt(world, target.x, target.y - 1, target.z)
        val below = belowIndex?.let { BlockType.getAssetMap().getAsset(it) }
        return targetIndex == BlockType.EMPTY_ID && below?.material == BlockMaterial.Solid
    }

    private fun blockIndexAt(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ): Int? {
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(x, y, z) ?: return null
        val section = world.chunkStore.store.getComponent(sectionRef, BlockSection.getComponentType()) ?: return null
        return section.get(x and 31, y and 31, z and 31)
    }
}

internal class WellBirthSystem(
    private val config: Config<WellConfig>,
) : DelayedEntitySystem<EntityStore>(1.0f) {
    private val rules = WellBirthRules()

    override fun getQuery(): Query<EntityStore> =
        Query.and(
            PlayerRef.getComponentType(),
            WeatherTracker.getComponentType(),
            TransformComponent.getComponentType(),
        )

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val settings = config.get()
        val birthState = world.chunkStore.store.getResource(WellBirthState.type)
        val weatherResource =
            store.getResource(WeatherResource.getResourceType())
        for (index in 0 until chunk.size()) {
            val player = chunk.getComponent(index, PlayerRef.getComponentType())
            val tracker = chunk.getComponent(index, WeatherTracker.getComponentType())
            val transform = chunk.getComponent(index, TransformComponent.getComponentType())
            if (player != null && tracker != null && transform != null) {
                tickPlayer(world, settings, birthState, weatherResource, player, tracker, transform.position, store)
            }
        }
    }

    private fun tickPlayer(
        world: World,
        settings: WellConfig,
        birthState: WellBirthState,
        weatherResource: WeatherResource,
        player: PlayerRef,
        tracker: WeatherTracker,
        position: Vector3d,
        store: ComponentAccessor<EntityStore>,
    ) {
        val playerPos = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
        val activeSection = world.chunkStore.getChunkSectionReferenceAtBlock(playerPos.x, playerPos.y, playerPos.z)
        if (activeSection == null || !CraftoryCore.isAreaActive(world.chunkStore.store, activeSection)) return
        val weatherId =
            Weather
                .getAssetMap()
                .getAsset(weatherResource.getEffectiveWeatherIndex(tracker.weatherIndex))
                ?.id
                ?.lowercase()
                .orEmpty()
        if (weatherId.contains("thunder")) {
            tryStormcall(world, settings, birthState, player, position, playerPos, store)
        } else {
            tryRainFlicker(world, settings, birthState, player, position, playerPos, weatherId, store)
        }
    }

    private fun tryStormcall(
        world: World,
        settings: WellConfig,
        state: WellBirthState,
        player: PlayerRef,
        position: Vector3d,
        playerPos: BlockPos,
        store: ComponentAccessor<EntityStore>,
    ) {
        val now = world.tick
        val interval = settings.stormcallIntervalSeconds.coerceAtLeast(1) * 20L
        if (!WellBirthRules.cooldownElapsed(state.lastStormcallByPlayer[player.uuid], now, interval)) return
        val region = regionId(playerPos.x shr 9, playerPos.z shr 9)
        if (!WellBirthRules.cooldownElapsed(state.lastStormcallByRegion[region], now, interval)) return
        val chancePassed = rules.stormcall(settings.stormcallChance)
        state.lastStormcallByPlayer[player.uuid] = now
        if (!chancePassed || !noStrongWellInRegion(world, playerPos)) return
        if (WellBirths.spawnStormcall(world, playerPos, rules, Random) != null) {
            state.lastStormcallByRegion[region] = now
            pulseEffects(player, position, store)
        }
    }

    private fun tryRainFlicker(
        world: World,
        settings: WellConfig,
        state: WellBirthState,
        player: PlayerRef,
        position: Vector3d,
        playerPos: BlockPos,
        weatherId: String,
        store: ComponentAccessor<EntityStore>,
    ) {
        if (!weatherId.contains("rain") && !weatherId.contains("storm")) return
        val now = world.tick
        val cooldown = settings.playerFlickerCooldownSeconds.coerceAtLeast(0) * 20L
        if (!WellBirthRules.cooldownElapsed(state.lastFlickerByPlayer[player.uuid], now, cooldown)) return
        if (!rules.rainFlicker(settings.rainFlickerChance)) return
        val spawned =
            WellBirths.spawnNear(
                world,
                playerPos,
                Temperament.TIDE,
                rules.flickerStrength(),
                rules.surfaceOffset(),
                Random,
            )
        if (spawned != null) {
            state.lastFlickerByPlayer[player.uuid] = now
            pulseEffects(player, position, store)
        }
    }

    private fun noStrongWellInRegion(
        world: World,
        position: BlockPos,
    ): Boolean =
        CraftoryCore
            .wells()
            .wellsNear(world.chunkStore.store, position, 512)
            .none { it.well.strength != WellStrength.FLICKER && it.well.state == WellState.WILD }

    private fun pulseEffects(
        player: PlayerRef,
        position: Vector3d,
        store: ComponentAccessor<EntityStore>,
    ) {
        ParticleUtil.spawnParticleEffect("Impact_Spark", position, store)
        SoundUtil.playSoundEvent2dToPlayer(
            player,
            "SFX_Interact",
            SoundCategory.SFX,
        )
    }

    private fun regionId(
        x: Int,
        z: Int,
    ): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
}

internal class WellKillBirthSystem(
    private val config: Config<WellConfig>,
) : DeathSystems.OnDeathSystem() {
    private val rules = WellBirthRules()

    override fun getQuery(): Query<EntityStore> =
        Query.and(DeathComponent.getComponentType(), NPCEntity.getComponentType()!!)

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val npc = store.getComponent(ref, NPCEntity.getComponentType()!!) ?: return
        val source = component.deathInfo?.source as? Damage.EntitySource ?: return
        val killerRef = source.ref
        val player = store.getComponent(killerRef, PlayerRef.getComponentType()) ?: return
        val transform = store.getComponent(ref, TransformComponent.getComponentType()) ?: return
        val position = transform.position
        val corpse = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
        val world = store.externalData.world
        val activeSection = world.chunkStore.getChunkSectionReferenceAtBlock(corpse.x, corpse.y, corpse.z) ?: return
        if (!CraftoryCore.isAreaActive(world.chunkStore.store, activeSection)) return
        val now = world.tick
        val birthState = world.chunkStore.store.getResource(WellBirthState.type)
        val cooldown = config.get().playerFlickerCooldownSeconds.coerceAtLeast(0) * 20L
        if (!WellBirthRules.cooldownElapsed(birthState.lastFlickerByPlayer[player.uuid], now, cooldown)) return
        if (!rules.killFlicker(config.get().killFlickerChance)) return
        val spawned =
            WellBirths.spawnNear(
                world,
                corpse,
                InsightRules.mobTemperament(npc.roleName),
                rules.flickerStrength(),
                rules.surfaceOffset(),
                Random,
            )
        if (spawned != null) birthState.lastFlickerByPlayer[player.uuid] = now
    }
}
