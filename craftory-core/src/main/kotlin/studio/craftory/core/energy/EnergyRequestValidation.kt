package studio.craftory.core.energy

import studio.craftory.core.api.energy.GradeTable
import studio.craftory.core.api.energy.MemberSpec

internal object EnergyRequestValidation {
    fun productionInput(
        amount: Long,
        rank: Int,
        grades: GradeTable = GradeTable.DEFAULT,
    ) {
        require(amount >= 0) { "Production amount must be non-negative" }
        require(rank >= 0) { "Production rank must be non-negative" }
        require(grades.byRank(rank) != null) { "Production rank $rank is outside the grade table" }
    }

    fun production(
        id: Long,
        spec: MemberSpec,
        amount: Long,
        rank: Int,
        grades: GradeTable = GradeTable.DEFAULT,
    ) {
        productionInput(amount, rank, grades)
        producer(id, spec)
    }

    fun producer(
        id: Long,
        spec: MemberSpec,
    ) {
        require((spec.role and MemberSpec.PRODUCER) != 0) { "Member $id is not a producer" }
    }

    fun demandInput(amount: Long) {
        require(amount >= 0) { "Demand amount must be non-negative" }
    }

    fun demand(
        id: Long,
        spec: MemberSpec,
        amount: Long,
    ) {
        demandInput(amount)
        consumer(id, spec)
    }

    fun consumer(
        id: Long,
        spec: MemberSpec,
    ) {
        require((spec.role and MemberSpec.CONSUMER) != 0) { "Member $id is not a consumer" }
    }
}
