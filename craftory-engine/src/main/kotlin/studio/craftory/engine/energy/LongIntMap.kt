package studio.craftory.engine.energy

internal class LongIntMap(
    initialCapacity: Int = 16,
) {
    private var keys = LongArray(powerOfTwo(initialCapacity))
    private var values = IntArray(keys.size)
    private var states = ByteArray(keys.size)
    private var size = 0
    private var tombstones = 0

    fun getOrDefault(
        key: Long,
        defaultValue: Int,
    ): Int {
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() != EMPTY) {
            if (states[slot].toInt() == OCCUPIED && keys[slot] == key) {
                return values[slot]
            }
            slot = (slot + 1) and (keys.size - 1)
        }
        return defaultValue
    }

    fun put(
        key: Long,
        value: Int,
    ) {
        if ((size + tombstones + 1) * 10 >= keys.size * 7) {
            val newCapacity = if (size * 10 < keys.size * 4) keys.size else keys.size shl 1
            rehash(newCapacity)
        }

        var slot = hash(key) and (keys.size - 1)
        var firstTombstone = -1
        while (states[slot].toInt() != EMPTY) {
            when (states[slot].toInt()) {
                OCCUPIED -> {
                    if (keys[slot] == key) {
                        values[slot] = value
                        return
                    }
                }

                DELETED -> {
                    if (firstTombstone < 0) firstTombstone = slot
                }
            }
            slot = (slot + 1) and (keys.size - 1)
        }

        if (firstTombstone >= 0) {
            slot = firstTombstone
            tombstones--
        }
        keys[slot] = key
        values[slot] = value
        states[slot] = OCCUPIED.toByte()
        size++
    }

    fun remove(key: Long): Boolean {
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() != EMPTY) {
            if (states[slot].toInt() == OCCUPIED && keys[slot] == key) {
                states[slot] = DELETED.toByte()
                size--
                tombstones++
                return true
            }
            slot = (slot + 1) and (keys.size - 1)
        }
        return false
    }

    private fun rehash(capacity: Int) {
        val oldKeys = keys
        val oldValues = values
        val oldStates = states
        keys = LongArray(capacity)
        values = IntArray(capacity)
        states = ByteArray(capacity)
        size = 0
        tombstones = 0

        for (index in oldKeys.indices) {
            if (oldStates[index].toInt() == OCCUPIED) {
                insertRehashed(oldKeys[index], oldValues[index])
            }
        }
    }

    private fun insertRehashed(
        key: Long,
        value: Int,
    ) {
        var slot = hash(key) and (keys.size - 1)
        while (states[slot].toInt() == OCCUPIED) {
            slot = (slot + 1) and (keys.size - 1)
        }
        keys[slot] = key
        values[slot] = value
        states[slot] = OCCUPIED.toByte()
        size++
    }

    private fun hash(key: Long): Int {
        var mixed = key
        mixed = (mixed xor (mixed ushr 33)) * -49064778989728563L
        mixed = (mixed xor (mixed ushr 33)) * -4265267296055464877L
        mixed = mixed xor (mixed ushr 33)
        return mixed.toInt()
    }

    companion object {
        private const val EMPTY = 0
        private const val OCCUPIED = 1
        private const val DELETED = 2

        private fun powerOfTwo(value: Int): Int {
            require(value > 0)
            var capacity = 1
            while (capacity < value) {
                capacity = capacity shl 1
            }
            return maxOf(capacity, 16)
        }
    }
}
