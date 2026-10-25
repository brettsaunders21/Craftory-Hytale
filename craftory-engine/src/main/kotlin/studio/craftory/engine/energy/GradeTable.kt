package studio.craftory.engine.energy

data class GradeDef(
    val id: String,
    val rank: Int,
)

class GradeTable(
    defs: List<GradeDef>,
) {
    private val byRank: Array<GradeDef>
    private val byId: Map<String, GradeDef>

    init {
        require(defs.isNotEmpty())
        require(defs.map { it.id }.toSet().size == defs.size)
        val sorted = defs.sortedBy { it.rank }
        require(sorted.indices.all { sorted[it].rank == it })
        byRank = sorted.toTypedArray()
        byId = sorted.associateBy { it.id }
    }

    val size: Int
        get() = byRank.size

    fun byId(id: String): GradeDef? = byId[id]

    fun byRank(rank: Int): GradeDef? = byRank.getOrNull(rank)

    fun rankOf(id: String): Int? = byId[id]?.rank

    companion object {
        val DEFAULT =
            GradeTable(
                listOf(
                    GradeDef("fizz", 0),
                    GradeDef("pressurised_fizz", 1),
                    GradeDef("dim_glimmer", 2),
                    GradeDef("bright_glimmer", 3),
                    GradeDef("radiant_glimmer", 4),
                    GradeDef("prismatic_glimmer", 5),
                ),
            )
    }
}
