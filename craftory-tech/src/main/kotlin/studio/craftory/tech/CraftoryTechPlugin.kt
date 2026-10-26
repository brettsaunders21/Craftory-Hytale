package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.Universe
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.tech.command.SummonCommand
import studio.craftory.tech.machine.AnchorLifecycle
import studio.craftory.tech.machine.AnchorRefreshIndex
import studio.craftory.tech.machine.AnchorState
import studio.craftory.tech.machine.AnchorSystem
import studio.craftory.tech.machine.BoilerState
import studio.craftory.tech.machine.BurnerState
import studio.craftory.tech.machine.BurnerSystem
import studio.craftory.tech.machine.CanisterPage
import studio.craftory.tech.machine.CanisterPageRefreshSystem
import studio.craftory.tech.machine.ClawUseInteraction
import studio.craftory.tech.machine.CraftoryBlockUseInteraction
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.machine.CraftoryWrenchUseInteraction
import studio.craftory.tech.machine.CrankState
import studio.craftory.tech.machine.CrankSystem
import studio.craftory.tech.machine.DaubUseInteraction
import studio.craftory.tech.machine.EnergyHudState
import studio.craftory.tech.machine.GasbagState
import studio.craftory.tech.machine.GasbagSystem
import studio.craftory.tech.machine.GnasherState
import studio.craftory.tech.machine.GnasherSystem
import studio.craftory.tech.machine.GutterBoilerSystem
import studio.craftory.tech.machine.JunkPowerHudSystem
import studio.craftory.tech.machine.JunkPowerKickSystem
import studio.craftory.tech.machine.MachineClockLifecycle
import studio.craftory.tech.machine.MachineOwner
import studio.craftory.tech.machine.MachineOwnerPlacementSystem
import studio.craftory.tech.machine.OvenPower
import studio.craftory.tech.machine.ReceiptUseInteraction
import studio.craftory.tech.machine.ScorchOvenSystem
import studio.craftory.tech.machine.SelfTestRun
import studio.craftory.tech.machine.SelfTestSystem
import studio.craftory.tech.machine.SnatcherClawState
import studio.craftory.tech.machine.SnatcherClawSystem
import studio.craftory.tech.machine.TransportSelfTestRun
import studio.craftory.tech.machine.TransportSelfTestSystem
import studio.craftory.tech.notebook.NotebookPageUi
import studio.craftory.tech.notebook.NotebookPages
import studio.craftory.tech.notebook.NotebookTierProgression

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    private var crewKnowledgeSubscription: AutoCloseable? = null
    private var crewChangedSubscription: AutoCloseable? = null

    @Suppress("LongMethod")
    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        CraftoryCore.registerModule("CraftoryTech", pluginVersion)
        CraftoryCore.registerEnergyType(
            CraftoryIds.CRANK_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.BURNER_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.CANISTER_TYPE,
            MemberSpec(MemberSpec.STORAGE, 100_000, 400, 400, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.PIPE_TYPE,
            MemberSpec(MemberSpec.LINK, 0, 0, 0, 0, 1, lossPerMillion = 5_000),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.OVEN_TYPE,
            MemberSpec(MemberSpec.CONSUMER, 0, 30, 0, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.GNASHER_TYPE,
            MemberSpec(MemberSpec.CONSUMER, 0, 15, 0, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.GUTTER_BOILER_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.GASBAG_TYPE,
            MemberSpec(MemberSpec.STORAGE, 400_000, 400, 400, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.SNATCHER_CLAW_TYPE,
            MemberSpec(MemberSpec.CONSUMER, 0, 4, 0, 0, 1),
        )
        val chunkRegistry = getChunkStoreRegistry()
        CrankState.type =
            chunkRegistry.registerComponent(
                CrankState::class.java,
                CraftoryIds.CRANK_COMPONENT,
                CrankState.CODEC,
            )
        BurnerState.type =
            chunkRegistry.registerComponent(
                BurnerState::class.java,
                CraftoryIds.BURNER_COMPONENT,
                BurnerState.CODEC,
            )
        GnasherState.type =
            chunkRegistry.registerComponent(
                GnasherState::class.java,
                CraftoryIds.GNASHER_COMPONENT,
                GnasherState.CODEC,
            )
        OvenPower.type =
            chunkRegistry.registerComponent(
                OvenPower::class.java,
                CraftoryIds.OVEN_COMPONENT,
                OvenPower.CODEC,
            )
        BoilerState.type =
            chunkRegistry.registerComponent(
                BoilerState::class.java,
                CraftoryIds.BOILER_COMPONENT,
                BoilerState.CODEC,
            )
        GasbagState.type =
            chunkRegistry.registerComponent(
                GasbagState::class.java,
                CraftoryIds.GASBAG_COMPONENT,
                GasbagState.CODEC,
            )
        studio.craftory.tech.machine
            .registerBoilerFluidHandler()
        SnatcherClawState.type =
            chunkRegistry.registerComponent(
                SnatcherClawState::class.java,
                CraftoryIds.SNATCHER_CLAW_COMPONENT,
                SnatcherClawState.CODEC,
            )
        AnchorState.type =
            chunkRegistry.registerComponent(
                AnchorState::class.java,
                CraftoryIds.ANCHOR_COMPONENT,
                AnchorState.CODEC,
            )
        MachineOwner.type =
            chunkRegistry.registerComponent(
                MachineOwner::class.java,
                CraftoryIds.MACHINE_OWNER_COMPONENT,
                MachineOwner.CODEC,
            )
        CraftoryCore.registerInspectableComponent(CraftoryIds.CRANK_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, CrankState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.BURNER_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, BurnerState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.GNASHER_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, GnasherState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.OVEN_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, OvenPower.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.ANCHOR_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, AnchorState.type)
        }
        SelfTestRun.type = chunkRegistry.registerResource(SelfTestRun::class.java, ::SelfTestRun)
        TransportSelfTestRun.type =
            chunkRegistry.registerResource(TransportSelfTestRun::class.java, ::TransportSelfTestRun)
        AnchorRefreshIndex.type = chunkRegistry.registerResource(AnchorRefreshIndex::class.java, ::AnchorRefreshIndex)
        chunkRegistry.registerSystem(MachineClockLifecycle())
        chunkRegistry.registerSystem(AnchorLifecycle())
        chunkRegistry.registerSystem(CrankSystem())
        chunkRegistry.registerSystem(BurnerSystem())
        chunkRegistry.registerSystem(GnasherSystem())
        chunkRegistry.registerSystem(ScorchOvenSystem())
        chunkRegistry.registerSystem(GutterBoilerSystem())
        chunkRegistry.registerSystem(GasbagSystem())
        chunkRegistry.registerSystem(SnatcherClawSystem())
        chunkRegistry.registerSystem(AnchorSystem())
        chunkRegistry.registerSystem(SelfTestSystem())
        chunkRegistry.registerSystem(TransportSelfTestSystem())
        val entityRegistry = getEntityStoreRegistry()
        EnergyHudState.type =
            entityRegistry.registerComponent(
                EnergyHudState::class.java,
                CraftoryIds.HUD_STATE_COMPONENT,
                EnergyHudState.CODEC,
            )
        entityRegistry.registerSystem(JunkPowerHudSystem())
        entityRegistry.registerSystem(CanisterPageRefreshSystem())
        entityRegistry.registerSystem(JunkPowerKickSystem())
        entityRegistry.registerSystem(MachineOwnerPlacementSystem())
        OpenCustomUIInteraction.registerBlockEntityCustomPage(
            this,
            CanisterPage::class.java,
            CraftoryIds.CANISTER_PAGE,
            CanisterPage::tryCreate,
        )
        OpenCustomUIInteraction.registerSimple(
            this,
            NotebookPageUi::class.java,
            CraftoryIds.NOTEBOOK_PAGE,
        ) { playerRef ->
            NotebookPageUi(playerRef, CraftoryCore.crewService())
        }
        getCodecRegistry(Interaction.CODEC)
            .register(
                CraftoryIds.BLOCK_USE_INTERACTION,
                CraftoryBlockUseInteraction::class.java,
                CraftoryBlockUseInteraction.CODEC,
            ).register(
                CraftoryIds.WRENCH_USE_INTERACTION,
                CraftoryWrenchUseInteraction::class.java,
                CraftoryWrenchUseInteraction.CODEC,
            ).register(
                CraftoryIds.RECEIPT_USE_INTERACTION,
                ReceiptUseInteraction::class.java,
                ReceiptUseInteraction.CODEC,
            ).register(
                CraftoryIds.CLAW_USE_INTERACTION,
                ClawUseInteraction::class.java,
                ClawUseInteraction.CODEC,
            ).register(
                CraftoryIds.DAUB_USE_INTERACTION,
                DaubUseInteraction::class.java,
                DaubUseInteraction.CODEC,
            )
        val crews = CraftoryCore.crewService()
        crewKnowledgeSubscription = crews.onKnowledgeGranted { crew, _ -> evaluateTier(crew) }
        crewChangedSubscription = crews.onCrewChanged(::evaluateTier)
        CraftoryCore.registerCommandHandler("summon", SummonCommand::execute)
        CraftoryCore.registerCommandHandler("selftest", CraftorySelfTest::execute)
        LOGGER.atInfo().log("Craftory Tech v%s loaded", pluginVersion)
    }

    override fun shutdown() {
        crewKnowledgeSubscription?.close()
        crewKnowledgeSubscription = null
        crewChangedSubscription?.close()
        crewChangedSubscription = null
    }

    private fun evaluateTier(crew: Crew) {
        val upgraded = NotebookTierProgression.evaluateTier(crew, CraftoryCore.crewService()) ?: return
        Universe
            .get()
            .getPlayers()
            .filter { it.uuid in upgraded.members }
            .forEach { playerRef ->
                playerRef.sendMessage(
                    Message
                        .translation("server.craftory.crew.tier-advanced")
                        .param("name", upgraded.name)
                        .param("tier", upgraded.tier),
                )
            }
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryTech")
    }
}
