package studio.craftory.tech.machine

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPage
import studio.craftory.tech.notebook.NotebookPages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object BuildableMachines {
    val requiredTierByItem =
        mapOf(
            CraftoryIds.GUTTER_BOILER to 2,
            CraftoryIds.GASBAG to 2,
            CraftoryIds.SNATCHER_CLAW to 2,
            CraftoryIds.HURL_TUBE to 2,
            CraftoryIds.HURL_STATION to 2,
            CraftoryIds.HURL_CANNON to 2,
            CraftoryIds.TETHER_STAKE to 3,
            CraftoryIds.CRACKLE_COIL to 3,
            CraftoryIds.RESONATOR to 3,
            CraftoryIds.WELL_CRADLE to 3,
            CraftoryIds.GLIMMER_LENS to 3,
            CraftoryIds.CODEX to 3,
        )

    private val machineTypeByItem =
        mapOf(
            CraftoryIds.CRANK to CraftoryIds.CRANK_TYPE,
            CraftoryIds.BURNER to CraftoryIds.BURNER_TYPE,
            CraftoryIds.CANISTER to CraftoryIds.CANISTER_TYPE,
            CraftoryIds.PIPE to CraftoryIds.PIPE_TYPE,
            CraftoryIds.GNASHER_PIT to CraftoryIds.GNASHER_TYPE,
            CraftoryIds.SCORCH_OVEN to CraftoryIds.OVEN_TYPE,
        )

    val itemIds: Set<String> get() = machineTypeByItem.keys

    fun pageFor(itemId: String): NotebookPage? = machineTypeByItem[itemId]?.let(NotebookPages::forMachineType)

    fun missingTier(
        itemId: String,
        crewTier: Int,
    ): Int? = requiredTierByItem[itemId]?.takeIf { crewTier < it }

    /** The page a player's crew still needs before crafting [itemId], or null when crafting is allowed. */
    fun missingPage(
        itemId: String,
        playerUuid: UUID,
        crews: CrewService,
    ): NotebookPage? {
        val page = pageFor(itemId) ?: return null
        return if (crews.hasKnowledge(playerUuid, page.id)) null else page
    }
}

internal class BuildableMachineCraftGate :
    EntityEventSystem<EntityStore, CraftRecipeEvent.Pre>(CraftRecipeEvent.Pre::class.java) {
    override fun getQuery(): Query<EntityStore> = Query.and(PlayerRef.getComponentType())

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
        event: CraftRecipeEvent.Pre,
    ) {
        if (event.isCancelled) return
        val itemId = event.craftedRecipe.primaryOutput?.itemId ?: return
        val player = chunk.getComponent(index, PlayerRef.getComponentType()) ?: return
        val crews = CraftoryCore.crewService()
        val missingTier =
            if (itemId in BuildableMachines.requiredTierByItem) {
                BuildableMachines.missingTier(itemId, crews.tierOf(player.uuid))
            } else {
                null
            }
        val page = BuildableMachines.missingPage(itemId, player.uuid, crews)
        if (missingTier == null && page == null) return
        event.isCancelled = true
        if (missingTier != null) {
            player.sendMessage(
                Message
                    .translation("server.craftory.craft.needs-tier")
                    .param("item", Message.translation("server.items.$itemId.name"))
                    .param("tier", missingTier),
            )
        } else {
            player.sendMessage(
                Message
                    .translation("server.craftory.craft.needs-page")
                    .param("machine", Message.translation("server.items.$itemId.name"))
                    .param("page", Message.translation(checkNotNull(page).nameKey)),
            )
        }
    }
}

internal object JunkHeartKnowledge {
    /** Selftest hearts (keyed by world and controller position) grant into a throwaway crew service. */
    private val testCrews = ConcurrentHashMap<String, CrewService>()

    fun redirectForTest(
        world: World,
        position: BlockPos,
        crews: CrewService,
    ) {
        testCrews[key(world, position)] = crews
    }

    fun clearTestRedirect(
        world: World,
        position: BlockPos,
    ) {
        testCrews.remove(key(world, position))
    }

    private fun key(
        world: World,
        position: BlockPos,
    ) = "${world.name}@${position.pack()}"

    fun onFormed(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        controller: MultiblockController,
    ) {
        if (controller.templateId != CraftoryIds.JUNK_HEART_TEMPLATE) return
        val owner = store.getComponent(controllerRef, MachineOwner.type)?.placerUuid ?: return
        val position = CraftoryCore.multiblocks().controllerPosition(store, controllerRef)
        val crews = position?.let { testCrews[key(store.externalData.world, it)] } ?: CraftoryCore.crewService()
        crews.grantKnowledge(owner, CraftoryIds.JUNK_HEART_KNOWLEDGE)
    }
}
