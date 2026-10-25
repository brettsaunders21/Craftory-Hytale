package studio.craftory.tech.transport

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import studio.craftory.core.api.transport.HurlMath

class HurlConfig {
    var rideDriver = "mount"
        set(value) {
            require(value == "mount" || value == "teleport")
            field = value
        }
    var costGrade = "fizz"
        set(value) {
            require(value.isNotBlank())
            field = value
        }
    var blocksPerUnit = 16.0
        set(value) {
            require(value.isFinite() && value > 0.0)
            field = value
        }
    var energyPerUnit = 100L
        set(value) {
            require(value >= 0L)
            field = value
        }
    var speed = DEFAULT_SPEED
        set(value) {
            require(value.isFinite() && value > 0.0)
            field = value
        }

    fun energyCost(lengthBlocks: Double): Long? {
        require(lengthBlocks.isFinite() && lengthBlocks >= 0.0)
        if (lengthBlocks / blocksPerUnit > Int.MAX_VALUE) return null
        val units = HurlMath.unitsFor(lengthBlocks, blocksPerUnit).toLong()
        if (energyPerUnit > Long.MAX_VALUE / units) return null
        return units * energyPerUnit
    }

    companion object {
        const val DEFAULT_SPEED = 0.5
        const val LOOKAHEAD = 32.0
        const val FALL_GUARD_TICKS = 200L

        val CODEC: BuilderCodec<HurlConfig> =
            BuilderCodec
                .builder(HurlConfig::class.java, ::HurlConfig)
                .append(
                    KeyedCodec("RideDriver", Codec.STRING),
                    { config, value -> config.rideDriver = value },
                    { it.rideDriver },
                ).add()
                .append(
                    KeyedCodec("CostGrade", Codec.STRING),
                    { config, value -> config.costGrade = value },
                    { it.costGrade },
                ).add()
                .append(KeyedCodec("BlocksPerUnit", Codec.DOUBLE), { config, value -> config.blocksPerUnit = value }, {
                    it.blocksPerUnit
                })
                .add()
                .append(KeyedCodec("EnergyPerUnit", Codec.LONG), { config, value -> config.energyPerUnit = value }, {
                    it.energyPerUnit
                })
                .add()
                .append(
                    KeyedCodec("Speed", Codec.DOUBLE),
                    { config, value -> config.speed = value },
                    { it.speed },
                ).add()
                .build()
    }
}
