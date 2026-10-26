package studio.craftory.tech.machine

import java.util.concurrent.atomic.AtomicBoolean

internal object SelfTestStartup {
    @Suppress("TooGenericExceptionCaught")
    fun <T : Any> execute(
        create: () -> T,
        start: (T) -> Unit,
        onFailure: (T?, Exception) -> Unit,
    ) {
        var value: T? = null
        try {
            value = create()
            start(value)
        } catch (failure: Exception) {
            onFailure(value, failure)
        }
    }
}

internal class SelfTestCompletion(
    private val onComplete: () -> Unit,
) {
    private val completed = AtomicBoolean(false)

    fun complete() {
        if (completed.compareAndSet(false, true)) runCatching { onComplete() }
    }
}
