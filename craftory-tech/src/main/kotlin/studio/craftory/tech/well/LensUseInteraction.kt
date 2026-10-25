package studio.craftory.tech.well

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.TargetUtil
import com.hypixel.hytale.server.npc.entities.NPCEntity
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.energy.EnergyMember
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.tech.CraftoryIds

class LensUseInteraction : SimpleInteraction() {
    override fun tick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) {
        if (!firstRun || context.heldItem?.itemId != CraftoryIds.GLIMMER_LENS) return
        val commandBuffer = context.commandBuffer ?: return
        val playerRef = commandBuffer.getComponent(context.entity, PlayerRef.getComponentType()) ?: return
        val player = commandBuffer.getComponent(context.entity, Player.getComponentType()) ?: return
        val entityStore = commandBuffer.store
        val world = commandBuffer.externalData.world
        val targetEntity: Ref<EntityStore>? = TargetUtil.getTargetEntity(context.entity, 8.0f, commandBuffer)
        val npc =
            targetEntity?.let {
                commandBuffer.getComponent(it, NPCEntity.getComponentType()!!)
            }
        if (npc != null) {
            LensScanner.scanMob(playerRef, player, npc.roleName)
            return
        }
        val target =
            TargetUtil
                .getTargetBlock(context.entity, 8.0, commandBuffer)
                ?.let { BlockPos(it.x, it.y, it.z) }
        if (target == null) {
            playerRef.sendMessage(Message.raw("Target a block or creature to scan."))
            return
        }
        val blockStore = world.chunkStore.store
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(target.x, target.y, target.z)
        if (sectionRef == null) {
            playerRef.sendMessage(Message.raw("No loaded block at $target."))
            return
        }
        val blockRef = BlockModule.getBlockEntity(blockStore, sectionRef, target.x, target.y, target.z)
        val well = blockRef?.let { blockStore.getComponent(it, Glimmerwell.type) }
        if (well != null && blockRef != null) {
            val state = entityStore.getResource(LensScanState.type)
            val session = state.sessions[playerRef.uuid]
            if (session == null || session.pos != target || session.ref != blockRef) {
                val fresh = LensWellSession(target, blockRef, well, world.tick)
                state.sessions[playerRef.uuid] = fresh
                WellHud.showResonance(playerRef, player, 0, 600)
                playerRef.sendMessage(Message.translation("server.craftory.lens.started"))
                return
            }
            session.lockTicks.add(world.tick)
            if (session.lockTicks.size < 3) {
                WellHud.showResonance(playerRef, player, session.lockTicks.size, 600 - (world.tick - session.startTick))
                return
            }
            val score =
                ScanScoring.score(
                    session.startTick,
                    session.lockTicks,
                    WellRuntimeConfig.current.scanPeriodTicks.toLong(),
                    WellRuntimeConfig.current.scanWindowTicks.toLong(),
                )
            LensScanner.finish(playerRef, player, session, score.perfect, world, entityStore)
            state.sessions.remove(playerRef.uuid)
            WellHud.hideResonance(playerRef, player)
            return
        }
        val member = blockRef?.let { blockStore.getComponent(it, EnergyMember.type) }
        if (member != null && member.delivered > 0) {
            LensScanner.scanMachine(playerRef, player, member.typeId)
            return
        }
        val section = blockStore.getComponent(sectionRef, BlockSection.getComponentType())
        val blockId =
            section
                ?.get(target.x and 31, target.y and 31, target.z and 31)
                ?.let(BlockType.getAssetMap()::getAsset)
                ?.id
        if (blockId != null) {
            LensScanner.scanBlock(playerRef, player, blockId)
        } else {
            playerRef.sendMessage(Message.raw("No scan data is available for this target."))
        }
    }

    override fun simulateTick0(
        firstRun: Boolean,
        time: Float,
        type: InteractionType,
        context: InteractionContext,
        cooldownHandler: CooldownHandler,
    ) = Unit

    companion object {
        @JvmField
        val CODEC: BuilderCodec<LensUseInteraction> =
            BuilderCodec
                .builder(LensUseInteraction::class.java, ::LensUseInteraction, SimpleInteraction.CODEC)
                .build()
    }
}
