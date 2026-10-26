package studio.craftory.tech.well

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.SoundCategory
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.ParticleUtil
import com.hypixel.hytale.server.core.universe.world.SoundUtil
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import org.joml.Vector3d
import studio.craftory.core.api.BlockPos
import studio.craftory.core.api.CraftoryCore
import studio.craftory.core.api.crew.CrewService
import studio.craftory.core.api.well.Glimmerwell
import studio.craftory.core.api.well.Temperament
import studio.craftory.core.api.well.WellScanEvent
import studio.craftory.core.api.well.WellStrength
import studio.craftory.tech.CraftoryIds

internal object LensScanner {
    fun finish(
        playerRef: PlayerRef,
        player: Player,
        session: LensWellSession,
        perfect: Boolean,
        world: World,
        store: Store<EntityStore>,
    ) {
        val well = session.ref.store.getComponent(session.ref, Glimmerwell.type) ?: return
        val crews = CraftoryCore.crewService()
        val crew = crews.crewOf(playerRef.uuid)
        val scanId = "craftory:scan/well/${well.temperament.id}"
        val firstRead = scanId !in crew.knowledge
        val firstTemperament = "craftory:well/${well.temperament.id}" !in crew.knowledge
        crews.grantKnowledge(playerRef.uuid, scanId)
        if (firstTemperament) crews.grantKnowledge(playerRef.uuid, "craftory:well/${well.temperament.id}")
        crews.addInsight(crew.id, well.temperament, InsightRules.well(well.strength, firstRead, perfect))
        CraftoryCore.wells().fireWellScanned(WellScanEvent(world, session.pos, well, perfect))
        if (perfect) {
            playerRef.sendMessage(Message.translation("server.craftory.lens.perfect").param("taste", well.taste))
            ParticleUtil.spawnParticleEffect(
                "Magic_Sparks_GS",
                Vector3d(session.pos.x + 0.5, session.pos.y + 1.2, session.pos.z + 0.5),
                store,
            )
            SoundUtil.playSoundEvent2dToPlayer(
                playerRef,
                "SFX_Crystal_Hit",
                SoundCategory.SFX,
            )
        } else {
            playerRef.sendMessage(Message.translation("server.craftory.lens.sloppy"))
        }
        WellHud.showInsight(playerRef, player, crews)
    }

    fun scanBlock(
        playerRef: PlayerRef,
        player: Player,
        blockId: String,
    ) {
        val crews = CraftoryCore.crewService()
        val crew = crews.crewOf(playerRef.uuid)
        val normalized = blockId.lowercase()
        val scanId = "craftory:scan/block/$normalized"
        val first = scanId !in crew.knowledge
        if (normalized.startsWith("ore_") || normalized.contains("_ore") || normalized.contains("crystal")) {
            val temperament = InsightRules.crystalTemperament(blockId)
            crews.grantKnowledge(playerRef.uuid, scanId)
            crews.addInsight(crew.id, temperament, InsightRules.oreCrystal(first))
            if (first) crews.grantKnowledge(playerRef.uuid, "craftory:well/${temperament.id}")
        } else {
            playerRef.sendMessage(Message.raw("No useful resonance found in $blockId."))
        }
        WellHud.showInsight(playerRef, player, crews)
    }

    fun scanMob(
        playerRef: PlayerRef,
        player: Player,
        role: String,
    ) {
        val crews = CraftoryCore.crewService()
        val crew = crews.crewOf(playerRef.uuid)
        val id = role.lowercase().replace(' ', '_')
        val scanId = "craftory:scan/mob/$id"
        val first = scanId !in crew.knowledge
        val temperament = InsightRules.mobTemperament(role)
        crews.grantKnowledge(playerRef.uuid, scanId)
        crews.addInsight(crew.id, temperament, InsightRules.mob(first))
        WellHud.showInsight(playerRef, player, crews)
    }

    fun scanMachine(
        playerRef: PlayerRef,
        player: Player,
        typeId: String,
        jank: Int? = null,
    ) {
        val crews = CraftoryCore.crewService()
        val crew = crews.crewOf(playerRef.uuid)
        val id = typeId.substringAfter(':').lowercase()
        val scanId = "craftory:scan/machine/$id"
        val first = scanId !in crew.knowledge
        crews.grantKnowledge(playerRef.uuid, scanId)
        crews.addInsight(
            crew.id,
            Temperament.CLANK,
            InsightRules.machine(first, jank),
        )
        WellHud.showInsight(playerRef, player, crews)
    }
}
