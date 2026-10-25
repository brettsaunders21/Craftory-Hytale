package studio.craftory.engine.tick

fun interface TickTask {
    fun run(currentTick: Long)
}

class Handle internal constructor(
    internal val token: Any,
)

class TickScheduler {
    private class Bucket(
        val interval: Int,
    ) {
        var index = -1
        var size = 0
        var entries = arrayOfNulls<Entry>(INITIAL_CAPACITY)

        fun add(entry: Entry) {
            if (size == entries.size) {
                entries = entries.copyOf(entries.size shl 1)
            }
            entry.bucketIndex = size
            entries[size++] = entry
        }

        fun remove(entry: Entry) {
            val index = entry.bucketIndex
            val lastIndex = --size
            if (index != lastIndex) {
                val moved = entries[lastIndex]!!
                entries[index] = moved
                moved.bucketIndex = index
            }
            entries[lastIndex] = null
        }

        companion object {
            private const val INITIAL_CAPACITY = 4
        }
    }

    private class Entry(
        val owner: TickScheduler,
        val key: Long,
        val interval: Int,
        val phase: Int,
        val task: TickTask,
        val bucket: Bucket,
    ) {
        var bucketIndex = -1
        var active = true
        var cancelled = false
    }

    private val bucketByInterval = HashMap<Int, Bucket>()
    private val buckets = ArrayList<Bucket>()
    private var pendingRemovals = arrayOfNulls<Entry>(16)
    private var pendingRemovalCount = 0
    private var registeredCount = 0
    private var ticking = false

    fun register(
        key: Long,
        intervalTicks: Int,
        task: TickTask,
    ): Handle {
        require(intervalTicks > 0)
        val bucket =
            bucketByInterval[intervalTicks]
                ?: Bucket(intervalTicks).also {
                    it.index = buckets.size
                    buckets.add(it)
                    bucketByInterval[intervalTicks] = it
                }
        val entry = Entry(this, key, intervalTicks, phaseFor(key, intervalTicks), task, bucket)
        bucket.add(entry)
        registeredCount++
        ensurePendingCapacity(registeredCount)
        return Handle(entry)
    }

    fun unregister(handle: Handle) {
        val entry = handle.token as? Entry ?: return
        if (entry.owner !== this || !entry.active || entry.cancelled) {
            return
        }
        entry.cancelled = true
        if (ticking) {
            pendingRemovals[pendingRemovalCount++] = entry
        } else {
            removeEntry(entry)
            removeEmptyBuckets()
        }
    }

    fun tick(currentTick: Long) {
        check(!ticking) { "TickScheduler does not support reentrant ticks" }
        ticking = true
        val bucketCount = buckets.size
        try {
            var bucketIndex = 0
            while (bucketIndex < bucketCount) {
                val bucket = buckets[bucketIndex]
                val entryCount = bucket.size
                val tickPhase = Math.floorMod(currentTick, bucket.interval.toLong())
                var index = 0
                while (index < entryCount) {
                    val entry = bucket.entries[index]!!
                    if (
                        entry.active &&
                        !entry.cancelled &&
                        (tickPhase + entry.phase) % bucket.interval == 0L
                    ) {
                        entry.task.run(currentTick)
                    }
                    index++
                }
                bucketIndex++
            }
        } finally {
            ticking = false
            drainPendingRemovals()
        }
    }

    private fun drainPendingRemovals() {
        var index = 0
        while (index < pendingRemovalCount) {
            val entry = pendingRemovals[index]!!
            pendingRemovals[index] = null
            if (entry.active && entry.cancelled) {
                removeEntry(entry)
            }
            index++
        }
        pendingRemovalCount = 0
        removeEmptyBuckets()
    }

    private fun removeEntry(entry: Entry) {
        entry.bucket.remove(entry)
        entry.active = false
        registeredCount--
    }

    private fun removeEmptyBuckets() {
        var index = 0
        while (index < buckets.size) {
            val bucket = buckets[index]
            if (bucket.size == 0) {
                bucketByInterval.remove(bucket.interval)
                val lastIndex = buckets.lastIndex
                if (index != lastIndex) {
                    val moved = buckets[lastIndex]
                    buckets[index] = moved
                    moved.index = index
                }
                buckets.removeAt(lastIndex)
            } else {
                index++
            }
        }
    }

    private fun ensurePendingCapacity(required: Int) {
        if (required <= pendingRemovals.size) {
            return
        }
        var capacity = pendingRemovals.size
        while (capacity < required) {
            capacity = capacity shl 1
        }
        pendingRemovals = pendingRemovals.copyOf(capacity)
    }

    private fun phaseFor(
        key: Long,
        interval: Int,
    ): Int {
        var mixed = key
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        mixed = mixed xor (mixed ushr 31)
        return java.lang.Long
            .remainderUnsigned(mixed, interval.toLong())
            .toInt()
    }
}
