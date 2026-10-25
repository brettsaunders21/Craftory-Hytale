package studio.craftory.tech.well

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.Config
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Temperament
import studio.craftory.tech.CraftoryIds
import studio.craftory.tech.well.JarActions
import studio.craftory.tech.well.WellConfig

internal class ResonanceHud(
    playerRef: PlayerRef,
) : CustomUIHud(playerRef, CraftoryIds.LENS_HUD, 30) {
    private var lastState: Pair<Int, Int>? = null

    override fun build(commandBuilder: UICommandBuilder) {
        commandBuilder.append("Craftory/Hud/ResonanceHud.ui")
    }

    fun render(
        locked: Int,
        remainingTicks: Long,
    ) {
        val state = locked to remainingTicks.toInt()
        if (state == lastState) return
        lastState = state
        update(
            false,
            UICommandBuilder()
                .set("#Progress.Text", Message.translation("server.craftory.scan.progress").param("ring", locked + 1)),
        )
    }
}

internal class InsightHud(
    playerRef: PlayerRef,
) : CustomUIHud(playerRef, CraftoryIds.INSIGHT_HUD, 20) {
    private var lastValues: List<Long>? = null

    override fun build(commandBuilder: UICommandBuilder) {
        commandBuilder.append("Craftory/Hud/InsightHud.ui")
    }

    fun render(insight: Map<Temperament, Long>) {
        val values = Temperament.entries.map { insight[it] ?: 0L }
        if (values == lastValues) return
        lastValues = values
        update(
            false,
            UICommandBuilder()
                .set(
                    "#Orbs.Text",
                    values
                        .take(4)
                        .mapIndexed { index, value -> "${Temperament.entries[index].tint} $value" }
                        .joinToString("  "),
                ).set(
                    "#Orbs2.Text",
                    values
                        .drop(4)
                        .mapIndexed { index, value -> "${Temperament.entries[index + 4].tint} $value" }
                        .joinToString("  "),
                ),
        )
    }
}

internal object WellHud {
    fun showResonance(
        playerRef: PlayerRef,
        player: Player,
        locked: Int,
        remainingTicks: Long,
    ) {
        val hud =
            (player.hudManager.getCustomHud(CraftoryIds.LENS_HUD) as? ResonanceHud)
                ?: ResonanceHud(playerRef).also { player.hudManager.addCustomHud(playerRef, it) }
        hud.render(locked, remainingTicks)
    }

    fun hideResonance(
        playerRef: PlayerRef,
        player: Player,
    ) {
        player.hudManager.removeCustomHud(playerRef, CraftoryIds.LENS_HUD)
    }

    fun showInsight(
        playerRef: PlayerRef,
        player: Player,
        crews: CrewService,
    ) {
        val crew = crews.crewOf(playerRef.uuid)
        val hud =
            (player.hudManager.getCustomHud(CraftoryIds.INSIGHT_HUD) as? InsightHud)
                ?: InsightHud(playerRef).also { player.hudManager.addCustomHud(playerRef, it) }
        hud.render(crew.insight)
    }
}

internal class LensScanSystem(
    private val config: Config<WellConfig>,
) : DelayedEntitySystem<EntityStore>(0.1f) {
    override fun getQuery(): Query<EntityStore> =
        Query.and(
            PlayerRef.getComponentType(),
            Player.getComponentType(),
        )

    override fun tick(
        dt: Float,
        systemIndex: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>,
    ) {
        val state = store.getResource(LensScanState.type)
        val world = store.externalData.world
        for (index in 0 until chunk.size()) {
            val playerRef = chunk.getComponent(index, PlayerRef.getComponentType())
            val player = chunk.getComponent(index, Player.getComponentType())
            if (playerRef != null && player != null) {
                updateSession(playerRef, player, state, world, store)
            }
        }
    }

    private fun updateSession(
        playerRef: PlayerRef,
        player: Player,
        state: LensScanState,
        world: World,
        store: Store<EntityStore>,
    ) {
        val session = state.sessions[playerRef.uuid] ?: return
        val remaining = config.get().scanTimeoutTicks.coerceAtLeast(1) - (world.tick - session.startTick)
        if (remaining <= 0) {
            LensScanner.finish(playerRef, player, session, false, world, store)
            state.sessions.remove(playerRef.uuid)
            WellHud.hideResonance(playerRef, player)
        } else {
            WellHud.showResonance(playerRef, player, session.lockTicks.size, remaining)
        }
    }
}

internal class CodexPageUi(
    playerRef: PlayerRef,
    private val crews: CrewService,
) : CustomUIPage(
        playerRef,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
    ) {
    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append("Craftory/Pages/Codex.ui")
        val crew = crews.crewOf(playerRef.uuid)
        val tier =
            listOf("Scrapjank", "Tinkered", "Sparkbound", "Glimmerforged", "Resonant", "Arcanum", "Ascendant")
                .getOrElse((crew.tier - 1).coerceIn(0, 6)) { "Scrapjank" }
        commandBuilder
            .set("#Title.Text", Message.translation("server.craftory.codex.title"))
            .set("#Tier.Text", Message.translation("server.craftory.codex.tier").param("tier", tier))
            .set(
                "#Insight.Text",
                if (crew.tier >= 3) {
                    Message.raw(
                        "Insight: " +
                            Temperament.entries.joinToString("  ") {
                                "${it.id}: ${crew.insight[it] ?: 0L}"
                            },
                    )
                } else {
                    Message.translation("server.craftory.tier.locked")
                },
            ).set(
                "#Temperaments.Text",
                if (crew.tier >= 3) {
                    Temperament.entries.joinToString("  ") { temperament ->
                        "${temperament.id}: ${if ("craftory:well/${temperament.id}" in crew.knowledge) "✓" else "○"}"
                    }
                } else {
                    "Reach Sparkbound tier to reveal discoveries."
                },
            ).set(
                "#Flicker.Text",
                Message.translation("server.craftory.codex.flicker").param(
                    "caught",
                    if (JarActions.FIRST_FLICKER_ID in crew.knowledge) "yes" else "no",
                ),
            )
        if (crew.tier < 2) playerRef.sendMessage(Message.translation("server.craftory.tier.locked"))
    }
}
