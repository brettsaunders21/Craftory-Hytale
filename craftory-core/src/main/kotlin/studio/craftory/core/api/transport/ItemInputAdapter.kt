package studio.craftory.core.api.transport

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore

fun interface ItemInputAdapter {
    fun insert(
        store: Store<ChunkStore>,
        ref: Ref<ChunkStore>,
        itemId: String,
        quantity: Int,
    ): Int
}
