package exposed.r2dbc.examples.cache.ktor

import exposed.r2dbc.examples.cache.ktor.config.KtorCacheJson
import exposed.r2dbc.examples.cache.ktor.config.KtorCacheResources
import exposed.r2dbc.examples.cache.ktor.config.KtorRedissonFactory
import exposed.r2dbc.examples.cache.ktor.domain.CacheStatsRecord
import exposed.r2dbc.examples.cache.ktor.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.domain.InvalidationResponse
import exposed.r2dbc.examples.cache.ktor.domain.StructuredError
import exposed.r2dbc.examples.cache.ktor.domain.UpsertUserRequest
import exposed.r2dbc.examples.cache.ktor.domain.UserCacheResponse
import exposed.r2dbc.examples.cache.ktor.domain.UserRecord
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.ktor.testing.bluetape4kJsonClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorCacheStrategiesApplicationTest {

    private lateinit var cacheName: String

    @BeforeEach
    fun beforeEach() {
        cacheName = "exposed:ktor:r2dbc:users:test:${UUID.randomUUID().toString().replace("-", "")}"
        clearCache(cacheName)
    }

    @AfterEach
    fun afterEach() {
        clearCache(cacheName)
    }

    @Test
    fun `first read misses and second read hits cache`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("read-hit"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        val first = client.get("/users/1").body<UserCacheResponse>()
        first.cacheStatus shouldBeEqualTo CacheStatus.MISS
        first.user.shouldNotBeNull().id shouldBeEqualTo 1L

        val second = client.get("/users/1").body<UserCacheResponse>()
        second.cacheStatus shouldBeEqualTo CacheStatus.HIT
        second.user.shouldNotBeNull().id shouldBeEqualTo 1L

        val stats = client.get("/cache/stats").body<CacheStatsRecord>()
        stats.misses shouldBeEqualTo 1L
        stats.hits shouldBeEqualTo 1L
    }

    @Test
    fun `cache invalidation preserves database fallback`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("invalidate"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        client.get("/users/2").body<UserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.MISS
        client.get("/users/2").body<UserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.HIT

        val invalidated = client.delete("/users/2/cache").body<InvalidationResponse>()
        invalidated.invalidated shouldBeEqualTo 1L

        val fallback = client.get("/users/2").body<UserCacheResponse>()
        fallback.cacheStatus shouldBeEqualTo CacheStatus.MISS
        fallback.user.shouldNotBeNull().id shouldBeEqualTo 2L
    }

    @Test
    fun `create and update refresh cache`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("write-through"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        val created = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("dana.cache", "Dana", "Writer", "Daegu", "41001", "1994-07-08"))
        }
        created.status shouldBeEqualTo HttpStatusCode.Created
        val createdBody = created.body<UserCacheResponse>()
        createdBody.cacheStatus shouldBeEqualTo CacheStatus.WRITTEN
        val createdUser = createdBody.user.shouldNotBeNull()
        createdUser.id shouldBeGreaterThan 3L

        val cachedCreated = client.get("/users/${createdUser.id}").body<UserCacheResponse>()
        cachedCreated.cacheStatus shouldBeEqualTo CacheStatus.HIT

        val updated = client.put("/users/${createdUser.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("dana.cache", "Dana", "Updated", "Daegu", "41001", "1994-07-08"))
        }.body<UserCacheResponse>()
        updated.cacheStatus shouldBeEqualTo CacheStatus.WRITTEN
        updated.user.shouldNotBeNull().lastName shouldBeEqualTo "Updated"

        val cachedUpdated = client.get("/users/${createdUser.id}").body<UserCacheResponse>()
        cachedUpdated.cacheStatus shouldBeEqualTo CacheStatus.HIT
        cachedUpdated.user.shouldNotBeNull().lastName shouldBeEqualTo "Updated"

        val missingUpdate = client.put("/users/999") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("missing.cache", "Missing", "User"))
        }
        missingUpdate.status shouldBeEqualTo HttpStatusCode.NotFound
        missingUpdate.body<UserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.NOT_FOUND
    }

    @Test
    fun `clear cache keeps database fallback`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("clear"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        client.get("/users/1").body<UserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.MISS
        client.get("/users/2").body<UserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.MISS

        val cleared = client.delete("/users/cache").body<InvalidationResponse>()
        cleared.invalidated shouldBeEqualTo 2L

        val fallback = client.get("/users/1").body<UserCacheResponse>()
        fallback.cacheStatus shouldBeEqualTo CacheStatus.MISS
        fallback.user.shouldNotBeNull().id shouldBeEqualTo 1L
    }

    @Test
    fun `list users reads database directly`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("list"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        val users = client.get("/users").body<List<UserRecord>>()

        users shouldHaveSize 3
        val stats = client.get("/cache/stats").body<CacheStatsRecord>()
        stats.hits shouldBeEqualTo 0L
        stats.misses shouldBeEqualTo 0L
    }

    @Test
    fun `missing and invalid ids return structured responses`() = testApplication {
        application {
            ktorCacheStrategiesModule(newResources("errors"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCacheJson)

        val missing = client.get("/users/999")
        missing.status shouldBeEqualTo HttpStatusCode.NotFound
        val missingBody = missing.body<UserCacheResponse>()
        missingBody.cacheStatus shouldBeEqualTo CacheStatus.NOT_FOUND
        missingBody.user shouldBeEqualTo null

        val invalid = client.get("/users/not-a-number")
        invalid.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalid.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val invalidDate = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("bad.date", "Bad", "Date", birthDate = "2026-99-99"))
        }
        invalidDate.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalidDate.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    private fun newResources(slug: String): KtorCacheResources =
        KtorCacheResources.create(
            databaseName = "ktor_cache_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            cacheName = cacheName,
        )

    private fun clearCache(cacheName: String) {
        val redisson = KtorRedissonFactory.create()
        try {
            redisson.getMap<Long, UserRecord>(cacheName).clear()
        } finally {
            redisson.shutdown()
        }
    }

}
