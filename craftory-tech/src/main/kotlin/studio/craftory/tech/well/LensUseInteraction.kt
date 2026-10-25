package studio.craftory.tech.well

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.block.BlockModule
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
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
        if (CraftoryCore.crewService().crewOf(playerRef.uuid).tier < 2) {
            playerRef.sendMessage(Message.translation("server.craftory.tier.locked"))
            return
        }
        if (scanMob(context, commandBuffer, playerRef, player)) return
        scanTargetBlock(context, commandBuffer, playerRef, player)
    }

    private fun scanMob(
        context: InteractionContext,
        commandBuffer: CommandBuffer<EntityStore>,
        playerRef: PlayerRef,
        player: Player,
    ): Boolean {
        val targetEntity = TargetUtil.getTargetEntity(context.entity, 8.0f, commandBuffer) ?: return false
        val npc = commandBuffer.getComponent(targetEntity, NPCEntity.getComponentType()!!) ?: return false
        LensScanner.scanMob(playerRef, player, npc.roleName)
        return true
    }

    private fun scanTargetBlock(
        context: InteractionContext,
        commandBuffer: CommandBuffer<EntityStore>,
        playerRef: PlayerRef,
        player: Player,
    ) {
        val target =
            TargetUtil
                .getTargetBlock(context.entity, 8.0, commandBuffer)
                ?.let { BlockPos(it.x, it.y, it.z) }
        if (target == null) {
            playerRef.sendMessage(Message.raw("Target a block or creature to scan."))
            return
        }
        val world = commandBuffer.externalData.world
        val blockStore = world.chunkStore.store
        val sectionRef = world.chunkStore.getChunkSectionReferenceAtBlock(target.x, target.y, target.z)
        if (sectionRef == null) {
            playerRef.sendMessage(Message.raw("No loaded block at $target."))
            return
        }
        val blockRef = BlockModule.getBlockEntity(blockStore, sectionRef, target.x, target.y, target.z)
        val well = blockRef?.let { blockStore.getComponent(it, Glimmerwell.type) }
        if (well != null) {
            scanWell(playerRef, player, world, commandBuffer.store, target, blockRef, well)
            return
        }
        val member = blockRef?.let { blockStore.getComponent(it, EnergyMember.type) }
        if (member != null && isRunning(member)) {
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

    private fun scanWell(
        playerRef: PlayerRef,
        player: Player,
        world: World,
        entityStore: Store<EntityStore>,
        target: BlockPos,
        blockRef: Ref<ChunkStore>,
        well: Glimmerwell,
    ) {
        val state = entityStore.getResource(LensScanState.type)
        val session = state.sessions[playerRef.uuid]
        if (session == null || session.pos != target || session.ref != blockRef) {
            val newSession = LensWellSession(target, blockRef, well, world.tick)
            state.sessions[playerRef.uuid] = newSession
            WellHud.showResonance(playerRef, player, newSession, world.tick)
            playerRef.sendMessage(Message.translation("server.craftory.lens.started"))
            return
        }
        session.lockTicks.add(world.tick)
        if (session.lockTicks.size < 3) {
            WellHud.showResonance(
                playerRef,
                player,
                session,
                world.tick,
            )
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
    }

    private fun isRunning(member: EnergyMember): Boolean =
        member.production > 0 || (member.demand > 0 && member.delivered > 0)

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
