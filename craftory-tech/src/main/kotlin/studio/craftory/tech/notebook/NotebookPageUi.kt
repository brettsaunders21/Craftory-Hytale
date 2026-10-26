package studio.craftory.tech.notebook

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.crew.CrewService
import studio.craftory.tech.CraftoryIds
import com.hypixel.hytale.server.core.ui.builder.EventData as UiEventData

class NotebookPageUi(
    playerRef: PlayerRef,
    private val crews: CrewService,
) : InteractiveCustomUIPage<NotebookPageUi.EventData>(
        playerRef,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
        EventData.CODEC,
    ) {
    private var selectedPageId: String? = null
    private var activeTab = Tab.PAGES

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        val crew = crews.crewOf(playerRef.uuid)
        selectedPageId =
            selectedPageId
                ?.takeIf { pageId -> NotebookPages.pages.any { it.id == pageId } }
                ?: NotebookPages.pages.firstOrNull { it.id !in crew.knowledge }?.id
                ?: NotebookPages.pages.first().id

        commandBuilder
            .append("Craftory/Pages/Notebook.ui")
            .set("#TitleLabel.Text", Message.translation("server.craftory.notebook.title"))
            .set("#CrewName.Text", Message.translation("server.craftory.notebook.crew").param("name", crew.name))
            .set("#PagesTab.Text", Message.translation("server.craftory.notebook.tab.pages"))
            .set("#NextStepsTab.Text", Message.translation("server.craftory.notebook.tab.next-steps"))
            .set("#CloseButton.Text", Message.translation("server.craftory.notebook.close"))

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PagesTab",
            UiEventData.of("Action", "Pages"),
        )
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#NextStepsTab",
            UiEventData.of("Action", "NextSteps"),
        )
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            UiEventData.of("Action", "Close"),
        )

        NotebookPages.pages.forEachIndexed { index, page ->
            val tile = "#IconList[$index]"
            val found = page.id in crew.knowledge
            commandBuilder
                .append("#IconList", "Craftory/Pages/NotebookTile.ui")
                .set("$tile #Icon.AssetPath", page.iconPath)
                .set("$tile #TileName.Text", Message.translation(page.nameKey))
                .set("$tile #EmptyBackground.Visible", !found)
                .set("$tile #LockedShade.Visible", !found)
                .set("$tile #FoundMark.Visible", found)
                .set("$tile #ButtonSelected.Visible", page.id == selectedPageId)
                .set("$tile #ButtonNotSelected.Visible", page.id != selectedPageId)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$tile #ButtonSelected",
                UiEventData.of("Action", "SelectPage").append("PageId", page.id),
            )
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$tile #ButtonNotSelected",
                UiEventData.of("Action", "SelectPage").append("PageId", page.id),
            )
        }

        renderPageDetails(commandBuilder, crew, selectedPage())
        renderFooter(commandBuilder, crew)
        renderGuide(commandBuilder, crew)
        setTab(commandBuilder, activeTab)
    }

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: EventData,
    ) {
        when (data.action) {
            "Close" -> {
                close()
            }

            "Pages" -> {
                activeTab = Tab.PAGES
                rebuild()
            }

            "NextSteps" -> {
                activeTab = Tab.NEXT_STEPS
                rebuild()
            }

            "SelectPage" -> {
                if (NotebookPages.pages.none { it.id == data.pageId }) return
                selectedPageId = data.pageId
                rebuild()
            }
        }
    }

    private fun selectedPage(): NotebookPage =
        NotebookPages.pages.firstOrNull { it.id == selectedPageId } ?: NotebookPages.pages.first()

    private fun renderPageDetails(
        commands: UICommandBuilder,
        crew: Crew,
        page: NotebookPage,
    ) {
        val found = page.id in crew.knowledge
        commands
            .set("#PageName.Text", Message.translation(page.nameKey))
            .set("#PageIcon.AssetPath", page.iconPath)
            .set("#PageStatusFound.Visible", found)
            .set("#PageStatusMissing.Visible", !found)
            .set("#PageStatusFound.Text", Message.translation("server.craftory.notebook.found"))
            .set("#PageStatusMissing.Text", Message.translation("server.craftory.notebook.missing"))
            .set("#PageUnlocks.Text", Message.translation(page.unlockKey))
            .set("#PageSource.Text", Message.translation(page.sourceKey))
    }

    private fun renderFooter(
        commands: UICommandBuilder,
        crew: Crew,
    ) {
        val found = crew.knowledge.count { it in NotebookPages.ids }
        val progress = found.toFloat() / NotebookPages.pages.size
        commands
            .set("#PagesProgressBar.Value", progress)
            .set("#PagesProgressBarTexture.Value", progress)
            .set(
                "#PageCount.Text",
                Message
                    .translation("server.craftory.notebook.progress")
                    .param("found", found)
                    .param("total", NotebookPages.pages.size),
            ).set("#TierLabel.Text", Message.translation(NotebookGuide.tierTitleKey(crew.tier)))
            .set(
                "#Heart.Text",
                Message.translation(
                    if (CraftoryIds.JUNK_HEART_KNOWLEDGE in crew.knowledge) {
                        "server.craftory.notebook.heart.built"
                    } else {
                        "server.craftory.notebook.heart.missing"
                    },
                ),
            )
    }

    private fun renderGuide(
        commands: UICommandBuilder,
        crew: Crew,
    ) {
        val steps = NotebookGuide.stepsFor(crew)
        val nextStep = steps.firstOrNull(NotebookGuideStep::isNext)
        commands
            .set("#GuideTitle.Text", Message.translation(NotebookGuide.tierGuideKey(crew.tier)))
            .set("#NextBanner.Visible", nextStep != null)
            .set(
                "#NextText.TextSpans",
                nextStep?.let {
                    Message
                        .translation("server.craftory.notebook.guide.next")
                        .param("step", Message.translation(it.titleKey))
                } ?: Message.raw(""),
            ).set("#GuideNextBanner.Visible", nextStep != null)
            .set(
                "#GuideNextText.TextSpans",
                nextStep?.let {
                    Message
                        .translation("server.craftory.notebook.guide.next")
                        .param("step", Message.translation(it.titleKey))
                } ?: Message.raw(""),
            )

        steps.forEachIndexed { index, step ->
            val row = "#StepList[$index]"
            commands
                .append("#StepList", "Craftory/Pages/NotebookGuideStep.ui")
                .set("$row #StepTitle.Text", Message.translation(step.titleKey))
                .set("$row #StepHint.Text", Message.translation(step.hintKey))
                .set("$row #DefaultBackground.Visible", !step.isNext)
                .set("$row #SelectedBackground.Visible", step.isNext)
                .set("$row #NextTag.Visible", step.isNext)
                .set("$row #NextTag.Text", Message.translation("server.craftory.notebook.guide.next-tag"))
                .set("$row #StatusIcon.Visible", step.completion != null)
            if (step.completion != null) {
                commands.set(
                    "$row #StatusIcon.AssetPath",
                    if (step.completion) {
                        "UI/Custom/Pages/Memories/Checkmark.png"
                    } else {
                        "UI/Custom/Pages/Memories/Tiles/TileEmpty.png"
                    },
                )
            }
        }
    }

    private fun setTab(
        commands: UICommandBuilder,
        tab: Tab,
    ) {
        commands
            .set("#PagesView.Visible", tab == Tab.PAGES)
            .set("#GuideView.Visible", tab == Tab.NEXT_STEPS)
            .set("#PagesTab.Disabled", tab == Tab.PAGES)
            .set("#NextStepsTab.Disabled", tab == Tab.NEXT_STEPS)
    }

    private enum class Tab {
        PAGES,
        NEXT_STEPS,
    }

    class EventData {
        var action: String = ""
        var pageId: String = ""

        companion object {
            val CODEC: BuilderCodec<EventData> =
                BuilderCodec
                    .builder(EventData::class.java, ::EventData)
                    .append(KeyedCodec("Action", Codec.STRING), { data, value -> data.action = value }, { it.action })
                    .add()
                    .append(KeyedCodec("PageId", Codec.STRING), { data, value -> data.pageId = value }, { it.pageId })
                    .add()
                    .build()
        }
    }
}
