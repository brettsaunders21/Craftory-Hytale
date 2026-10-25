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
)
