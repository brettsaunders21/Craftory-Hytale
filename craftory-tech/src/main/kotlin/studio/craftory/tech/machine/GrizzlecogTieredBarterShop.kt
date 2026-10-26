package studio.craftory.tech.machine

import com.google.gson.JsonElement
import com.hypixel.hytale.builtin.adventure.shop.barter.BarterPage
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport
import com.hypixel.hytale.server.npc.asset.builder.InstructionType
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder
import com.hypixel.hytale.server.npc.asset.builder.validators.StringNotEmptyValidator
import com.hypixel.hytale.server.npc.corecomponents.ActionBase
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase
import com.hypixel.hytale.server.npc.instructions.Action
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider
import studio.craftory.core.api.CraftoryCore
import java.util.EnumSet

internal fun grizzlecogShopId(
    prefix: String,
    tier: Int,
): String = "$prefix${tier.coerceIn(1, 3)}"

internal const val GRIZZLECOG_SHOP_PREFIX = "Craftory_Grizzlecog_T"

class BuilderActionOpenTieredBarterShop : BuilderActionBase() {
    private val shopPrefix = StringHolder()

    override fun getShortDescription(): String = "Open the Grizzlecog shop for the player's crew tier"

    override fun getLongDescription(): String = getShortDescription()

    override fun getBuilderDescriptorState(): BuilderDescriptorState = BuilderDescriptorState.Stable

    override fun readConfig(data: JsonElement): BuilderActionOpenTieredBarterShop {
        requireString(
            data,
            "ShopPrefix",
            shopPrefix,
            StringNotEmptyValidator.get(),
            BuilderDescriptorState.Stable,
            "The prefix of the tiered barter shops",
            null,
        )
        requireInstructionType(EnumSet.of(InstructionType.Interaction))
        return this
    }

    override fun build(builderSupport: BuilderSupport): Action = ActionOpenTieredBarterShop(this, builderSupport)

    internal fun shopPrefix(builderSupport: BuilderSupport): String = shopPrefix.get(builderSupport.executionContext)
}

class ActionOpenTieredBarterShop(
    builder: BuilderActionOpenTieredBarterShop,
    builderSupport: BuilderSupport,
) : ActionBase(builder) {
    private val shopPrefix = builder.shopPrefix(builderSupport)

    override fun canExecute(
        ref: Ref<EntityStore>,
        executionSupport: ExecutionSupport,
        sensorInfo: InfoProvider?,
        dt: Double,
        store: Store<EntityStore>,
    ): Boolean =
        super.canExecute(ref, executionSupport, sensorInfo, dt, store) &&
            executionSupport.stateSupport.interactionIterationTarget != null

    override fun execute(
        ref: Ref<EntityStore>,
        executionSupport: ExecutionSupport,
        sensorInfo: InfoProvider?,
        dt: Double,
        store: Store<EntityStore>,
    ): Boolean {
        super.execute(ref, executionSupport, sensorInfo, dt, store)
        val playerReference = executionSupport.stateSupport.interactionIterationTarget ?: return false
        val playerRef = store.getComponent(playerReference, PlayerRef.getComponentType()) ?: return false
        val player = store.getComponent(playerReference, Player.getComponentType()) ?: return false
        val shopId = grizzlecogShopId(shopPrefix, CraftoryCore.crewService().tierOf(playerRef.uuid))
        player.pageManager.openCustomPage(ref, store, BarterPage(playerRef, shopId))
        return true
    }
}
