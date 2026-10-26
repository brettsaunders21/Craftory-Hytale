package studio.craftory.engine.energy

import java.util.Arrays
import kotlin.math.min

@Suppress("LargeClass", "TooManyFunctions")
class EnergyGraph {
    private class Network(
        val id: Int,
    ) {
        var size = 0
        var lossPerMillion = 0
        var awakeIndex = -1
        var ids = LongArray(INITIAL_CAPACITY)
        var handles = IntArray(INITIAL_CAPACITY)
        var stored = LongArray(INITIAL_CAPACITY)
        var storageEnabled = BooleanArray(INITIAL_CAPACITY)
        var capacity = LongArray(INITIAL_CAPACITY)
        var maxInput = LongArray(INITIAL_CAPACITY)
        var maxOutput = LongArray(INITIAL_CAPACITY)
        var production = LongArray(INITIAL_CAPACITY)
        var demand = LongArray(INITIAL_CAPACITY)
        var delivered = LongArray(INITIAL_CAPACITY)
        var minRank = IntArray(INITIAL_CAPACITY)
        var maxRank = IntArray(INITIAL_CAPACITY)
        var priority = IntArray(INITIAL_CAPACITY)
        var role = IntArray(INITIAL_CAPACITY)
        var loss = IntArray(INITIAL_CAPACITY)
        var productionRank = IntArray(INITIAL_CAPACITY)
        var storeRank = IntArray(INITIAL_CAPACITY) { -1 }
        var deliveredRank = IntArray(INITIAL_CAPACITY) { -1 }

        fun append(
            id: Long,
            handle: Int,
            spec: MemberSpec,
        ) {
            ensureCapacity(size + 1)
            val slot = size++
            ids[slot] = id
            handles[slot] = handle
            stored[slot] = 0L
            storageEnabled[slot] = (spec.role and MemberSpec.STORAGE) != 0
            capacity[slot] = spec.capacity
            maxInput[slot] = spec.maxInput
            maxOutput[slot] = spec.maxOutput
            production[slot] = 0L
            demand[slot] = 0L
            delivered[slot] = 0L
            minRank[slot] = spec.minRank
            maxRank[slot] = spec.maxRank
            priority[slot] = spec.priority
            role[slot] = spec.role
            loss[slot] = spec.lossPerMillion
            productionRank[slot] = 0
            storeRank[slot] = -1
            deliveredRank[slot] = -1
        }

        fun appendCopy(
            source: Network,
            sourceSlot: Int,
        ) {
            ensureCapacity(size + 1)
            copySlot(source, sourceSlot, size)
            size++
        }

        fun copySlot(
            source: Network,
            sourceSlot: Int,
            targetSlot: Int,
        ) {
            ids[targetSlot] = source.ids[sourceSlot]
            handles[targetSlot] = source.handles[sourceSlot]
            stored[targetSlot] = source.stored[sourceSlot]
            storageEnabled[targetSlot] = source.storageEnabled[sourceSlot]
            capacity[targetSlot] = source.capacity[sourceSlot]
            maxInput[targetSlot] = source.maxInput[sourceSlot]
            maxOutput[targetSlot] = source.maxOutput[sourceSlot]
            production[targetSlot] = source.production[sourceSlot]
            demand[targetSlot] = source.demand[sourceSlot]
            delivered[targetSlot] = source.delivered[sourceSlot]
            minRank[targetSlot] = source.minRank[sourceSlot]
            maxRank[targetSlot] = source.maxRank[sourceSlot]
            priority[targetSlot] = source.priority[sourceSlot]
            role[targetSlot] = source.role[sourceSlot]
            loss[targetSlot] = source.loss[sourceSlot]
            productionRank[targetSlot] = source.productionRank[sourceSlot]
            storeRank[targetSlot] = source.storeRank[sourceSlot]
            deliveredRank[targetSlot] = source.deliveredRank[sourceSlot]
        }

        private fun ensureCapacity(required: Int) {
            if (required <= ids.size) {
                return
            }
            val previousCapacity = ids.size
            var newCapacity = ids.size
            while (newCapacity < required) {
                newCapacity = newCapacity shl 1
            }
            ids = ids.copyOf(newCapacity)
            handles = handles.copyOf(newCapacity)
            stored = stored.copyOf(newCapacity)
            storageEnabled = storageEnabled.copyOf(newCapacity)
            capacity = capacity.copyOf(newCapacity)
            maxInput = maxInput.copyOf(newCapacity)
            maxOutput = maxOutput.copyOf(newCapacity)
            production = production.copyOf(newCapacity)
            demand = demand.copyOf(newCapacity)
            delivered = delivered.copyOf(newCapacity)
            minRank = minRank.copyOf(newCapacity)
            maxRank = maxRank.copyOf(newCapacity)
            priority = priority.copyOf(newCapacity)
            role = role.copyOf(newCapacity)
            loss = loss.copyOf(newCapacity)
            productionRank = productionRank.copyOf(newCapacity)
            storeRank = storeRank.copyOf(newCapacity)
            Arrays.fill(storeRank, previousCapacity, newCapacity, -1)
            deliveredRank = deliveredRank.copyOf(newCapacity)
            Arrays.fill(deliveredRank, previousCapacity, newCapacity, -1)
        }

        companion object {
            private const val INITIAL_CAPACITY = 4
        }
    }

