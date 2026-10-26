package studio.craftory.tech.well

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import java.util.concurrent.ThreadLocalRandom

internal fun spawnOnSchedule(
    world: World,
    position: BlockPos,
) {
    val settings = WellAfflictionRuntimeConfig.settings
    val key = WellKey(world.worldConfig.uuid, position.pack())
    if (settings.riftmothIntervalSeconds > 0) {
        val last = lastMothAttempt.putIfAbsent(key, world.tick) ?: world.tick
        if (world.tick - last >= settings.riftmothIntervalSeconds * TICKS_PER_SECOND) {
            lastMothAttempt[key] = world.tick
            if (ThreadLocalRandom.current().nextInt(100) < settings.riftmothChancePercent) {
                val count = ThreadLocalRandom.current().nextInt(1, 3)
                world.execute { spawnFollowers(world, position, WellAfflictionFollower.MOTH, count) }
            }
        }
    }
    schedulePoacherRaid(world, position, key, settings.poacherRaidIntervalSeconds, settings.poacherRaidChancePercent)
    if (!wellIsCorrupted(world, position) || settings.voidSpawnCap == 0) return
    val afflictionRef = wellRef(world, position) ?: return
    val state = world.chunkStore.store.getComponent(afflictionRef, WellAffliction.type) ?: return
    if (state.voidIds.size >= settings.voidSpawnCap) return
    val last = lastVoidAttempt.putIfAbsent(key, world.tick) ?: world.tick
    if (world.tick - last < settings.voidSpawnIntervalSeconds * TICKS_PER_SECOND) return
    lastVoidAttempt[key] = world.tick
    world.execute { spawnFollowers(world, position, WellAfflictionFollower.VOID, 1) }
}

private fun schedulePoacherRaid(
    world: World,
    position: BlockPos,
    key: WellKey,
    intervalSeconds: Int,
    chancePercent: Int,
) {
    if (intervalSeconds <= 0) return
    val currentRef = wellRef(world, position) ?: return
    val current = world.chunkStore.store.getComponent(currentRef, WellAffliction.type) ?: return
    if (current.raidNpcIds.isNotEmpty()) return
    val lastRaid = lastRaidAttempt.putIfAbsent(key, world.tick) ?: world.tick
    if (world.tick - lastRaid < intervalSeconds * TICKS_PER_SECOND) return
    lastRaidAttempt[key] = world.tick
    if (ThreadLocalRandom.current().nextInt(100) < chancePercent) {
        world.execute { spawnRaid(world, position) }
    }
}
