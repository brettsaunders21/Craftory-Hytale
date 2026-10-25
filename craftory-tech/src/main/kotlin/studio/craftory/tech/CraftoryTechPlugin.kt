package studio.craftory.tech

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.MemberSpec
import studio.craftory.tech.command.SummonCommand
import studio.craftory.tech.machine.CanisterPage
import studio.craftory.tech.machine.CraftoryBlockUseInteraction
import studio.craftory.tech.machine.CraftorySelfTest
import studio.craftory.tech.machine.JunkPowerHudSystem
import studio.craftory.tech.machine.JunkPowerKickSystem
import studio.craftory.tech.machine.JunkPowerLifecycle
import studio.craftory.tech.machine.JunkPowerState
import studio.craftory.tech.machine.JunkPowerTickSystem
import studio.craftory.tech.machine.JunkPowerWorld

class CraftoryTechPlugin(
    init: JavaPluginInit,
) : JavaPlugin(init) {
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
        JunkPowerState.type =
            chunkRegistry.registerComponent(
                JunkPowerState::class.java,
                CraftoryIds.MACHINE_COMPONENT,
                JunkPowerState.CODEC,
            )
        JunkPowerWorld.type = chunkRegistry.registerResource(JunkPowerWorld::class.java, ::JunkPowerWorld)
        chunkRegistry.registerSystem(JunkPowerLifecycle())
        chunkRegistry.registerSystem(JunkPowerTickSystem())
        getEntityStoreRegistry().registerSystem(JunkPowerKickSystem())
        getEntityStoreRegistry().registerSystem(JunkPowerHudSystem())
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