    private val idToHandle = LongIntMap()
    private val networksById = ArrayList<Network?>()
    private val awakeNetworks = ArrayList<Network>()
    private var nextNetworkId = 0
    private var handleNetworks = IntArray(16) { -1 }
    private var handleSlots = IntArray(16) { -1 }
    private var adjacencies = arrayOfNulls<IntArray>(16)
    private var adjacencySizes = IntArray(16)
    private var nextHandle = 0
    private var freeHandles = IntArray(16)
    private var freeHandleCount = 0
    private var visited = IntArray(16)
    private var visitOrder = IntArray(16)
    private var componentStarts = IntArray(16)
    private var componentSizes = IntArray(16)
    private var visitStamp = 0
    private var largestComponentScratch = 0
    private var demandScratch = LongArray(16)
    private var allocationScratch = LongArray(16)
    private var weightScratch = LongArray(16)
    private var priorityScratch = IntArray(16)
    private var productionTotalScratch = 0L
    private var storeAvailableScratch = 0L
    private var usableSupplyScratch = 0L
    private var eligibleDemandScratch = 0L

    fun add(
        id: Long,
        spec: MemberSpec,
        neighbours: LongArray,
    ) {
        require(idToHandle.getOrDefault(id, -1) < 0) { "Member $id already exists" }
        for (neighbour in neighbours) {
            require(neighbour == id || idToHandle.getOrDefault(neighbour, -1) >= 0) {
                "Unknown neighbour $neighbour"
            }
        }

        val handle = allocateHandle()
        val network = createNetwork()
        network.append(id, handle, spec)
        handleNetworks[handle] = network.id
        handleSlots[handle] = 0
        idToHandle.put(id, handle)
        recomputeLoss(network)
        wake(network)

        for (neighbour in neighbours) {
            if (neighbour != id) {
                connectHandles(handle, idToHandle.getOrDefault(neighbour, -1))
            }
        }
    }

    fun remove(id: Long) {
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) {
            return
        }
        val network = networkForHandle(handle)
        val adjacency = adjacencies[handle]!!
        while (adjacencySizes[handle] > 0) {
            val neighbour = adjacency[adjacencySizes[handle] - 1]
            removeAdjacency(neighbour, handle)
            adjacencySizes[handle]--
        }

        idToHandle.remove(id)
        removeSlot(network, handleSlots[handle])
        handleNetworks[handle] = -1
        handleSlots[handle] = -1
        freeHandle(handle)

