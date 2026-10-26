package studio.craftory.tech.machine

import com.hypixel.hytale.protocol.ChangeVelocityType
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import org.joml.Vector3d

internal object GutterBoilerEffects {
    fun vent(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ) {
        world.execute { spawnSteam(world, x, y, z) }
    }

    fun pop(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ) {
        world.execute {
            spawnSteam(world, x, y, z)
            applyPopKnockback(world, x, y, z)
        }
    }

    private fun spawnSteam(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ) {
        val store = world.entityStore.store
        val position = Vector3d(x + 0.5, y + 1.0, z + 0.5)
        ParticleUtil.spawnParticleEffect(STEAM_PARTICLE_ID, position, store)
        BOILER_WHISTLE_SOUND_ID?.let { soundId ->
            val soundIndex = SoundEvent.getAssetMap().getIndex(soundId)
            if (soundIndex >= 0) {
                SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, position, store)
            }
        }
    }

    private fun applyPopKnockback(
        world: World,
        x: Int,
        y: Int,
        z: Int,
    ) {
        val store = world.entityStore.store
        val originX = x + 0.5
        val originY = y + 0.5
        val originZ = z + 0.5
        for (player in world.playerRefs) {
            applyPopKnockback(store, player, originX, originY, originZ)
        }
    }

    private fun applyPopKnockback(
        store: com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
        player: com.hypixel.hytale.server.core.universe.PlayerRef,
        originX: Double,
        originY: Double,
        originZ: Double,
    ) {
        val ref = player.reference?.takeIf { it.isValid && it.store === store } ?: return
        val point = store.getComponent(ref, TransformComponent.getComponentType())?.position ?: return
        val dx = point.x - originX
        val dy = point.y - originY
        val dz = point.z - originZ
        if (dx * dx + dy * dy + dz * dz > PLAYER_RADIUS_SQUARED) return
        val velocity = GutterBoilerRules.popKnockback(originX, originZ, point.x, point.z)
        val knockback = store.ensureAndGetComponent(ref, KnockbackComponent.getComponentType())
        knockback.setVelocity(Vector3d(velocity.x, velocity.y, velocity.z))
        knockback.setVelocityType(ChangeVelocityType.Set)
        knockback.setVelocityConfig(null)
        knockback.setDuration(0.0F)
        knockback.setTimer(0.0F)
    }

    private const val STEAM_PARTICLE_ID = "Craftory_Steam"
    private val BOILER_WHISTLE_SOUND_ID: String? = null
    private const val PLAYER_RADIUS_SQUARED = 16.0
}
