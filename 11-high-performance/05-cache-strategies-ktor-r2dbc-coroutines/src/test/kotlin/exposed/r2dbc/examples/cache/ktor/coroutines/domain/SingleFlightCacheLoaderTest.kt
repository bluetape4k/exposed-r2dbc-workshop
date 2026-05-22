package exposed.r2dbc.examples.cache.ktor.coroutines.domain

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class SingleFlightCacheLoaderTest {

    @Test
    fun `concurrent callers share one producer`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var producerCalls = 0
        val loader = SingleFlightCacheLoader<String, String>(scope = this)

        val primary = async {
            loader.load("user:1") {
                producerCalls += 1
                entered.complete(Unit)
                release.await()
                "alice"
            }
        }
        entered.await()

        val shared = (1..3).map {
            async {
                loader.load("user:1") {
                    producerCalls += 1
                    "duplicate"
                }
            }
        }
        release.complete(Unit)

        primary.await() shouldBeEqualTo SingleFlightLoad.Primary("alice")
        shared.awaitAll().forEach { it shouldBeEqualTo SingleFlightLoad.Shared("alice") }
        producerCalls shouldBeEqualTo 1
        loader.inFlightSize shouldBeEqualTo 0
    }

    @Test
    fun `cancelled owner waiter does not cancel shared producer`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cancellations = 0
        val loader = SingleFlightCacheLoader<String, String>(
            scope = this,
            onCallerCancelled = { cancellations += 1 },
        )

        val owner = async {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10.milliseconds) {
                    loader.load("user:2") {
                        entered.complete(Unit)
                        release.await()
                        "brad"
                    }
                }
            }
        }
        entered.await()
        val waiter = async {
            loader.load("user:2") {
                "duplicate"
            }
        }

        advanceTimeBy(10)
        owner.await()
        release.complete(Unit)

        waiter.await() shouldBeEqualTo SingleFlightLoad.Shared("brad")
        cancellations shouldBeEqualTo 1
        loader.inFlightSize shouldBeEqualTo 0
    }

    @Test
    fun `failed producer is removed so retry can succeed`() = runTest {
        val producerScope = CoroutineScope(coroutineContext + SupervisorJob())
        val loader = SingleFlightCacheLoader<String, String>(scope = producerScope)
        var calls = 0

        try {
            assertFailsWith<IllegalStateException> {
                loader.load("user:3") {
                    calls += 1
                    error("first failure")
                }
            }

            loader.inFlightSize shouldBeEqualTo 0
            loader.load("user:3") {
                calls += 1
                "carol"
            } shouldBeEqualTo SingleFlightLoad.Primary("carol")
            calls shouldBeEqualTo 2
        } finally {
            producerScope.cancel()
        }
    }

    @Test
    fun `producer timeout is removed so retry can succeed`() = runTest {
        val producerScope = CoroutineScope(coroutineContext + SupervisorJob())
        val loader = SingleFlightCacheLoader<String, String>(
            scope = producerScope,
            producerTimeout = 10.milliseconds,
        )

        try {
            assertFailsWith<CacheLoadTimeoutException> {
                loader.load("user:4") {
                    delay(100)
                    "dana"
                }
            }

            loader.inFlightSize shouldBeEqualTo 0
            loader.load("user:4") { "dana" } shouldBeEqualTo SingleFlightLoad.Primary("dana")
        } finally {
            producerScope.cancel()
        }
    }
}
