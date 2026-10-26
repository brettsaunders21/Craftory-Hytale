package studio.craftory.engine.transport

class ItemKeyTable {
    private val idsByKey = HashMap<String, Int>()
    private val keysById = ArrayList<String>()

    fun intern(itemId: String): Int {
        require(itemId.isNotBlank())
        return idsByKey.getOrPut(itemId) {
            val id = keysById.size
            keysById += itemId
            id
        }
    }

    fun key(itemKey: Int): String? = keysById.getOrNull(itemKey)

    val size: Int
        get() = keysById.size
}
