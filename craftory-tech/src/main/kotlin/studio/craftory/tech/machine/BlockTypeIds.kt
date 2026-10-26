package studio.craftory.tech.machine

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType

internal fun baseBlockId(
    id: String,
    defaultStateKey: String?,
): String = defaultStateKey ?: id

internal fun baseBlockId(type: BlockType): String = baseBlockId(type.id, type.defaultStateKey)
