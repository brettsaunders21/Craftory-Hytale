package studio.craftory.tech.machine

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
