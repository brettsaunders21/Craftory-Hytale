package studio.craftory.core.api

interface Inspectable {
    fun describe(): List<Pair<String, String>>
}
