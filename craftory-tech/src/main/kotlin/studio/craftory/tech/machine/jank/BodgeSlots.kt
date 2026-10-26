package studio.craftory.tech.machine.jank

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import studio.craftory.core.api.Inspectable

class BodgeSlots(
    var slot0: String? = null,
    var slot1: String? = null,
) : Component<ChunkStore>,
    Inspectable {
    override fun clone(): Component<ChunkStore> = BodgeSlots(slot0, slot1)

    override fun describe(): List<Pair<String, String>> =
        listOf(
            "slot0" to slot0.orEmpty(),
            "slot1" to slot1.orEmpty(),
        )

    fun itemIds(): List<String> = listOfNotNull(slot0, slot1)

    fun modifiers(): BodgeModifiers = bodgeModifiers(itemIds().mapNotNull(BodgePart::fromItemId))

    fun contains(itemId: String): Boolean = slot0 == itemId || slot1 == itemId

    fun install(itemId: String): Boolean {
        if (contains(itemId)) return false
        if (slot0 == null) {
            slot0 = itemId
            return true
        }
        if (slot1 == null) {
            slot1 = itemId
            return true
        }
        return false
    }

    fun remove(itemId: String): Boolean {
        if (slot0 == itemId) {
            slot0 = slot1
            slot1 = null
            return true
        }
        if (slot1 == itemId) {
            slot1 = null
            return true
        }
        return false
    }

    companion object {
        lateinit var type: ComponentType<ChunkStore, BodgeSlots>

        val CODEC: BuilderCodec<BodgeSlots> =
            BuilderCodec
                .builder(BodgeSlots::class.java, ::BodgeSlots)
                .append(
                    KeyedCodec("Slot0", Codec.STRING),
                    { slots, value -> slots.slot0 = value.takeIf(String::isNotEmpty) },
                    { it.slot0.orEmpty() },
                ).add()
                .append(
                    KeyedCodec("Slot1", Codec.STRING),
                    { slots, value -> slots.slot1 = value.takeIf(String::isNotEmpty) },
                    { it.slot1.orEmpty() },
                ).add()
                .build()
    }
}
