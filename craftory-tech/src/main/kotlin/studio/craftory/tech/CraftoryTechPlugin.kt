package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.util.Config
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.Crew
import studio.craftory.core.api.energy.CraftoryEnergyEventsApi
import studio.craftory.core.api.energy.MachineEnergyCondition
import studio.craftory.core.api.energy.MachineEnergyConditionListener
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.core.api.mob.CraftoryMobs
import studio.craftory.core.api.multiblock.MultiblockController
import studio.craftory.tech.command.ScrapRushCommand
import studio.craftory.tech.command.SummonCommand
import studio.craftory.tech.machine.AnchorLifecycle
import studio.craftory.tech.machine.AnchorRefreshIndex
import studio.craftory.tech.machine.AnchorState
import studio.craftory.tech.machine.AnchorSystem
import studio.craftory.tech.machine.AssemblyViewIndex
import studio.craftory.tech.machine.BodgeUseInteraction
import studio.craftory.tech.machine.BuildableMachineCraftGate
import studio.craftory.tech.machine.BurnerState
import studio.craftory.tech.machine.BurnerSystem
import studio.craftory.tech.machine.CanisterPage
import studio.craftory.tech.machine.CanisterPageRefreshSystem
import studio.craftory.tech.machine.ClawUseInteraction
import studio.craftory.tech.machine.CraftoryBlockUseInteraction
import studio.craftory.tech.machine.CraftoryHammerUseInteraction
import studio.craftory.tech.machine.CraftoryOvenUseInteraction
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.machine.CraftoryWrenchUseInteraction
import studio.craftory.tech.machine.CrankState
import studio.craftory.tech.machine.CrankSystem
import studio.craftory.tech.machine.DaubUseInteraction
import studio.craftory.tech.machine.EnergyHudState
import studio.craftory.tech.machine.GhostPreviewLifecycle
import studio.craftory.tech.machine.GnasherState
import studio.craftory.tech.machine.GnasherSystem
import studio.craftory.tech.machine.JankSystem
import studio.craftory.tech.machine.JunkHeartKnowledge
import studio.craftory.tech.machine.JunkHeartLifecycle
import studio.craftory.tech.machine.JunkHeartState
import studio.craftory.tech.machine.JunkHeartSystem
import studio.craftory.tech.machine.JunkHeartTemplate
import studio.craftory.tech.machine.JunkPowerHudSystem
import studio.craftory.tech.machine.JunkPowerKickSystem
import studio.craftory.tech.machine.MachineClockLifecycle
import studio.craftory.tech.machine.MachineOwner
import studio.craftory.tech.machine.MachineOwnerPlacementSystem
import studio.craftory.tech.machine.MultiblockAssemblySystem
import studio.craftory.tech.machine.OvenPower
import studio.craftory.tech.machine.RattlewheelState
import studio.craftory.tech.machine.RattlewheelSystem
import studio.craftory.tech.machine.ReceiptUseInteraction
import studio.craftory.tech.machine.ScorchOvenSystem
import studio.craftory.tech.machine.ScrapRushRaid
import studio.craftory.tech.machine.ScrapRushSystem
import studio.craftory.tech.machine.SelfTestRun
import studio.craftory.tech.machine.SelfTestSystem
import studio.craftory.tech.machine.ShroomBedState
import studio.craftory.tech.machine.ShroomBedSystem
import studio.craftory.tech.machine.SnatcherClawState
import studio.craftory.tech.machine.SnatcherClawSystem
import studio.craftory.tech.machine.TransportSelfTestRun
import studio.craftory.tech.machine.TransportSelfTestSystem
import studio.craftory.tech.machine.jank.BodgeSlots
import studio.craftory.tech.machine.jank.Jank
import studio.craftory.tech.mob.RiftmothDrainSystem
import studio.craftory.tech.notebook.NotebookPageUi
import studio.craftory.tech.notebook.NotebookPages
import studio.craftory.tech.notebook.NotebookTierProgression
import studio.craftory.tech.transport.HurlConfig
import studio.craftory.tech.transport.HurlCounters
import studio.craftory.tech.transport.HurlFallDamageSystem
import studio.craftory.tech.transport.HurlFallGuard
import studio.craftory.tech.transport.HurlRide
import studio.craftory.tech.transport.HurlRideLifecycle
import studio.craftory.tech.transport.HurlRideSystem
import studio.craftory.tech.transport.HurlRider
import studio.craftory.tech.transport.HurlRiderLifecycle
import studio.craftory.tech.transport.HurlStation
import studio.craftory.tech.well.CodexPageUi
import studio.craftory.tech.well.CodexUseInteraction
import studio.craftory.tech.well.FizzDamperState
import studio.craftory.tech.well.FizzDamperSystem
import studio.craftory.tech.well.GobJarUseInteraction
import studio.craftory.tech.well.JarCarrier
import studio.craftory.tech.well.JarCarrierDamageSystem
import studio.craftory.tech.well.JarCarrierDropSystem
import studio.craftory.tech.well.JarCarrierSaveLifecycle
import studio.craftory.tech.well.JarCarrierSeatLifecycle
import studio.craftory.tech.well.JarCarrierSystem
import studio.craftory.tech.well.LensScanState
import studio.craftory.tech.well.LensScanSystem
import studio.craftory.tech.well.LensUseInteraction
import studio.craftory.tech.well.SparkjawGuardian
import studio.craftory.tech.well.StormVaneState
import studio.craftory.tech.well.StormVaneSystem
import studio.craftory.tech.well.TetherStakeBeamSystem
import studio.craftory.tech.well.TetherStakeLifecycle
import studio.craftory.tech.well.TetherStakePlacementSystem
import studio.craftory.tech.well.TetherStakeState
import studio.craftory.tech.well.WellBirthState
import studio.craftory.tech.well.WellBirthSystem
import studio.craftory.tech.well.WellCaptureState
import studio.craftory.tech.well.WellConfig
import studio.craftory.tech.well.WellGuardianCalmSystem
import studio.craftory.tech.well.WellGuardianDeathSystem
import studio.craftory.tech.well.WellGuardianIndex
import studio.craftory.tech.well.WellGuardianLifecycle
import studio.craftory.tech.well.WellKillBirthSystem
import studio.craftory.tech.well.WellPulseState
import studio.craftory.tech.well.WellPulseSystem
import studio.craftory.tech.well.WellRuntimeConfig

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
    private val config = withConfig("config", CraftoryTechConfig.CODEC)
    private val wellConfig: Config<WellConfig> = withConfig("wells", WellConfig.CODEC)
    private val hurlConfig: Config<HurlConfig> = withConfig("hurl", HurlConfig.CODEC)
    private var crewKnowledgeSubscription: AutoCloseable? = null
    private var crewChangedSubscription: AutoCloseable? = null
    private var heartFormedSubscription: AutoCloseable? = null
    private var energyEventsSubscription: AutoCloseable? = null

    @Suppress("LongMethod")
    override fun setup() {
        val pluginVersion = getManifest().getVersion().toString()
        WellRuntimeConfig.current = wellConfig.get()
        CraftoryCore.registerModule("CraftoryTech", pluginVersion)
        CraftoryMobs.registerRoleAlias("glimmer_wisp", CraftoryIds.GLIMMER_WISP)
        CraftoryMobs.registerRoleAlias("sparkjaw", CraftoryIds.SPARKJAW)
        CraftoryMobs.registerRoleAlias("riftmoth", CraftoryIds.RIFTMOTH)
        CraftoryMobs.registerRoleAlias("goblin_poacher", CraftoryIds.GOBLIN_POACHER)
        CraftoryCore.registerEnergyType(
            CraftoryIds.CRANK_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.BURNER_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.RATTLEWHEEL_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.JUNK_HEART_TYPE,
            MemberSpec(MemberSpec.PRODUCER, 0, 0, 0, 0, 0),
        )
        CraftoryCore.multiblocks().register(JunkHeartTemplate.template)
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
            CraftoryIds.SNATCHER_CLAW_TYPE,
            MemberSpec(MemberSpec.CONSUMER, 0, 4, 0, 0, 1),
        )
        CraftoryCore.registerEnergyType(
            CraftoryIds.FIZZ_DAMPER_TYPE,
            MemberSpec(MemberSpec.CONSUMER, 0, 2, 0, 0, 1),
        )
        val chunkRegistry = getChunkStoreRegistry()
        CraftoryCore.registerEnergyType(
            CraftoryIds.HURL_STATION_TYPE,
            MemberSpec(MemberSpec.LINK, 0, 0, 0, 0, 1),
        )
        HurlStation.type =
            chunkRegistry.registerComponent(
                HurlStation::class.java,
                CraftoryIds.HURL_STATION_COMPONENT,
                HurlStation.CODEC,
            )
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
        RattlewheelState.type =
            chunkRegistry.registerComponent(
                RattlewheelState::class.java,
                CraftoryIds.RATTLEWHEEL_COMPONENT,
                RattlewheelState.CODEC,
            )
        ShroomBedState.type =
            chunkRegistry.registerComponent(
                ShroomBedState::class.java,
                CraftoryIds.SHROOM_BED_COMPONENT,
                ShroomBedState.CODEC,
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
        StormVaneState.type =
            chunkRegistry.registerComponent(
                StormVaneState::class.java,
                CraftoryIds.STORM_VANE_COMPONENT,
                StormVaneState.CODEC,
            )
        FizzDamperState.type =
            chunkRegistry.registerComponent(
                FizzDamperState::class.java,
                CraftoryIds.FIZZ_DAMPER_COMPONENT,
                FizzDamperState.CODEC,
            )
        TetherStakeState.type =
            chunkRegistry.registerComponent(
                TetherStakeState::class.java,
                CraftoryIds.TETHER_STAKE_COMPONENT,
                TetherStakeState.CODEC,
            )
        JunkHeartState.type =
            chunkRegistry.registerComponent(
                JunkHeartState::class.java,
                CraftoryIds.JUNK_HEART_COMPONENT,
                JunkHeartState.CODEC,
            )
        CraftoryCore.registerInspectableComponent(CraftoryIds.JUNK_HEART_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, JunkHeartState.type)
        }
        CraftoryCore.registerInspectableComponent("Craftory_MultiblockController") { accessor, ref ->
            accessor.getComponent(ref, MultiblockController.type)
        }
        Jank.type =
            chunkRegistry.registerComponent(
                Jank::class.java,
                CraftoryIds.JANK_COMPONENT,
                Jank.CODEC,
            )
        BodgeSlots.type =
            chunkRegistry.registerComponent(
                BodgeSlots::class.java,
                CraftoryIds.BODGE_SLOTS_COMPONENT,
                BodgeSlots.CODEC,
            )
        ScrapRushRaid.type =
            chunkRegistry.registerComponent(
                ScrapRushRaid::class.java,
                CraftoryIds.SCRAP_RUSH_COMPONENT,
                ScrapRushRaid.CODEC,
            )
        CraftoryCore.registerInspectableComponent(CraftoryIds.CRANK_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, CrankState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.BURNER_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, BurnerState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.RATTLEWHEEL_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, RattlewheelState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.SHROOM_BED_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, ShroomBedState.type)
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
        CraftoryCore.registerInspectableComponent(CraftoryIds.JANK_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, Jank.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.BODGE_SLOTS_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, BodgeSlots.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.SCRAP_RUSH_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, ScrapRushRaid.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.STORM_VANE_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, StormVaneState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.FIZZ_DAMPER_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, FizzDamperState.type)
        }
        CraftoryCore.registerInspectableComponent(CraftoryIds.TETHER_STAKE_COMPONENT) { accessor, ref ->
            accessor.getComponent(ref, TetherStakeState.type)
        }
        SelfTestRun.type = chunkRegistry.registerResource(SelfTestRun::class.java, ::SelfTestRun)
        WellBirthState.type = chunkRegistry.registerResource(WellBirthState::class.java, ::WellBirthState)
        TransportSelfTestRun.type =
            chunkRegistry.registerResource(TransportSelfTestRun::class.java, ::TransportSelfTestRun)
        AnchorRefreshIndex.type = chunkRegistry.registerResource(AnchorRefreshIndex::class.java, ::AnchorRefreshIndex)
        chunkRegistry.registerSystem(MachineClockLifecycle())
        chunkRegistry.registerSystem(AnchorLifecycle())
        chunkRegistry.registerSystem(CrankSystem())
        chunkRegistry.registerSystem(BurnerSystem())
        chunkRegistry.registerSystem(RattlewheelSystem())
        chunkRegistry.registerSystem(ShroomBedSystem())
        chunkRegistry.registerSystem(GnasherSystem())
        chunkRegistry.registerSystem(ScorchOvenSystem())
        chunkRegistry.registerSystem(JankSystem())
        chunkRegistry.registerSystem(ScrapRushSystem(config.get().scrapRush))
        chunkRegistry.registerSystem(SnatcherClawSystem())
        chunkRegistry.registerSystem(AnchorSystem())
        AssemblyViewIndex.type = chunkRegistry.registerResource(AssemblyViewIndex::class.java, ::AssemblyViewIndex)
        chunkRegistry.registerSystem(JunkHeartLifecycle())
        chunkRegistry.registerSystem(JunkHeartSystem())
        chunkRegistry.registerSystem(MultiblockAssemblySystem())
        chunkRegistry.registerSystem(GhostPreviewLifecycle())
        chunkRegistry.registerSystem(StormVaneSystem())
        chunkRegistry.registerSystem(FizzDamperSystem())
        chunkRegistry.registerSystem(TetherStakeLifecycle())
        chunkRegistry.registerSystem(TetherStakeBeamSystem())
        chunkRegistry.registerSystem(SelfTestSystem())
        chunkRegistry.registerSystem(TransportSelfTestSystem())
        val entityRegistry = getEntityStoreRegistry()
        LensScanState.type = entityRegistry.registerResource(LensScanState::class.java, ::LensScanState)
        WellPulseState.type = entityRegistry.registerResource(WellPulseState::class.java, ::WellPulseState)
        WellCaptureState.type = entityRegistry.registerResource(WellCaptureState::class.java, ::WellCaptureState)
        WellGuardianIndex.type = entityRegistry.registerResource(WellGuardianIndex::class.java, ::WellGuardianIndex)
        SparkjawGuardian.type =
            entityRegistry.registerComponent(
                SparkjawGuardian::class.java,
                "CraftorySparkjawGuardian",
                SparkjawGuardian.CODEC,
            )
        JarCarrier.type =
            entityRegistry.registerComponent(
                JarCarrier::class.java,
                "CraftoryJarCarrier",
                JarCarrier.CODEC,
            )
        HurlRide.type = entityRegistry.registerComponent(HurlRide::class.java, ::HurlRide)
        HurlRider.type = entityRegistry.registerComponent(HurlRider::class.java, ::HurlRider)
        HurlFallGuard.type = entityRegistry.registerComponent(HurlFallGuard::class.java, ::HurlFallGuard)
        HurlCounters.type = entityRegistry.registerResource(HurlCounters::class.java) { HurlCounters(hurlConfig.get()) }
        entityRegistry.registerSystem(HurlRideSystem())
        entityRegistry.registerSystem(HurlRideLifecycle())
        entityRegistry.registerSystem(HurlRiderLifecycle())
        entityRegistry.registerSystem(HurlFallDamageSystem())
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
        entityRegistry.registerSystem(TetherStakePlacementSystem())
        entityRegistry.registerSystem(BuildableMachineCraftGate())
        entityRegistry.registerSystem(LensScanSystem(wellConfig))
        entityRegistry.registerSystem(WellPulseSystem(wellConfig))
        entityRegistry.registerSystem(WellGuardianCalmSystem())
        entityRegistry.registerSystem(WellGuardianLifecycle())
        entityRegistry.registerSystem(WellGuardianDeathSystem())
        entityRegistry.registerSystem(JarCarrierSystem())
        entityRegistry.registerSystem(JarCarrierDamageSystem())
        entityRegistry.registerSystem(JarCarrierDropSystem())
        entityRegistry.registerSystem(JarCarrierSeatLifecycle())
        entityRegistry.registerSystem(JarCarrierSaveLifecycle())
        entityRegistry.registerSystem(WellBirthSystem(wellConfig))
        entityRegistry.registerSystem(WellKillBirthSystem(wellConfig))
        entityRegistry.registerSystem(RiftmothDrainSystem())
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
        OpenCustomUIInteraction.registerSimple(
            this,
            CodexPageUi::class.java,
            CraftoryIds.CODEX_PAGE,
        ) { playerRef ->
            CodexPageUi(playerRef, CraftoryCore.crewService())
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
                CraftoryIds.HAMMER_USE_INTERACTION,
                CraftoryHammerUseInteraction::class.java,
                CraftoryHammerUseInteraction.CODEC,
            ).register(
                CraftoryIds.RECEIPT_USE_INTERACTION,
                ReceiptUseInteraction::class.java,
                ReceiptUseInteraction.CODEC,
            ).register(
                CraftoryIds.BODGE_USE_INTERACTION,
                BodgeUseInteraction::class.java,
                BodgeUseInteraction.CODEC,
            ).register(
                CraftoryIds.OVEN_USE_INTERACTION,
                CraftoryOvenUseInteraction::class.java,
                CraftoryOvenUseInteraction.CODEC,
            ).register(
                CraftoryIds.LENS_USE_INTERACTION,
                LensUseInteraction::class.java,
                LensUseInteraction.CODEC,
            ).register(
                CraftoryIds.JAR_USE_INTERACTION,
                GobJarUseInteraction::class.java,
                GobJarUseInteraction.CODEC,
            ).register(
                CraftoryIds.CODEX_USE_INTERACTION,
                CodexUseInteraction::class.java,
                CodexUseInteraction.CODEC,
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
        heartFormedSubscription = CraftoryCore.multiblocks().onFormed(JunkHeartKnowledge::onFormed)
        energyEventsSubscription =
            CraftoryEnergyEventsApi.subscribe(
                MachineEnergyConditionListener { event ->
                    val expiresAt = System.nanoTime() + JankSystem.FLAG_TTL_NANOS
                    when (event.condition) {
                        MachineEnergyCondition.OVER_GRADED -> {
                            JankSystem.recordOverGraded(event.worldUuid, event.memberId, expiresAt)
                        }

                        MachineEnergyCondition.OVERLOADED -> {
                            JankSystem.recordOverloaded(event.worldUuid, event.memberId, expiresAt)
                        }
                    }
                },
            )
        CraftoryCore.registerCommandHandler("summon", SummonCommand::execute)
        CraftoryCore.registerCommandHandler("selftest", CraftorySelfTest::execute)
        CraftoryCore.registerCommandHandler("scraprush") { context ->
            ScrapRushCommand.execute(context, config.get().scrapRush)
        }
        LOGGER.atInfo().log("Craftory Tech v%s loaded", pluginVersion)
    }

    override fun shutdown() {
        crewKnowledgeSubscription?.close()
        crewKnowledgeSubscription = null
        crewChangedSubscription?.close()
        crewChangedSubscription = null
        heartFormedSubscription?.close()
        heartFormedSubscription = null
        energyEventsSubscription?.close()
        energyEventsSubscription = null
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
