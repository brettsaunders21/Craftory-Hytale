package studio.craftory.tech.notebook

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.crew.CrewService

class NotebookPageUi(
    playerRef: PlayerRef,
    private val crews: CrewService,
) : CustomUIPage(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction) {
    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append("Craftory/Pages/Notebook.ui")
        val crew = crews.crewOf(playerRef.uuid)
        val members = crew.members.size
        commandBuilder
            .set("#Title.Text", Message.translation("server.craftory.notebook.title"))
            .set("#Crew.Text", Message.translation("server.craftory.notebook.crew").param("name", crew.name))
            .set("#Members.Text", Message.translation("server.craftory.notebook.members").param("count", members))
            .set(
                "#Progress.Text",
                Message
                    .translation("server.craftory.notebook.progress")
                    .param("found", crew.knowledge.count { it in NotebookPages.ids })
                    .param("total", NotebookPages.pages.size),
            )
        NotebookPages.pages.forEachIndexed { index, page ->
            val prefix = "#Page$index"
            commandBuilder
                .set("$prefix.Name.Text", Message.translation(page.nameKey))
                .set(
                    "$prefix.Status.Text",
                    Message.translation(
                        if (page.id in crew.knowledge) {
                            "server.craftory.notebook.found"
                        } else {
                            "server.craftory.notebook.missing"
                        },
                    ),
                ).set("$prefix.Source.Text", Message.translation(page.sourceKey))
        }
    }
}
