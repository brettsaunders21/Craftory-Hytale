package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellState
import studio.craftory.core.api.well.WellStrength
import kotlin.math.floor
import kotlin.random.Random

object WellBirths {
    fun forceStormcall(
        world: World,
        playerOrPos: BlockPos,
        random: Random = Random.Default,
    ): Ref<ChunkStore>? {
        world.debugAssertInTickingThread()
        val temperament = Temperament.entries[random.nextInt(Temperament.entries.size)]
        return CraftoryCore.wells().spawn(world, playerOrPos, temperament, WellStrength.STEADY, WellState.WILD)
    }

    internal fun spawnStormcall(
        world: World,
        position: BlockPos,
        rules: WellBirthRules,
        random: Random,
    ): Ref<ChunkStore>? {
        val offset = rules.surfaceOffset()
        val angle = random.nextDouble() * Math.PI * 2
        val target =
            BlockPos(
                position.x + (kotlin.math.cos(angle) * offset).toInt(),
                position.y,
                position.z + (kotlin.math.sin(angle) * offset).toInt(),
            )
        val strength = listOf(WellStrength.STEADY, WellStrength.ROARING, WellStrength.PRIMAL)[random.nextInt(3)]
        return CraftoryCore.wells().spawn(world, target, rules.stormTemperament(), strength, WellState.WILD)
    }
}

internal class WellBirthSystem(
    private val config: com.hypixel.hytale.server.core.util.Config<WellConfig>,
) : com.hypixel.hytale.component.system.tick.DelayedEntitySystem<EntityStore>(1.0f) {
    private val rules = WellBirthRules()

    override fun getQuery(): com.hypixel.hytale.component.query.Query<EntityStore> =
        com.hypixel.hytale.component.query.Query.and(
            PlayerRef.getComponentType(),
            com.hypixel.hytale.builtin.weather.components.WeatherTracker
                .getComponentType(),
            TransformComponent.getComponentType(),
        )

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: com.hypixel.hytale.component.ArchetypeChunk<EntityStore>,
        store: com.hypixel.hytale.component.Store<EntityStore>,
        commandBuffer: com.hypixel.hytale.component.CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        val settings = config.get()
        val birthState = world.chunkStore.store.getResource(WellBirthState.type)
        val weatherResource =
            store.getResource(
                com.hypixel.hytale.builtin.weather.resources.WeatherResource
                    .getResourceType(),
            )
        for (index in 0 until chunk.size()) {
            val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: continue
            val tracker =
                chunk.getComponent(
                    index,
                    com.hypixel.hytale.builtin.weather.components.WeatherTracker
                        .getComponentType(),
                )
                    ?: continue
            val transform = chunk.getComponent(index, TransformComponent.getComponentType()) ?: continue
            val position = transform.position
            val playerPos = BlockPos(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt())
            val activeSection = world.chunkStore.getChunkSectionReferenceAtBlock(playerPos.x, playerPos.y, playerPos.z)
            if (activeSection == null || !CraftoryCore.isAreaActive(world.chunkStore.store, activeSection)) continue
            val weatherIndex = weatherResource.getEffectiveWeatherIndex(tracker.weatherIndex)
            val weatherId =
                Weather
                    .getAssetMap()
                    .getAsset(weatherIndex)
                    ?.id
                    ?.lowercase()
                    .orEmpty()
            val now = world.tick
            val interval = settings.stormcallIntervalSeconds.coerceAtLeast(1) * 20L
            if (weatherId.contains("thunder") &&
                now - (birthState.lastStormcallByPlayer[player.uuid] ?: 0L) >= interval
            ) {
                birthState.lastStormcallByPlayer[player.uuid] = now
                val region = regionId(playerPos.x shr 9, playerPos.z shr 9)
                val regionLast = birthState.lastStormcallByRegion[region] ?: Long.MIN_VALUE
                if (
                    now - regionLast >= interval &&
                    rules.stormcall(settings.stormcallChance) &&
                    noStrongWellInRegion(world, playerPos)
                ) {
                    val spawned = WellBirths.spawnStormcall(world, playerPos, rules, Random)
                    if (spawned != null) {
                        birthState.lastStormcallByRegion[region] = now
                        pulseEffects(player, position, store)
                    }
                }
            } else if (
                (weatherId.contains("rain") || weatherId.contains("storm")) &&
                !weatherId.contains("thunder") &&
                rules.rainFlicker(settings.rainFlickerChance) &&
                now - (birthState.lastFlickerByPlayer[player.uuid] ?: Long.MIN_VALUE) >=
                settings.playerFlickerCooldownSeconds.coerceAtLeast(0) * 20L
            ) {
                birthState.lastFlickerByPlayer[player.uuid] = now
                val spawned =
                    CraftoryCore.wells().spawn(
                        world,
                        playerPos,
                        Temperament.TIDE,
                        rules.flickerStrength(),
                        WellState.WILD,
                    )
                if (spawned != null) pulseEffects(player, position, store)
            }
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
        store: com.hypixel.hytale.component.ComponentAccessor<EntityStore>,
    ) {
        ParticleUtil.spawnParticleEffect("Impact_Spark", position, store)
        SoundUtil.playSoundEvent2dToPlayer(
            player,
            "SFX_Interact",
            com.hypixel.hytale.protocol.SoundCategory.SFX,
        )
    }

    private fun regionId(
        x: Int,
        z: Int,
    ): Long = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)
}
