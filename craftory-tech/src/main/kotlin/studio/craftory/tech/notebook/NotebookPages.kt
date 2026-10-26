package studio.craftory.tech.notebook

import studio.craftory.tech.CraftoryIds

data class NotebookPage(
    val id: String,
    val nameKey: String,
    val sourceKey: String,
    val iconPath: String,
    val unlockKey: String,
    val machineTypeId: String? = null,
)

object NotebookPages {
    const val DELIVERY_PAGE_ID = "craftory:page/job/grizzlecog_delivery"
    const val SCRAP_RUSH_PAGE_ID = "craftory:page/job/scrap_rush"

    val pages =
        listOf(
            NotebookPage(
                "craftory:page/burner",
                "server.craftory.notebook.page.burner",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Boomshroom_Burner.png",
                "server.craftory.notebook.unlock.burner",
                CraftoryIds.BURNER_TYPE,
            ),
            NotebookPage(
                "craftory:page/crank",
                "server.craftory.notebook.page.crank",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Crank_A_Fizz.png",
                "server.craftory.notebook.unlock.crank",
                CraftoryIds.CRANK_TYPE,
            ),
            NotebookPage(
                "craftory:page/canister",
                "server.craftory.notebook.page.canister",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Fizz_Canister.png",
                "server.craftory.notebook.unlock.canister",
                CraftoryIds.CANISTER_TYPE,
            ),
            NotebookPage(
                "craftory:page/gutterpipe",
                "server.craftory.notebook.page.gutterpipe",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Gutterpipe.png",
                "server.craftory.notebook.unlock.gutterpipe",
                CraftoryIds.PIPE_TYPE,
            ),
            NotebookPage(
                "craftory:page/gnasher",
                "server.craftory.notebook.page.gnasher",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Gnasher_Pit.png",
                "server.craftory.notebook.unlock.gnasher",
                CraftoryIds.GNASHER_TYPE,
            ),
            NotebookPage(
                "craftory:page/oven",
                "server.craftory.notebook.page.oven",
                "server.craftory.notebook.source.machines",
                "Icons/ItemsGenerated/Craftory/Craftory_Scorch_Oven.png",
                "server.craftory.notebook.unlock.oven",
                CraftoryIds.OVEN_TYPE,
            ),
            NotebookPage(
                DELIVERY_PAGE_ID,
                "server.craftory.notebook.page.delivery",
                "server.craftory.notebook.source.delivery",
                "Icons/ItemsGenerated/Craftory/Craftory_Tinkers_Notebook.png",
                "server.craftory.notebook.unlock.delivery",
            ),
            NotebookPage(
                SCRAP_RUSH_PAGE_ID,
                "server.craftory.notebook.page.scrap-rush",
                "server.craftory.notebook.source.scrap-rush",
                "Icons/ItemsGenerated/Craftory/Craftory_Scrap_Plating.png",
                "server.craftory.notebook.unlock.scrap-rush",
            ),
        )

    val ids: Set<String> = pages.mapTo(linkedSetOf(), NotebookPage::id)

    fun forMachineType(typeId: String): NotebookPage? = pages.firstOrNull { it.machineTypeId == typeId }
}
