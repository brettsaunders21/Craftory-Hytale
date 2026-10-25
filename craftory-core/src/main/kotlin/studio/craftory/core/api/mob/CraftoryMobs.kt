package studio.craftory.core.api.mob

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.npc.NPCPlugin
import com.hypixel.hytale.server.npc.entities.NPCEntity
import com.hypixel.hytale.server.spawning.ISpawnableWithModel
import com.hypixel.hytale.server.spawning.SpawningContext
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level

object CraftoryMobs {
    private val listeners = CopyOnWriteArrayList<(CraftoryMobKill) -> Unit>()
    private val aliases = ConcurrentHashMap<String, String>()

    fun spawn(
        world: World,
        roleId: String,
        position: Vector3d,
        home: BlockPos?,
        leashRadius: Double = 12.0,
    ): com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? {
        world.debugAssertInTickingThread()
        val resolvedRoleId = aliases[roleId] ?: roleId
        val npcPlugin = NPCPlugin.get()
        val ref = spawnRole(npcPlugin, world, resolvedRoleId, position) ?: return null
        val store = world.entityStore.store
        store.addComponent(
            ref,
            MobHome.type,
            MobHome(
                roleId = resolvedRoleId,
                hasHome = home != null,
                homeX = home?.x ?: 0,
                homeY = home?.y ?: 0,
                homeZ = home?.z ?: 0,
                leashRadius = leashRadius,
            ),
        )
        if (home != null) {
            NPCEntity.getComponentType()?.let { npcComponentType ->
                store.getComponent(ref, npcComponentType)?.setLeashPoint(home.leashPoint())
            }
        }
        return ref
    }

    private fun spawnRole(
        npcPlugin: NPCPlugin,
        world: World,
        roleId: String,
        position: Vector3d,
    ): com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? {
        val roleIndex = npcPlugin.getIndex(roleId)
        if (roleIndex < 0) return null
        val roleInfo = npcPlugin.getRoleBuilderInfo(roleIndex) ?: return null
        npcPlugin.forceValidation(roleIndex)
        if (!npcPlugin.testAndValidateRole(roleInfo)) return null
        val roleBuilder = npcPlugin.tryGetCachedValidRole(roleIndex) ?: return null
        if (!roleBuilder.isSpawnable) return null
        val spawnable = roleBuilder as? ISpawnableWithModel ?: return null
        val spawningContext = SpawningContext()
        if (!spawningContext.setSpawnable(spawnable)) return null
        val model = spawningContext.model ?: return null
        val boundingBox = model.boundingBox ?: return null
        val store = world.entityStore.store
        val spawnPosition = Vector3d(position).also { it.y -= boundingBox.min.y }
        val spawned =
            npcPlugin.spawnEntity(
                store,
                roleIndex,
                spawnPosition,
                Rotation3f(0f, 0f, 0f),
                model,
                null,
            ) ?: return null
        return spawned.left()
    }

    fun registerRoleAlias(
        alias: String,
        roleId: String,
    ) {
        require(alias.isNotBlank())
        require(roleId.isNotBlank())
        val previous = aliases.putIfAbsent(alias, roleId)
        check(previous == null || previous == roleId) { "Role alias already registered: $alias" }
    }

    fun registeredRoleAliases(): List<String> = aliases.keys.sorted()

    fun onCraftoryMobKilled(listener: (CraftoryMobKill) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun fireKilled(kill: CraftoryMobKill) {
        listeners.forEach { listener ->
            try {
                listener(kill)
            } catch (exception: Exception) {
                LOGGER.at(Level.SEVERE).log("Craftory mob kill listener failed: %s", exception.message)
            }
        }
    }

    private fun BlockPos.leashPoint(): Vector3d = Vector3d(x + 0.5, y + 0.5, z + 0.5)

    private val LOGGER = HytaleLogger.get("CraftoryCore")
}

data class CraftoryMobKill(
    val world: World,
    val ref: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>,
    val roleId: String,
    val home: BlockPos?,
    val position: Vector3d,
    val killer: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>?,
)
