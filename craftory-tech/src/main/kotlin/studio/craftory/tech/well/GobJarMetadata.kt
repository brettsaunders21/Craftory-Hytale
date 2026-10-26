package studio.craftory.tech.well

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec

object GobJarMetadata {
    val TEMPERAMENT = KeyedCodec("Craftory_Temperament", Codec.STRING)
    val STRENGTH = KeyedCodec("Craftory_Strength", Codec.STRING)
    val TASTE = KeyedCodec("Craftory_Taste", Codec.STRING)
}
