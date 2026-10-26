package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.hypixel.hytale.server.core.entity.InteractionContext
import com.hypixel.hytale.server.core.inventory.ItemStack
import studio.craftory.tech.CraftoryIds

private const val FILLED_WATER_BUCKET_STATE = "Filled_Water"

internal fun filledWaterBucketItemId(stack: ItemStack?): String? {
    if (stack == null || stack.quantity != 1) return null
    val emptyBucket = Item.getAssetMap().getAsset(CraftoryIds.EMPTY_WOODEN_BUCKET) ?: return null
    if (emptyBucket.getStateForItem(stack.itemId) != FILLED_WATER_BUCKET_STATE) return null
    return emptyBucket.getItemIdForState(FILLED_WATER_BUCKET_STATE)
}

internal fun InteractionContext.consumeHeld(quantity: Int = 1): Boolean {
    val container = heldItemContainer ?: return false
    val slot = heldItemSlot.toShort()
    val held = container.getItemStack(slot) ?: return false
    if (held.quantity < quantity) return false
    val replacement = if (held.quantity == quantity) null else held.withQuantity(held.quantity - quantity)
    return container.setItemStackForSlot(slot, replacement).succeeded()
}
