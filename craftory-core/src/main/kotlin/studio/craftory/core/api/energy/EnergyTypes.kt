package studio.craftory.core.api.energy

import studio.craftory.engine.energy.EnergyEventSink as EngineEnergyEventSink
import studio.craftory.engine.energy.GradeDef as EngineGradeDef
import studio.craftory.engine.energy.GradeTable as EngineGradeTable
import studio.craftory.engine.energy.MemberSpec as EngineMemberSpec

typealias MemberSpec = EngineMemberSpec
typealias GradeDef = EngineGradeDef
typealias GradeTable = EngineGradeTable
typealias EnergyEventSink = EngineEnergyEventSink

data class EnergyMemberType(
    val id: String,
    val spec: MemberSpec,
    val linkFaces: Int = EnergyFaces.ALL,
) {
    init {
        EnergyFaces.requireValid(linkFaces)
    }
}

data class EnergyConverterType(
    val id: String,
    val input: MemberSpec,
    val output: MemberSpec,
    val inputFaces: Int,
    val outputFaces: Int,
) {
    init {
        EnergyFaces.requireValid(inputFaces)
        EnergyFaces.requireValid(outputFaces)
        require(input.role and MemberSpec.CONSUMER != 0) { "Converter input must be a consumer" }
        require(output.role and MemberSpec.PRODUCER != 0) { "Converter output must be a producer" }
    }
}

object EnergyFaces {
    const val DOWN = 1 shl 0
    const val UP = 1 shl 1
    const val NORTH = 1 shl 2
    const val SOUTH = 1 shl 3
    const val EAST = 1 shl 4
    const val WEST = 1 shl 5
    const val ALL = DOWN or UP or NORTH or SOUTH or EAST or WEST

    internal fun requireValid(faces: Int) {
        require((faces and ALL) == faces) { "Unknown energy face bits: $faces" }
    }

    internal fun connects(
        firstFaces: Int,
        secondFaces: Int,
        firstFace: Int,
    ): Boolean {
        val opposite =
            when (firstFace) {
                DOWN -> UP
                UP -> DOWN
                NORTH -> SOUTH
                SOUTH -> NORTH
                EAST -> WEST
                WEST -> EAST
                else -> return false
            }
        return (firstFaces and firstFace) != 0 && (secondFaces and opposite) != 0
    }
}

internal object EnergyMemberIds {
    const val CONVERTER_Y_MASK = 0x800L shl 26

    fun converterOutput(positionId: Long): Long = positionId xor CONVERTER_Y_MASK

    fun allowPhysicalNode(existingConverterOutput: Boolean): Boolean = !existingConverterOutput

    fun allowConverterOutput(outputIdTaken: Boolean): Boolean = !outputIdTaken
}
