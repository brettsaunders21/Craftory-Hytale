package studio.craftory.core.api.mob

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.RefChangeSystem
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.entities.NPCEntity
import org.joml.Vector3d

internal class CraftoryMobDeathSystem : RefChangeSystem<EntityStore, DeathComponent>() {
    override fun getQuery(): Query<EntityStore> = Query.and(MobHome.type)

    override fun componentType() = DeathComponent.getComponentType()

    override fun onComponentAdded(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        fireKilled(ref, component, store, commandBuffer)
    }

    override fun onComponentSet(
        ref: Ref<EntityStore>,
        oldComponent: DeathComponent?,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) = Unit

    override fun onComponentRemoved(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) = Unit

    private fun fireKilled(
        ref: Ref<EntityStore>,
        component: DeathComponent,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val mobHome = commandBuffer.getComponent(ref, MobHome.type) ?: return
        val transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType())
        val position = transform?.position?.let(::Vector3d) ?: Vector3d()
        val source = component.deathInfo?.source as? Damage.EntitySource
        val killer = source?.ref?.takeIf { it.isValid }
        CraftoryMobs.fireKilled(
            CraftoryMobKill(
                store.externalData.world,
                ref,
                mobHome.roleId,
                mobHome.home,
                position,
                killer,
            ),
        )
    }
}

internal class MobLeashSystem : EntityTickingSystem<EntityStore>() {
    override fun getQuery(): Query<EntityStore> =
        Query.and(
            MobHome.type,
            TransformComponent.getComponentType(),
            requireNotNull(NPCEntity.getComponentType()),
        )

    override fun isParallel(
        systemIndex: Int,
        archetypeChunkSize: Int,
    ): Boolean = false

    override fun tick(
        dt: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val world = store.externalData.world
        if ((world.tick + index.toLong()) % LEASH_REFRESH_TICKS != 0L) return
        val mobHome = chunk.getComponent(index, MobHome.type) ?: return
        val home = mobHome.home ?: return
        val npcComponentType = NPCEntity.getComponentType() ?: return
        val npc = chunk.getComponent(index, npcComponentType) ?: return
        val target = Vector3d(home.x + 0.5, home.y + 0.5, home.z + 0.5)
        npc.setLeashPoint(target)
    }

    private companion object {
        const val LEASH_REFRESH_TICKS = 20L
    }
}
