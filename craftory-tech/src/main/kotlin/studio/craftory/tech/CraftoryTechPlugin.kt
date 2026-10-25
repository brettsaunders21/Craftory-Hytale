package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.tech.command.SummonCommand
import studio.craftory.tech.machine.BurnerState
import studio.craftory.tech.machine.BurnerSystem
import studio.craftory.tech.machine.CanisterPage
import studio.craftory.tech.machine.CanisterPageRefreshSystem
import studio.craftory.tech.machine.CraftoryBlockUseInteraction
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.machine.CrankState
import studio.craftory.tech.machine.CrankSystem
import studio.craftory.tech.machine.EnergyHudState
import studio.craftory.tech.machine.GnasherState
import studio.craftory.tech.machine.GnasherSystem
import studio.craftory.tech.machine.JunkPowerHudSystem
import studio.craftory.tech.machine.JunkPowerKickSystem
import studio.craftory.tech.machine.MachineClockLifecycle
import studio.craftory.tech.machine.OvenPower
import studio.craftory.tech.machine.ScorchOvenSystem
import studio.craftory.tech.machine.SelfTestRun
import studio.craftory.tech.machine.SelfTestSystem

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
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
        SelfTestRun.type = chunkRegistry.registerResource(SelfTestRun::class.java, ::SelfTestRun)
        chunkRegistry.registerSystem(MachineClockLifecycle())
        chunkRegistry.registerSystem(CrankSystem())
        chunkRegistry.registerSystem(BurnerSystem())
        chunkRegistry.registerSystem(GnasherSystem())
        chunkRegistry.registerSystem(ScorchOvenSystem())
        chunkRegistry.registerSystem(SelfTestSystem())
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
        OpenCustomUIInteraction.registerBlockEntityCustomPage(
            this,
            CanisterPage::class.java,
            CraftoryIds.CANISTER_PAGE,
            CanisterPage::tryCreate,
        )
        getCodecRegistry(Interaction.CODEC)
            .register(
                CraftoryIds.BLOCK_USE_INTERACTION,
                CraftoryBlockUseInteraction::class.java,
                CraftoryBlockUseInteraction.CODEC,
            )
        CraftoryCore.registerCommandHandler("summon", SummonCommand::execute)
        CraftoryCore.registerCommandHandler("selftest", CraftorySelfTest::execute)
        LOGGER.atInfo().log("Craftory Tech v%s loaded", pluginVersion)
    }

    private companion object {
        val LOGGER: HytaleLogger = HytaleLogger.get("CraftoryTech")
    }
}
