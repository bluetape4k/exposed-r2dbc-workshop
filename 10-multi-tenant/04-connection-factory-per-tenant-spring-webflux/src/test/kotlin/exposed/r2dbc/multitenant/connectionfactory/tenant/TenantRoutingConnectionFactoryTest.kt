package exposed.r2dbc.multitenant.connectionfactory.tenant

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.tenant.TenantId
import io.bluetape4k.tenant.reactor.ReactorTenantContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import reactor.util.context.ContextView
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TenantRoutingConnectionFactoryTest {

    companion object: KLoggingChannel() {
        val FIXTURE_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

    @Test
    fun `deterministic reactor tenant fixture preserves interleaved subscriptions`() {
        deterministicReactorTenantFixture()
    }

    @Test
    fun `cancelled reactor tenant fixture restores empty subscriber context`() {
        deterministicReactorCancellationFixture()
    }

    @Test
    fun `no reactor tenant uses default tenant factory`() {
        val factory = routingFactory()

        StepVerifier
            .create(Mono.from(factory.create()))
            .expectErrorSatisfies {
                (it as SelectedTenantException).tenantId shouldBeEqualTo Tenants.Tenant.KOREAN.id
            }
            .verify()
    }

    @Test
    fun `known reactor tenant routes to matching factory`() {
        val factory = routingFactory()

        StepVerifier
            .create(
                Mono
                    .from(factory.create())
                    .contextWrite { context ->
                        ReactorTenantContext.withTenant(context, TenantId(Tenants.Tenant.ENGLISH.id))
                    },
            )
            .expectErrorSatisfies {
                (it as SelectedTenantException).tenantId shouldBeEqualTo Tenants.Tenant.ENGLISH.id
            }
            .verify()
    }

    @Test
    fun `unknown emitted tenant fails with lenient fallback disabled`() {
        val factory = routingFactory()

        StepVerifier
            .create(
                Mono
                    .from(factory.create())
                    .contextWrite { context ->
                        ReactorTenantContext.withTenant(context, TenantId("unknown"))
                    },
            )
            .expectError(IllegalStateException::class.java)
            .verify()
    }

    private fun routingFactory(): TenantRoutingConnectionFactory =
        TenantRoutingConnectionFactory().apply {
            val korean = FailingConnectionFactory(Tenants.Tenant.KOREAN.id)
            val english = FailingConnectionFactory(Tenants.Tenant.ENGLISH.id)
            setTargetConnectionFactories(
                mapOf(
                    Tenants.Tenant.KOREAN.id to korean,
                    Tenants.Tenant.ENGLISH.id to english,
                ),
            )
            setDefaultTargetConnectionFactory(korean)
            setLenientFallback(false)
            afterPropertiesSet()
        }

    private class FailingConnectionFactory(private val tenantId: String): ConnectionFactory {

        override fun create(): Publisher<out Connection> =
            Mono.error(SelectedTenantException(tenantId))

        override fun getMetadata(): ConnectionFactoryMetadata =
            ConnectionFactoryMetadata { "tenant-$tenantId" }
    }

    private class SelectedTenantException(val tenantId: String): RuntimeException(tenantId)

    private fun deterministicReactorTenantFixture() {
        val recorder = TenantTraceRecorder { trace -> log.info { trace.toLogLine() } }
        val ready = Sinks.one<Unit>()
        val release = Sinks.one<Unit>()
        val arrivals = AtomicInteger()
        val subscriptions = 2
        val fixtureScheduler = Schedulers.newSingle("tenant-fixture-reused")

        fun probe(subscriptionId: String, tenant: Tenants.Tenant): Mono<Unit> =
            Mono.deferContextual { context ->
                val expectedTenant = context.tenantOrNull()
                    ?: error("fixture subscription is missing tenant context")
                recorder.record(
                    expectedTenant = expectedTenant,
                    observedTenant = expectedTenant,
                    carrier = "Reactor.ContextView",
                    subscriptionId = subscriptionId,
                    dispatcher = "test-subscriber",
                    phase = TracePhase.START,
                )
                if (arrivals.incrementAndGet() == subscriptions) {
                    ready.tryEmitValue(Unit)
                }

                release
                    .asMono()
                    .publishOn(fixtureScheduler)
                    .then(
                        Mono.deferContextual { resumedContext ->
                            recorder.record(
                                expectedTenant = expectedTenant,
                                observedTenant = resumedContext.tenantOrNull(),
                                carrier = "Reactor.ContextView",
                                subscriptionId = subscriptionId,
                                dispatcher = "tenant-fixture-reused",
                                phase = TracePhase.RESUME,
                            )

                            Mono.deferContextual { nestedContext ->
                                recorder.record(
                                    expectedTenant = expectedTenant,
                                    observedTenant = nestedContext.tenantOrNull(),
                                    carrier = "Reactor.ContextView",
                                    subscriptionId = subscriptionId,
                                    dispatcher = "tenant-fixture-reused",
                                    phase = TracePhase.NESTED_ENTER,
                                )
                                if (subscriptionId.endsWith("-failure")) {
                                    Mono.error<Unit>(IllegalStateException("fixture failure"))
                                } else {
                                    recorder.record(
                                        expectedTenant = expectedTenant,
                                        observedTenant = nestedContext.tenantOrNull(),
                                        carrier = "Reactor.ContextView",
                                        subscriptionId = subscriptionId,
                                        dispatcher = "tenant-fixture-reused",
                                        phase = TracePhase.NESTED_SUCCESS,
                                    )
                                    Mono.just(Unit)
                                }
                            }
                                .contextWrite { context ->
                                    ReactorTenantContext.withTenant(
                                        context,
                                        TenantId(otherTenant(expectedTenant)),
                                    )
                                }
                                .onErrorResume {
                                    recorder.record(
                                        expectedTenant = expectedTenant,
                                        observedTenant = resumedContext.tenantOrNull(),
                                        carrier = "Reactor.ContextView",
                                        subscriptionId = subscriptionId,
                                        dispatcher = "tenant-fixture-reused",
                                        phase = TracePhase.NESTED_FAILURE,
                                    )
                                    Mono.just(Unit)
                                }
                                .then(
                                    Mono.deferContextual { completedContext ->
                                        recorder.record(
                                            expectedTenant = expectedTenant,
                                            observedTenant = completedContext.tenantOrNull(),
                                            carrier = "Reactor.ContextView",
                                            subscriptionId = subscriptionId,
                                            dispatcher = "tenant-fixture-reused",
                                            phase = TracePhase.COMPLETE,
                                        )
                                        Mono.just(Unit)
                                    },
                                )
                        },
                    )
            }.contextWrite { context ->
                ReactorTenantContext.withTenant(context, TenantId(tenant.id))
            }

        val executor = Executors.newFixedThreadPool(subscriptions)
        try {
            val korean = CompletableFuture.supplyAsync(
                { probe("reactor-korean", Tenants.Tenant.KOREAN).block(FIXTURE_TIMEOUT) },
                executor,
            )
            val english = CompletableFuture.supplyAsync(
                { probe("reactor-english-failure", Tenants.Tenant.ENGLISH).block(FIXTURE_TIMEOUT) },
                executor,
            )

            awaitSignal(ready)
            release.tryEmitValue(Unit)
            korean.join()
            english.join()
        } finally {
            fixtureScheduler.dispose()
            executor.shutdownNow()
        }

        val expectedBySubscription = mapOf(
            "reactor-korean" to Tenants.Tenant.KOREAN.id,
            "reactor-english-failure" to Tenants.Tenant.ENGLISH.id,
        )
        expectedBySubscription.forEach { (subscriptionId, expectedTenant) ->
            val events = recorder.events.filter { it.subscriptionId == subscriptionId }
            events shouldHaveSize 5
            events
                .filter { it.phase !in setOf(TracePhase.NESTED_ENTER, TracePhase.NESTED_SUCCESS) }
                .forEach { it.observedTenant shouldBeEqualTo expectedTenant }
            events
                .filter { it.phase in setOf(TracePhase.NESTED_ENTER, TracePhase.NESTED_SUCCESS) }
                .forEach { it.observedTenant shouldBeEqualTo otherTenant(expectedTenant) }
            events.map { it.phase }.toSet() shouldBeEqualTo setOf(
                TracePhase.START,
                TracePhase.RESUME,
                TracePhase.NESTED_ENTER,
                if (subscriptionId.endsWith("-failure")) TracePhase.NESTED_FAILURE else TracePhase.NESTED_SUCCESS,
                TracePhase.COMPLETE,
            )
        }
        expectedBySubscription
            .map { (subscriptionId, _) -> recorder.events.single { it.subscriptionId == subscriptionId && it.phase == TracePhase.RESUME }.threadId }
            .toSet() shouldHaveSize 1

        val outsideTenant = AtomicReference<String?>()
        Mono.deferContextual { context ->
            outsideTenant.set(context.tenantOrNull())
            Mono.just(Unit)
        }.block(FIXTURE_TIMEOUT)
        outsideTenant.get().shouldBeNull()
    }

    private fun deterministicReactorCancellationFixture() {
        val recorder = TenantTraceRecorder { trace -> log.info { trace.toLogLine() } }
        val started = Sinks.one<Unit>()
        val cancelled = Sinks.one<Unit>()
        val subscription: Disposable = Mono.deferContextual { context ->
            val expectedTenant = context.tenantOrNull()
                ?: error("fixture subscription is missing tenant context")
            recorder.record(
                expectedTenant = expectedTenant,
                observedTenant = expectedTenant,
                carrier = "Reactor.ContextView",
                subscriptionId = "reactor-cancelled",
                dispatcher = "Schedulers.boundedElastic",
                phase = TracePhase.START,
            )
            started.tryEmitValue(Unit)
            Mono.never<Unit>().doOnCancel {
                recorder.record(
                    expectedTenant = expectedTenant,
                    observedTenant = expectedTenant,
                    carrier = "Reactor.ContextView",
                    subscriptionId = "reactor-cancelled",
                    dispatcher = "Schedulers.boundedElastic",
                    phase = TracePhase.CANCEL,
                )
                cancelled.tryEmitValue(Unit)
            }
        }
            .contextWrite { context ->
                ReactorTenantContext.withTenant(context, TenantId(Tenants.Tenant.ENGLISH.id))
            }
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe()

        try {
            awaitSignal(started)
            subscription.dispose()
            awaitSignal(cancelled)
        } finally {
            subscription.dispose()
        }

        recorder.events shouldHaveSize 2
        recorder.events.last().phase shouldBeEqualTo TracePhase.CANCEL
        recorder.events.last().observedTenant shouldBeEqualTo Tenants.Tenant.ENGLISH.id

        val outsideTenant = AtomicReference<String?>()
        Mono.deferContextual { context ->
            outsideTenant.set(context.tenantOrNull())
            Mono.just(Unit)
        }.block(FIXTURE_TIMEOUT)
        outsideTenant.get().shouldBeNull()
    }

    private fun awaitSignal(signal: Sinks.One<Unit>) {
        signal.asMono().block(FIXTURE_TIMEOUT)
    }

    private fun otherTenant(tenantId: String): String =
        when (tenantId) {
            Tenants.Tenant.KOREAN.id -> Tenants.Tenant.ENGLISH.id
            Tenants.Tenant.ENGLISH.id -> Tenants.Tenant.KOREAN.id
            else -> error("unsupported fixture tenant: $tenantId")
        }

    private fun ContextView.tenantOrNull(): String? =
        ReactorTenantContext.currentOrNull(this)?.value

    private enum class TracePhase(private val logValue: String) {
        START("start"),
        RESUME("resume"),
        NESTED_ENTER("nested-enter"),
        NESTED_SUCCESS("nested-success"),
        NESTED_FAILURE("nested-failure"),
        COMPLETE("complete"),
        CANCEL("cancel"),

        ;

        override fun toString(): String = logValue
    }

    private data class TenantTrace(
        val expectedTenant: String,
        val observedTenant: String?,
        val carrier: String,
        val threadId: Long,
        val subscriptionId: String,
        val dispatcher: String,
        val phase: TracePhase,
    ) {
        fun toLogLine(): String =
            "tenant-trace expectedTenant=$expectedTenant observedTenant=${observedTenant ?: "<none>"} " +
                "carrier=$carrier threadId=$threadId subscriptionId=$subscriptionId dispatcher=$dispatcher phase=$phase"
    }

    private class TenantTraceRecorder(
        private val emit: (TenantTrace) -> Unit,
    ) {
        val events = CopyOnWriteArrayList<TenantTrace>()

        fun record(
            expectedTenant: String,
            observedTenant: String?,
            carrier: String,
            subscriptionId: String,
            dispatcher: String,
            phase: TracePhase,
        ) {
            TenantTrace(
                expectedTenant = expectedTenant,
                observedTenant = observedTenant,
                carrier = carrier,
                threadId = Thread.currentThread().threadId(),
                subscriptionId = subscriptionId,
                dispatcher = dispatcher,
                phase = phase,
            ).also {
                events += it
                emit(it)
            }
        }
    }

}
