package exposed.r2dbc.examples.springbootrepository.config

import exposed.r2dbc.examples.springbootrepository.domain.Products
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.error
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * 애플리케이션 준비 시 Product schema와 결정적인 최소 fixture를 한 번 초기화합니다.
 */
@Component
class ProductDataInitializer(
    private val database: org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase,
    private val databaseCoroutineDispatcher: CoroutineDispatcher,
) : ApplicationListener<ApplicationReadyEvent> {

    companion object : KLoggingChannel()

    private val initialized = AtomicBoolean(false)

    /** 애플리케이션 준비 이벤트에서 schema와 fixture를 bounded transaction으로 준비합니다. */
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        if (!initialized.compareAndSet(false, true)) return

        try {
            runBlocking(databaseCoroutineDispatcher) {
                withTimeout(15.seconds) {
                    suspendTransaction(db = database) {
                        SchemaUtils.create(Products)
                        if (Products.selectAll().firstOrNull() == null) {
                            Products.insert {
                                it[name] = "Notebook"
                                it[description] = "Deterministic H2 fixture"
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            initialized.set(false)
            log.error { "product_initializer_failed type=${failure::class.simpleName}" }
            throw failure
        }
    }
}
