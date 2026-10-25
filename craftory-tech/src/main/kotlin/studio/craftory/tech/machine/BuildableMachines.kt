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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.notebook.NotebookPage
import studio.craftory.tech.notebook.NotebookPages
import java.util.UUID

internal object BuildableMachines {
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
        val page = BuildableMachines.missingPage(itemId, player.uuid, CraftoryCore.crewService()) ?: return
        event.isCancelled = true
        player.sendMessage(
            Message
                .translation("server.craftory.craft.needs-page")
                .param("machine", Message.translation("server.items.$itemId.name"))
                .param("page", Message.translation(page.nameKey)),
        )
    }
}

internal object JunkHeartKnowledge {
    fun onFormed(
        store: Store<ChunkStore>,
        controllerRef: Ref<ChunkStore>,
        controller: MultiblockController,
    ) {
        if (controller.templateId != CraftoryIds.JUNK_HEART_TEMPLATE) return
        val owner = store.getComponent(controllerRef, MachineOwner.type)?.placerUuid ?: return
        CraftoryCore.crewService().grantKnowledge(owner, CraftoryIds.JUNK_HEART_KNOWLEDGE)
    }
}
