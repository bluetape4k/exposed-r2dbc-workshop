package exposed.r2dbc.examples.cache.caffeine

import exposed.r2dbc.examples.cache.caffeine.config.R2dbcCaffeineJson
import exposed.r2dbc.examples.cache.caffeine.config.R2dbcCaffeineResources
import exposed.r2dbc.examples.cache.caffeine.domain.CacheReadStatus
import exposed.r2dbc.examples.cache.caffeine.domain.CacheHealthResponse
import exposed.r2dbc.examples.cache.caffeine.domain.ProductReadResponse
import exposed.r2dbc.examples.cache.caffeine.domain.ProductRecord
import exposed.r2dbc.examples.cache.caffeine.domain.StructuredError
import exposed.r2dbc.examples.cache.caffeine.domain.UpdateProductRequest
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.exposed.cache.CacheWriteMode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

class R2dbcCaffeineCacheApplicationTest {

    @Test
    fun `read miss becomes hit and unknown sku is not found`() = testApplication {
        application {
            r2dbcCaffeineCacheModule(newResources("read-hit"))
        }
        val client = createJsonClient()

        val first = client.get("/products/sku-1").body<ProductReadResponse>()
        first.cache shouldBeEqualTo CacheReadStatus.MISS
        first.product.sku shouldBeEqualTo "sku-1"

        val second = client.get("/products/sku-1").body<ProductReadResponse>()
        second.cache shouldBeEqualTo CacheReadStatus.HIT
        second.product shouldBeEqualTo first.product

        val missing = client.get("/products/unknown")
        missing.status shouldBeEqualTo HttpStatusCode.NotFound
        missing.body<StructuredError>().code shouldBeEqualTo "NOT_FOUND"
    }

    @Test
    fun `single key invalidation and clear force database misses`() = testApplication {
        application {
            r2dbcCaffeineCacheModule(newResources("invalidation"))
        }
        val client = createJsonClient()

        client.get("/products/sku-1").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.MISS
        client.get("/products/sku-1").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.HIT
        client.delete("/products/sku-1/cache").status shouldBeEqualTo HttpStatusCode.NoContent
        client.get("/products/sku-1").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.MISS

        client.get("/products/sku-2").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.MISS
        client.delete("/products/cache").status shouldBeEqualTo HttpStatusCode.NoContent
        client.get("/products/sku-1").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.MISS
        client.get("/products/sku-2").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.MISS
    }

    @Test
    fun `read only update changes cache while database remains the source value`() = testApplication {
        val resources = newResources("read-only", CacheWriteMode.READ_ONLY)
        application {
            r2dbcCaffeineCacheModule(resources)
        }
        val client = createJsonClient()

        client.get("/products/sku-1")
        val updated = client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("cached name"))
        }.body<ProductRecord>()

        updated.name shouldBeEqualTo "cached name"
        resources.repository.findByIdFromDb("sku-1")?.name shouldBeEqualTo "Product 1"
        client.get("/products/sku-1").body<ProductReadResponse>().let {
            it.cache shouldBeEqualTo CacheReadStatus.HIT
            it.product.name shouldBeEqualTo "cached name"
        }
    }

    @Test
    fun `write through update changes cache and database immediately`() = testApplication {
        val resources = newResources("write-through", CacheWriteMode.WRITE_THROUGH)
        application {
            r2dbcCaffeineCacheModule(resources)
        }
        val client = createJsonClient()

        val updated = client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("database name"))
        }.body<ProductRecord>()

        updated.version shouldBeEqualTo 2
        resources.repository.findByIdFromDb("sku-1") shouldBeEqualTo updated
        client.get("/products/sku-1").body<ProductReadResponse>().cache shouldBeEqualTo CacheReadStatus.HIT
    }

    @Test
    fun `write behind publishes cache and eventually drains to database`() = testApplication {
        val resources = newResources("write-behind", CacheWriteMode.WRITE_BEHIND)
        application {
            r2dbcCaffeineCacheModule(resources)
        }
        val client = createJsonClient()

        val updated = client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("queued name"))
        }.body<ProductRecord>()

        client.get("/products/sku-1").body<ProductReadResponse>().let {
            it.cache shouldBeEqualTo CacheReadStatus.HIT
            it.product shouldBeEqualTo updated
        }
        val health = awaitHealth(client)
        health.queueDepth shouldBeEqualTo 0
        health.lastFlushError shouldBeEqualTo null
        resources.repository.findByIdFromDb("sku-1") shouldBeEqualTo updated
    }

    @Test
    fun `database list bypasses cache and invalid input returns structured errors`() = testApplication {
        val resources = newResources("list-and-errors", CacheWriteMode.READ_ONLY)
        application {
            r2dbcCaffeineCacheModule(resources)
        }
        val client = createJsonClient()

        client.get("/products/sku-1")
        client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("only cache"))
        }
        val products = client.get("/products").body<List<ProductRecord>>()
        products shouldHaveSize 2
        products.single { it.sku == "sku-1" }.name shouldBeEqualTo "Product 1"

        val blank = client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("  "))
        }
        blank.status shouldBeEqualTo HttpStatusCode.BadRequest
        blank.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val malformed = client.put("/products/sku-1") {
            contentType(ContentType.Application.Json)
            setBody("{\"name\":")
        }
        malformed.status shouldBeEqualTo HttpStatusCode.BadRequest
        malformed.body<StructuredError>().code shouldBeEqualTo "INVALID_JSON"

        val missing = client.put("/products/unknown") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProductRequest("missing"))
        }
        missing.status shouldBeEqualTo HttpStatusCode.NotFound
        missing.body<StructuredError>().code shouldBeEqualTo "NOT_FOUND"
    }

    private suspend fun awaitHealth(client: HttpClient): CacheHealthResponse = withTimeout(5_000.milliseconds) {
        suspend fun poll(): CacheHealthResponse {
            val health = client.get("/cache/health").body<CacheHealthResponse>()
            return if (health.queueDepth == 0 && health.lastFlushError == null) {
                health
            } else {
                delay(10)
                poll()
            }
        }
        poll()
    }

    private fun newResources(slug: String, mode: CacheWriteMode = CacheWriteMode.WRITE_THROUGH): R2dbcCaffeineResources =
        R2dbcCaffeineResources.create(
            databaseName = "r2dbc_caffeine_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            writeMode = mode,
        )

    private fun ApplicationTestBuilder.createJsonClient(): HttpClient = createClient {
        install(ContentNegotiation) {
            json(R2dbcCaffeineJson)
        }
    }
}
