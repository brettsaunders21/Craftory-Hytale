plugins {
    id("craftory.kotlin")
    alias(libs.plugins.jmh)
}

jmh {
    warmupIterations = 2
    iterations = 3
    fork = 1
}
