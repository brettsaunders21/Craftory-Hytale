package studio.craftory.tech.machine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SelfTestStartupTest {
    @Test
    fun `startup wrapper reports constructor and start failures`() {
        var constructorFailure: Exception? = null
        SelfTestStartup.execute(
            create = { error("layout failed") },
            start = {},
            onFailure = { instance, failure ->
                assertNull(instance)
                constructorFailure = failure
            },
        )
        assertEquals("layout failed", constructorFailure?.message)

        var startFailure: Exception? = null
        SelfTestStartup.execute(
            create = { "run" },
            start = { error("start failed") },
            onFailure = { instance, failure ->
                assertEquals("run", instance)
                startFailure = failure
            },
        )
        assertEquals("start failed", startFailure?.message)
    }

    @Test
    fun `completion callback fires once after successful startup`() {
        var completions = 0
        val completion = SelfTestCompletion { completions++ }

        SelfTestStartup.execute(
            create = { "run" },
            start = { completion.complete() },
            onFailure = { _, _ -> completion.complete() },
        )
        completion.complete()

        assertEquals(1, completions)
    }

    @Test
    fun `completion callback fires once after setup failure`() {
        var completions = 0
        val completion = SelfTestCompletion { completions++ }

        SelfTestStartup.execute(
            create = { error("layout failed") },
            start = { completion.complete() },
            onFailure = { _, _ -> completion.complete() },
        )
        completion.complete()

        assertEquals(1, completions)
    }
}
