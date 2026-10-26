package studio.craftory.core.api.mob

import com.hypixel.hytale.component.Holder
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.asset.type.model.config.Model
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
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

    /**
     * Spawns a Craftory NPC at [position].
     *
     * Returns null for an unknown role or a target section that is not entity-ticking. Hytale stores an entity
     * added to a non-ticking section in that chunk; the mob's home and leash point are attached before insertion
     * so they are persisted with it.
     */
    fun spawn(
        world: World,
        roleId: String,
        position: Vector3d,
        home: BlockPos?,
    ): com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? {
        world.debugAssertInTickingThread()
        val resolvedRoleId = aliases[roleId] ?: roleId
        val npcPlugin = NPCPlugin.get()
        return spawnRole(npcPlugin, world, resolvedRoleId, position, home)
    }

    private fun spawnRole(
        npcPlugin: NPCPlugin,
        world: World,
        roleId: String,
        position: Vector3d,
        home: BlockPos?,
    ): com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? {
        val spawnDetails = resolveSpawnDetails(npcPlugin, roleId) ?: return null
        val store = world.entityStore.store
        val spawnPosition = Vector3d(position).also { it.y -= spawnDetails.minY }
        val spawned =
            npcPlugin.spawnEntity(
                store,
                spawnDetails.roleIndex,
                spawnPosition,
                Rotation3f(0f, 0f, 0f),
                spawnDetails.model,
                { npc, holder, _ -> initializeMobBeforeAdd(npc, holder, roleId, home) },
                null,
            ) ?: return null
        return spawned.left()
    }

    private fun resolveSpawnDetails(
        npcPlugin: NPCPlugin,
        roleId: String,
    ): SpawnDetails? {
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
        return SpawnDetails(roleIndex, model, boundingBox.min.y)
    }

    private fun initializeMobBeforeAdd(
        npc: NPCEntity,
        holder: Holder<EntityStore>,
        roleId: String,
        home: BlockPos?,
    ) {
        holder.addComponent(
            MobHome.type,
            MobHome(
                roleId = roleId,
                hasHome = home != null,
                homeX = home?.x ?: 0,
                homeY = home?.y ?: 0,
                homeZ = home?.z ?: 0,
            ),
        )
        home?.let { npc.setLeashPoint(it.leashPoint()) }
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

    private data class SpawnDetails(
        val roleIndex: Int,
        val model: Model,
        val minY: Double,
    )

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
