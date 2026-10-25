package studio.craftory.tech.notebook

import studio.craftory.tech.CraftoryIds

data class NotebookPage(
    val id: String,
    val nameKey: String,
    val sourceKey: String,
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
                CraftoryIds.BURNER_TYPE,
            ),
            NotebookPage(
                "craftory:page/crank",
                "server.craftory.notebook.page.crank",
                "server.craftory.notebook.source.machines",
                CraftoryIds.CRANK_TYPE,
            ),
            NotebookPage(
                "craftory:page/canister",
                "server.craftory.notebook.page.canister",
                "server.craftory.notebook.source.machines",
                CraftoryIds.CANISTER_TYPE,
            ),
            NotebookPage(
                "craftory:page/gutterpipe",
                "server.craftory.notebook.page.gutterpipe",
                "server.craftory.notebook.source.machines",
                CraftoryIds.PIPE_TYPE,
            ),
            NotebookPage(
                "craftory:page/gnasher",
                "server.craftory.notebook.page.gnasher",
                "server.craftory.notebook.source.machines",
                CraftoryIds.GNASHER_TYPE,
            ),
            NotebookPage(
                "craftory:page/oven",
                "server.craftory.notebook.page.oven",
                "server.craftory.notebook.source.machines",
                CraftoryIds.OVEN_TYPE,
            ),
            NotebookPage(
                DELIVERY_PAGE_ID,
                "server.craftory.notebook.page.delivery",
                "server.craftory.notebook.source.delivery",
            ),
            NotebookPage(
                SCRAP_RUSH_PAGE_ID,
                "server.craftory.notebook.page.scrap-rush",
                "server.craftory.notebook.source.scrap-rush",
            ),
        )

    val ids: Set<String> = pages.mapTo(linkedSetOf(), NotebookPage::id)

    fun forMachineType(typeId: String): NotebookPage? = pages.firstOrNull { it.machineTypeId == typeId }
}