        if (network.size == 0) {
            detachNetwork(network)
        } else {
            splitNetwork(network)
        }
    }

    fun connect(
        a: Long,
        b: Long,
    ) {
        val first = requireHandle(a)
        val second = requireHandle(b)
        connectHandles(first, second)
    }

    fun disconnect(
        a: Long,
        b: Long,
    ) {
        val first = requireHandle(a)
        val second = requireHandle(b)
        if (first == second || !hasAdjacency(first, second)) {
            return
        }
        removeAdjacency(first, second)
        removeAdjacency(second, first)
        val firstNetwork = networkForHandle(first)
        val secondNetwork = networkForHandle(second)
        if (firstNetwork.id == secondNetwork.id) {
            splitNetwork(firstNetwork)
        } else {
            wake(firstNetwork)
            wake(secondNetwork)
        }
    }

    fun networkOf(id: Long): Int {
        val handle = idToHandle.getOrDefault(id, -1)
        return if (handle < 0) -1 else handleNetworks[handle]
    }

    fun isAwake(id: Long): Boolean {
        val handle = idToHandle.getOrDefault(id, -1)
        return handle >= 0 && networkForHandle(handle).awakeIndex >= 0
    }

    val networkCount: Int
        get() = networksById.count { it != null }

    val awakeNetworkCount: Int
        get() = awakeNetworks.size

    fun setProduction(
        id: Long,
        amount: Long,
        rank: Int,
    ) {
        require(amount >= 0)
        require(rank >= 0)
        val handle = requireHandle(id)
        val network = networkForHandle(handle)
        val slot = handleSlots[handle]
        require((network.role[slot] and MemberSpec.PRODUCER) != 0) { "Member $id is not a producer" }
        network.production[slot] = amount
        network.productionRank[slot] = rank
        wake(network)
    }

    fun setDemand(
        id: Long,
        amount: Long,
    ) {
        require(amount >= 0)
        val handle = requireHandle(id)
        val network = networkForHandle(handle)
        val slot = handleSlots[handle]
        require((network.role[slot] and MemberSpec.CONSUMER) != 0) { "Member $id is not a consumer" }
        network.demand[slot] = amount
        wake(network)
    }

    fun setStorageEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        val handle = requireHandle(id)
        val network = networkForHandle(handle)
        val slot = handleSlots[handle]
        require((network.role[slot] and MemberSpec.STORAGE) != 0) { "Member $id is not storage" }
        if (network.storageEnabled[slot] == enabled) return
        network.storageEnabled[slot] = enabled
        wake(network)
    }

    fun stored(id: Long): Long {
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) {
            return 0L
        }
        return networkForHandle(handle).stored[handleSlots[handle]]
    }

    fun storedRank(id: Long): Int {
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) {
            return -1
        }
        return networkForHandle(handle).storeRank[handleSlots[handle]]
    }

    fun networkStored(
        id: Long,
        minRank: Int,
    ): Long {
        require(minRank >= 0)
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) return 0L
        val network = networkForHandle(handle)
        var total = 0L
        for (slot in 0 until network.size) {
            if (eligibleStore(network, slot, minRank)) {
                total = saturatedAdd(total, network.stored[slot])
            }
        }
        return total
    }

    /** Explicit debit ignores per-tick output limits and solver activity gates. */
    fun drawStored(
        id: Long,
        amount: Long,
        minGradeRank: Int,
    ): Boolean {
        require(amount >= 0L && minGradeRank >= 0)
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0 || networkStored(id, minGradeRank) < amount) return false
        val network = networkForHandle(handle)
        var remaining = amount
        for (slot in 0 until network.size) {
            if (remaining == 0L) break
            if (eligibleStore(network, slot, minGradeRank)) {
                val drawn = min(remaining, network.stored[slot])
                network.stored[slot] -= drawn
                remaining -= drawn
                if (network.stored[slot] == 0L) network.storeRank[slot] = -1
            }
        }
        if (amount > 0L) wake(network)
        return true
    }

    private fun eligibleStore(
        network: Network,
        slot: Int,
        minRank: Int,
    ): Boolean = (network.role[slot] and MemberSpec.STORAGE) != 0 && network.storeRank[slot] >= minRank

    fun restoreStored(
        id: Long,
        amount: Long,
        rank: Int,
    ) {
        require(amount >= 0)
        require(rank >= -1)
        val handle = requireHandle(id)
        val network = networkForHandle(handle)
        val slot = handleSlots[handle]
        require((network.role[slot] and MemberSpec.STORAGE) != 0) { "Member $id is not storage" }
        require(amount <= network.capacity[slot]) { "Stored energy exceeds capacity for member $id" }
        require(amount == 0L || rank >= 0) { "Stored energy requires a grade rank" }
        network.stored[slot] = amount
        network.storeRank[slot] = if (amount == 0L) -1 else rank
        wake(network)
    }

    fun delivered(id: Long): Long {
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) {
            return 0L
        }
        return networkForHandle(handle).delivered[handleSlots[handle]]
    }

    fun deliveredRank(id: Long): Int {
        val handle = idToHandle.getOrDefault(id, -1)
        if (handle < 0) {
            return -1
        }
        return networkForHandle(handle).deliveredRank[handleSlots[handle]]
    }

    fun tick(sink: EnergyEventSink = EnergyEventSink.NONE) {
        var index = 0
        while (index < awakeNetworks.size) {
            val network = awakeNetworks[index]
            solve(network, sink)
            if (isIdle(network)) {
                sleep(network)
            } else {
                index++
            }
        }
    }

    private fun solve(
        network: Network,
        sink: EnergyEventSink,
    ) {
        val grade = prepareSupply(network)
        val priorityCount = collectEligibleConsumers(network, grade, sink)
        val delivered = allocatePriorityBuckets(network, grade, priorityCount, sink)
        settleSupply(network, grade, delivered)
    }

    @Suppress("ComplexCondition")
    private fun prepareSupply(network: Network): Int {
        var grade = -1
        productionTotalScratch = 0L
        var slot = 0
        while (slot < network.size) {
            network.delivered[slot] = 0L
            network.deliveredRank[slot] = -1
            val production = network.production[slot]
            if (production > 0L) {
                productionTotalScratch = saturatedAdd(productionTotalScratch, production)
                if (network.productionRank[slot] > grade) {
                    grade = network.productionRank[slot]
                }
            }
            if (
                (network.role[slot] and MemberSpec.STORAGE) != 0 &&
                network.storageEnabled[slot] &&
                network.stored[slot] > 0L &&
                network.storeRank[slot] > grade
            ) {
                grade = network.storeRank[slot]
            }
            weightScratch[slot] = 0L
            demandScratch[slot] = 0L
            allocationScratch[slot] = 0L
            slot++
        }

        storeAvailableScratch = 0L
        slot = 0
        while (slot < network.size) {
            if (
                (network.role[slot] and MemberSpec.STORAGE) != 0 &&
                network.storageEnabled[slot] &&
                network.stored[slot] > 0L &&
                network.storeRank[slot] == grade
            ) {
                val available = min(network.stored[slot], network.maxOutput[slot])
                weightScratch[slot] = available
                storeAvailableScratch = saturatedAdd(storeAvailableScratch, available)
            }
            slot++
        }

        val rawSupply = saturatedAdd(productionTotalScratch, storeAvailableScratch)
        val lossFactor = (ENERGY_SCALE - network.lossPerMillion).toInt()
        usableSupplyScratch = scaleDown(rawSupply, lossFactor)
        return grade
    }

    private fun collectEligibleConsumers(
        network: Network,
        grade: Int,
        sink: EnergyEventSink,
    ): Int {
        var priorityCount = 0
        eligibleDemandScratch = 0L

        var slot = 0
        while (slot < network.size) {
            val requested = min(network.demand[slot], network.maxInput[slot])
            if (requested > 0L && (network.role[slot] and MemberSpec.CONSUMER) != 0) {
                when {
                    grade > network.maxRank[slot] -> {
                        sink.overload(network.ids[slot], grade - network.maxRank[slot])
                    }

                    grade < network.minRank[slot] -> {
                        sink.underGrade(network.ids[slot])
                    }

                    else -> {
                        demandScratch[slot] = requested
                        eligibleDemandScratch = saturatedAdd(eligibleDemandScratch, requested)
                        priorityScratch[priorityCount++] = network.priority[slot]
                    }
                }
            }
            slot++
        }
        return priorityCount
    }

    private fun allocatePriorityBuckets(
        network: Network,
        grade: Int,
        priorityCount: Int,
        sink: EnergyEventSink,
    ): Long {
        Arrays.sort(priorityScratch, 0, priorityCount)
        var deliveredTotal = 0L
        var priorityIndex = priorityCount - 1
        var brownedOut = false
        while (priorityIndex >= 0) {
            val bucketPriority = priorityScratch[priorityIndex]
            while (priorityIndex >= 0 && priorityScratch[priorityIndex] == bucketPriority) {
                priorityIndex--
            }

            val bucketDemand = demandForPriority(network, bucketPriority)
            if (bucketDemand > 0L) {
                val available = usableSupplyScratch - deliveredTotal
                val bucketBudget = min(available.coerceAtLeast(0L), bucketDemand)
                allocatePriorityBucket(network, bucketPriority, grade, bucketDemand, bucketBudget)
                deliveredTotal = saturatedAdd(deliveredTotal, bucketBudget)
                if (bucketBudget < bucketDemand) {
                    brownedOut = true
                    break
                }
            }
        }

        if (brownedOut) {
            val fraction =
                if (eligibleDemandScratch == 0L) {
                    1.0
                } else {
                    deliveredTotal.toDouble() / eligibleDemandScratch.toDouble()
                }
            sink.brownout(network.id, fraction.coerceIn(0.0, 1.0))
        }
        return deliveredTotal
    }

    private fun demandForPriority(
        network: Network,
        bucketPriority: Int,
    ): Long {
        var total = 0L
        var slot = 0
        while (slot < network.size) {
            if (demandScratch[slot] > 0L && network.priority[slot] == bucketPriority) {
                total = saturatedAdd(total, demandScratch[slot])
            }
            slot++
        }
        return total
    }

    private fun allocatePriorityBucket(
        network: Network,
        bucketPriority: Int,
        grade: Int,
        bucketDemand: Long,
        bucketBudget: Long,
    ) {
        var allocated = 0L
        var slot = 0
        while (slot < network.size) {
            val requested = demandScratch[slot]
            if (requested > 0L && network.priority[slot] == bucketPriority) {
                val share =
                    if (bucketBudget == bucketDemand) {
                        requested
                    } else {
                        proportionalShare(bucketBudget, requested, bucketDemand)
                    }
                allocationScratch[slot] = share
                allocated = saturatedAdd(allocated, share)
            }
            slot++
        }

        var remainder = bucketBudget - allocated
        slot = 0
        while (remainder > 0L && slot < network.size) {
            if (
                demandScratch[slot] > allocationScratch[slot] &&
                network.priority[slot] == bucketPriority
            ) {
                allocationScratch[slot]++
                remainder--
            }
            slot++
        }

        slot = 0
        while (slot < network.size) {
            if (demandScratch[slot] > 0L && network.priority[slot] == bucketPriority) {
                val amount = allocationScratch[slot]
                network.delivered[slot] = amount
                if (amount > 0L) {
                    network.deliveredRank[slot] = grade
                }
            }
            slot++
        }
    }

    private fun settleSupply(
        network: Network,
        grade: Int,
        deliveredTotal: Long,
    ) {
        val lossFactor = (ENERGY_SCALE - network.lossPerMillion).toInt()
        val rawUsed = requiredRaw(deliveredTotal, lossFactor)
        val productionUsed = min(rawUsed, productionTotalScratch)
        val storeDraw = (rawUsed - productionUsed).coerceAtLeast(0L)
        if (storeDraw > 0L) {
            distributeStoreDraw(network, storeDraw, storeAvailableScratch)
        }

        val surplusProduction = productionTotalScratch - productionUsed
        val chargeSupply = scaleDown(surplusProduction, lossFactor)
        if (chargeSupply > 0L && grade >= 0) {
            chargeStores(network, chargeSupply, grade)
        }
    }

    private fun distributeStoreDraw(
        network: Network,
        amount: Long,
        totalAvailable: Long,
    ) {
        if (amount <= 0L || totalAvailable <= 0L) {
            return
        }
        distributeProportionally(network, amount, totalAvailable)
        var slot = 0
        while (slot < network.size) {
            val draw = allocationScratch[slot]
            if (draw > 0L) {
                network.stored[slot] -= draw
                if (network.stored[slot] == 0L) {
                    network.storeRank[slot] = -1
                }
            }
            slot++
        }
    }

    private fun chargeStores(
        network: Network,
        supply: Long,
        grade: Int,
    ) {
        val totalCapacity = availableStoreCapacity(network, grade)
        val budget = min(supply, totalCapacity)
        if (budget == 0L || totalCapacity == 0L) {
            return
        }
        distributeProportionally(network, budget, totalCapacity)
        var slot = 0
        while (slot < network.size) {
            val charge = allocationScratch[slot]
            if (charge > 0L) {
                if (network.stored[slot] == 0L) {
                    network.storeRank[slot] = grade
                }
                network.stored[slot] += charge
            }
            slot++
        }
    }

    private fun availableStoreCapacity(
        network: Network,
        grade: Int,
    ): Long {
        var totalCapacity = 0L
        var slot = 0
        while (slot < network.size) {
            val stored = network.stored[slot]
            val acceptsGrade =
                stored == 0L ||
                    (network.storeRank[slot] == grade)
            if ((network.role[slot] and MemberSpec.STORAGE) != 0 && network.storageEnabled[slot] && acceptsGrade) {
                val free = network.capacity[slot] - stored
                val inputCapacity = min(free.coerceAtLeast(0L), network.maxInput[slot])
                weightScratch[slot] = inputCapacity
                totalCapacity = saturatedAdd(totalCapacity, inputCapacity)
            } else {
                weightScratch[slot] = 0L
            }
            allocationScratch[slot] = 0L
            slot++
        }
        return totalCapacity
    }

    private fun distributeProportionally(
        network: Network,
        budget: Long,
        totalCapacity: Long,
    ) {
        var allocated = 0L
        var slot = 0
        while (slot < network.size) {
            val capacity = weightScratch[slot]
            val charge =
                if (capacity == 0L) {
                    0L
                } else if (budget == totalCapacity) {
                    capacity
                } else {
                    proportionalShare(budget, capacity, totalCapacity)
                }
            allocationScratch[slot] = charge
            allocated = saturatedAdd(allocated, charge)
            slot++
        }
        var remainder = budget - allocated
        slot = 0
        while (remainder > 0L && slot < network.size) {
            if (allocationScratch[slot] < weightScratch[slot]) {
                allocationScratch[slot]++
                remainder--
            }
            slot++
        }
    }

    private fun isIdle(network: Network): Boolean {
        var slot = 0
        while (slot < network.size) {
            if (network.production[slot] > 0L || network.demand[slot] > 0L) {
                return false
            }
            slot++
        }
        return true
    }

    private fun createNetwork(): Network {
        val network = Network(nextNetworkId++)
        networksById.add(network)
        return network
    }

    private fun networkForHandle(handle: Int): Network = networksById[handleNetworks[handle]]!!

    private fun requireHandle(id: Long): Int {
        val handle = idToHandle.getOrDefault(id, -1)
        require(handle >= 0) { "Unknown member $id" }
        return handle
    }

    private fun connectHandles(
        first: Int,
        second: Int,
    ) {
        if (first == second || hasAdjacency(first, second)) {
            return
        }
        addAdjacency(first, second)
        addAdjacency(second, first)
        val firstNetwork = networkForHandle(first)
        val secondNetwork = networkForHandle(second)
        wake(firstNetwork)
        wake(secondNetwork)
        if (firstNetwork.id == secondNetwork.id) {
            return
        }

        val target: Network
        val source: Network
        if (firstNetwork.size >= secondNetwork.size) {
            target = firstNetwork
            source = secondNetwork
        } else {
            target = secondNetwork
            source = firstNetwork
        }
        while (source.size > 0) {
            moveMember(source, source.size - 1, target)
        }
        target.lossPerMillion =
            min(
                MAX_NETWORK_LOSS,
                target.lossPerMillion + source.lossPerMillion,
            )
        detachNetwork(source)
        wake(target)
    }

    private fun moveMember(
        source: Network,
        sourceSlot: Int,
        target: Network,
    ) {
        val handle = source.handles[sourceSlot]
        val targetSlot = target.size
        target.appendCopy(source, sourceSlot)
        removeSlot(source, sourceSlot)
        handleNetworks[handle] = target.id
        handleSlots[handle] = targetSlot
    }

    private fun splitNetwork(network: Network) {
        if (network.size <= 1) {
            recomputeLoss(network)
            wake(network)
            return
        }

        val componentCount = collectComponents(network)
        if (componentCount == 1) {
            recomputeLoss(network)
            wake(network)
            return
        }
        splitComponents(network, componentCount)
        recomputeLoss(network)
        wake(network)
    }

    private fun collectComponents(network: Network): Int {
        ensureTopologyScratch(network.size)
        val stamp = nextVisitStamp()
        val originalSize = network.size
        var orderSize = 0
        var componentCount = 0
        var largestComponent = 0
        var largestSize = 0
        var slot = 0
        while (slot < originalSize) {
            val startHandle = network.handles[slot]
            if (visited[startHandle] != stamp) {
                val componentSize = visitComponent(network, startHandle, stamp, orderSize)
                componentStarts[componentCount] = orderSize
                componentSizes[componentCount] = componentSize
                if (componentSize > largestSize) {
                    largestComponent = componentCount
                    largestSize = componentSize
                }
                orderSize += componentSize
                componentCount++
            }
            slot++
        }
        largestComponentScratch = largestComponent
        return componentCount
    }

    private fun visitComponent(
        network: Network,
        startHandle: Int,
        stamp: Int,
        start: Int,
    ): Int {
        var tail = start
        visitOrder[tail++] = startHandle
        visited[startHandle] = stamp
        var head = start
        while (head < tail) {
            val handle = visitOrder[head++]
            val adjacency = adjacencies[handle]!!
            var edge = 0
            while (edge < adjacencySizes[handle]) {
                val neighbour = adjacency[edge++]
                if (handleNetworks[neighbour] == network.id && visited[neighbour] != stamp) {
                    visited[neighbour] = stamp
                    visitOrder[tail++] = neighbour
                }
            }
        }
        return tail - start
    }

    private fun splitComponents(
        network: Network,
        componentCount: Int,
    ) {
        var component = 0
        while (component < componentCount) {
            if (component != largestComponentScratch) {
                val split = createNetwork()
                moveComponentMembers(network, split, component)
                recomputeLoss(split)
                wake(split)
            }
            component++
        }
    }

    private fun moveComponentMembers(
        network: Network,
        target: Network,
        component: Int,
    ) {
        val start = componentStarts[component]
        val end = start + componentSizes[component]
        var index = start
        while (index < end) {
            val handle = visitOrder[index++]
            moveMember(network, handleSlots[handle], target)
        }
    }

    private fun recomputeLoss(network: Network) {
        var total = 0
        var slot = 0
        while (slot < network.size && total < MAX_NETWORK_LOSS) {
            if ((network.role[slot] and MemberSpec.LINK) != 0) {
                val remaining = MAX_NETWORK_LOSS - total
                total =
                    if (network.loss[slot] >= remaining) {
                        MAX_NETWORK_LOSS
                    } else {
                        total + network.loss[slot]
                    }
            }
            slot++
        }
        network.lossPerMillion = total
    }

    private fun removeSlot(
        network: Network,
        slot: Int,
    ) {
        val last = network.size - 1
        if (slot != last) {
            network.copySlot(network, last, slot)
            val movedHandle = network.handles[slot]
            handleNetworks[movedHandle] = network.id
            handleSlots[movedHandle] = slot
        }
        network.size = last
    }

    private fun detachNetwork(network: Network) {
        sleep(network)
        networksById[network.id] = null
    }

    private fun wake(network: Network) {
        if (network.awakeIndex >= 0) {
            return
        }
        network.awakeIndex = awakeNetworks.size
        awakeNetworks.add(network)
    }

    private fun sleep(network: Network) {
        val index = network.awakeIndex
        if (index < 0) {
            return
        }
        val lastIndex = awakeNetworks.size - 1
        if (index != lastIndex) {
            val moved = awakeNetworks[lastIndex]
            awakeNetworks[index] = moved
            moved.awakeIndex = index
        }
        awakeNetworks.removeAt(lastIndex)
        network.awakeIndex = -1
    }

    private fun allocateHandle(): Int {
        val handle =
            if (freeHandleCount > 0) {
                freeHandles[--freeHandleCount]
            } else {
                ensureHandleCapacity(nextHandle + 1)
                nextHandle++
                nextHandle - 1
            }
        ensureTopologyScratch(handle + 1)
        adjacencySizes[handle] = 0
        if (adjacencies[handle] == null) {
            adjacencies[handle] = IntArray(INITIAL_ADJACENCY_CAPACITY)
        }
        return handle
    }

    private fun freeHandle(handle: Int) {
        if (freeHandleCount == freeHandles.size) {
            freeHandles = freeHandles.copyOf(freeHandles.size shl 1)
        }
        freeHandles[freeHandleCount++] = handle
    }

    private fun ensureHandleCapacity(required: Int) {
        if (required <= handleNetworks.size) {
            return
        }
        var newCapacity = handleNetworks.size
        while (newCapacity < required) {
            newCapacity = newCapacity shl 1
        }
        val oldCapacity = handleNetworks.size
        handleNetworks =
            handleNetworks.copyOf(newCapacity).also {
                Arrays.fill(it, oldCapacity, newCapacity, -1)
            }
        handleSlots =
            handleSlots.copyOf(newCapacity).also {
                Arrays.fill(it, oldCapacity, newCapacity, -1)
            }
        adjacencies = adjacencies.copyOf(newCapacity)
        adjacencySizes = adjacencySizes.copyOf(newCapacity)
        visited = visited.copyOf(newCapacity)
        visitOrder = visitOrder.copyOf(newCapacity)
        componentStarts = componentStarts.copyOf(newCapacity)
        componentSizes = componentSizes.copyOf(newCapacity)
        demandScratch = demandScratch.copyOf(newCapacity)
        allocationScratch = allocationScratch.copyOf(newCapacity)
        weightScratch = weightScratch.copyOf(newCapacity)
        priorityScratch = priorityScratch.copyOf(newCapacity)
        if (freeHandles.size < newCapacity) {
            freeHandles = freeHandles.copyOf(newCapacity)
        }
    }

    private fun ensureTopologyScratch(required: Int) {
        if (required <= visited.size) {
            return
        }
        ensureHandleCapacity(required)
    }

    private fun nextVisitStamp(): Int {
        if (visitStamp == Int.MAX_VALUE) {
            Arrays.fill(visited, 0)
            visitStamp = 1
        } else {
            visitStamp++
        }
        return visitStamp
    }

    private fun hasAdjacency(
        handle: Int,
        neighbour: Int,
    ): Boolean {
        val adjacency = adjacencies[handle]!!
        var index = 0
        while (index < adjacencySizes[handle]) {
            if (adjacency[index] == neighbour) {
                return true
            }
            index++
        }
        return false
    }

    private fun addAdjacency(
        handle: Int,
        neighbour: Int,
    ) {
        val required = adjacencySizes[handle] + 1
        var adjacency = adjacencies[handle]!!
        if (required > adjacency.size) {
            adjacency = adjacency.copyOf(adjacency.size shl 1)
            adjacencies[handle] = adjacency
        }
        adjacency[adjacencySizes[handle]++] = neighbour
    }

    private fun removeAdjacency(
        handle: Int,
        neighbour: Int,
    ) {
        val adjacency = adjacencies[handle]!!
        var index = 0
        while (index < adjacencySizes[handle]) {
            if (adjacency[index] == neighbour) {
                val last = --adjacencySizes[handle]
                adjacency[index] = adjacency[last]
                return
            }
            index++
        }
    }

    private fun proportionalShare(
        budget: Long,
        weight: Long,
        total: Long,
    ): Long {
        if (budget <= 0L || weight <= 0L || total <= 0L) {
            return 0L
        }
        if (budget >= total) {
            return weight
        }
        val product = budget * weight
        if (budget <= Long.MAX_VALUE / weight) {
            return product / total
        }

        val high = Math.multiplyHigh(budget, weight)
        var remainder = high
        var quotient = 0L
        var bit = Long.SIZE_BITS - 1
        while (bit >= 0) {
            val incoming = (product ushr bit) and 1L
            val complement = total - remainder
            if (remainder >= complement) {
                remainder = remainder - complement + incoming
                quotient = quotient or (1L shl bit)
            } else {
                remainder = remainder + remainder + incoming
            }
            bit--
        }
        return quotient
    }

    private fun scaleDown(
        amount: Long,
        factor: Int,
    ): Long {
        if (amount <= 0L || factor <= 0) {
            return 0L
        }
        val quotient = amount / ENERGY_SCALE
        val remainder = amount % ENERGY_SCALE
        return quotient * factor + remainder * factor / ENERGY_SCALE
    }

    private fun requiredRaw(
        delivered: Long,
        factor: Int,
    ): Long {
        if (delivered <= 0L) {
            return 0L
        }
        val quotient = delivered / factor
        val remainder = delivered % factor
        val whole = saturatedMultiply(quotient, ENERGY_SCALE)
        val fractionalNumerator = remainder * ENERGY_SCALE
        val fractional = (fractionalNumerator + factor - 1L) / factor
        return saturatedAdd(whole, fractional)
    }

    private fun saturatedAdd(
        first: Long,
        second: Long,
    ): Long {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE
        }
        return first + second
    }

    private fun saturatedMultiply(
        first: Long,
        second: Long,
    ): Long {
        if (first == 0L || second == 0L) {
            return 0L
        }
        if (first > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE
        }
        return first * second
    }

    companion object {
        private const val ENERGY_SCALE = 1_000_000L
        private const val MAX_NETWORK_LOSS = 900_000
        private const val INITIAL_ADJACENCY_CAPACITY = 4
    }
}
