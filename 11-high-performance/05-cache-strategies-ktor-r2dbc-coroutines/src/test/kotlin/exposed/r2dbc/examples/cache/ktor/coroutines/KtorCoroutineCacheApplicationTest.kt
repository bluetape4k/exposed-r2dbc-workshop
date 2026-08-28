package exposed.r2dbc.examples.cache.ktor.coroutines

import exposed.r2dbc.examples.cache.ktor.coroutines.config.KtorCoroutineCacheJson
import exposed.r2dbc.examples.cache.ktor.coroutines.config.KtorCoroutineCacheResources
import exposed.r2dbc.examples.cache.ktor.coroutines.config.KtorCoroutineRedissonFactory
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineCacheReadResult
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineCacheStatsRecord
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheRepository
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheResponse
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.InvalidationResponse
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.StructuredError
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UpsertUserRequest
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UserRecord
import io.bluetape4k.junit5.coroutines.runSuspendIO
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtorCoroutineCacheApplicationTest {

    private lateinit var cacheName: String

    @BeforeEach
    fun beforeEach() {
        cacheName = "exposed:ktor:r2dbc:coroutine:users:test:${UUID.randomUUID().toString().replace("-", "")}"
        clearCache(cacheName)
    }

    @AfterEach
    fun afterEach() {
        clearCache(cacheName)
    }

    @Test
    fun `first read misses and second read hits cache`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("read-hit"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val first = client.get("/users/1").body<CoroutineUserCacheResponse>()
        first.cacheStatus shouldBeEqualTo CacheStatus.MISS
        first.user.shouldNotBeNull().id shouldBeEqualTo 1L

        val second = client.get("/users/1").body<CoroutineUserCacheResponse>()
        second.cacheStatus shouldBeEqualTo CacheStatus.HIT
        second.user.shouldNotBeNull().id shouldBeEqualTo 1L

        val stats = client.get("/cache/stats").body<CoroutineCacheStatsRecord>()
        stats.misses shouldBeEqualTo 1L
        stats.hits shouldBeEqualTo 1L
    }

    @Test
    fun `concurrent cold reads coalesce behind one fallback`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("coalesce"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val responses = coroutineScope {
            (1..8)
                .map {
                    async {
                        client.get("/users/1?loadDelayMillis=200").body<CoroutineUserCacheResponse>()
                    }
                }
                .awaitAll()
        }

        responses.count { it.cacheStatus == CacheStatus.MISS } shouldBeEqualTo 1
        responses.count { it.cacheStatus == CacheStatus.COALESCED } shouldBeGreaterThan 0
        responses.forEach { it.user.shouldNotBeNull().id shouldBeEqualTo 1L }

        val stats = client.get("/cache/stats").body<CoroutineCacheStatsRecord>()
        stats.misses shouldBeEqualTo 1L
        stats.coalesced shouldBeGreaterThan 0L
    }

    @Test
    fun `producer timeout returns 503 and later retry succeeds`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("timeout", producerTimeout = 10.milliseconds))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val timeout = client.get("/users/1?loadDelayMillis=100")
        timeout.status shouldBeEqualTo HttpStatusCode.ServiceUnavailable
        timeout.body<StructuredError>().code shouldBeEqualTo "CACHE_LOAD_TIMEOUT"

        val retry = client.get("/users/1").body<CoroutineUserCacheResponse>()
        retry.cacheStatus shouldBeEqualTo CacheStatus.MISS
        retry.user.shouldNotBeNull().id shouldBeEqualTo 1L

        val stats = client.get("/cache/stats").body<CoroutineCacheStatsRecord>()
        stats.loadFailures shouldBeEqualTo 1L
    }

    @Test
    fun `cache write failure returns database row and records diagnostic`() = runSuspendIO {
        val resources = newResources("cache-write-failure")
        try {
            val repository = CoroutineUserCacheRepository(
                database = resources.database,
                cache = FailingPutCacheStore,
                observation = resources.observation,
                loader = resources.loader,
                awaitReady = resources::awaitInitialized,
            )

            val result = repository.findCached(1)

            result as CoroutineCacheReadResult.Miss
            result.user.id shouldBeEqualTo 1L
            resources.observation.snapshot().cacheWriteFailures shouldBeEqualTo 1L
        } finally {
            resources.close()
        }
    }

    @Test
    fun `cache invalidation and clear preserve database fallback`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("invalidate"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        client.get("/users/2").body<CoroutineUserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.MISS
        client.get("/users/2").body<CoroutineUserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.HIT

        val invalidated = client.delete("/users/2/cache").body<InvalidationResponse>()
        invalidated.invalidated shouldBeEqualTo 1L

        val fallback = client.get("/users/2").body<CoroutineUserCacheResponse>()
        fallback.cacheStatus shouldBeEqualTo CacheStatus.MISS
        fallback.user.shouldNotBeNull().id shouldBeEqualTo 2L

        client.get("/users/1").body<CoroutineUserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.MISS
        val cleared = client.delete("/users/cache").body<InvalidationResponse>()
        cleared.invalidated shouldBeGreaterThan 0L
    }

    @Test
    fun `create and update refresh cache`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("write-through"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val created = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("dana.cache", "Dana", "Writer", "Daegu", "41001", "1994-07-08"))
        }
        created.status shouldBeEqualTo HttpStatusCode.Created
        val createdBody = created.body<CoroutineUserCacheResponse>()
        createdBody.cacheStatus shouldBeEqualTo CacheStatus.WRITTEN
        val createdUser = createdBody.user.shouldNotBeNull()
        createdUser.id shouldBeGreaterThan 3L

        val cachedCreated = client.get("/users/${createdUser.id}").body<CoroutineUserCacheResponse>()
        cachedCreated.cacheStatus shouldBeEqualTo CacheStatus.HIT

        val updated = client.put("/users/${createdUser.id}") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("dana.cache", "Dana", "Updated", "Daegu", "41001", "1994-07-08"))
        }.body<CoroutineUserCacheResponse>()
        updated.cacheStatus shouldBeEqualTo CacheStatus.WRITTEN
        updated.user.shouldNotBeNull().lastName shouldBeEqualTo "Updated"

        val cachedUpdated = client.get("/users/${createdUser.id}").body<CoroutineUserCacheResponse>()
        cachedUpdated.cacheStatus shouldBeEqualTo CacheStatus.HIT
        cachedUpdated.user.shouldNotBeNull().lastName shouldBeEqualTo "Updated"

        val missingUpdate = client.put("/users/999") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("missing.cache", "Missing", "User"))
        }
        missingUpdate.status shouldBeEqualTo HttpStatusCode.NotFound
        missingUpdate.body<CoroutineUserCacheResponse>().cacheStatus shouldBeEqualTo CacheStatus.NOT_FOUND
    }

    @Test
    fun `list users reads database directly`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("list"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val users = client.get("/users").body<List<UserRecord>>()

        users shouldHaveSize 3
        val stats = client.get("/cache/stats").body<CoroutineCacheStatsRecord>()
        stats.hits shouldBeEqualTo 0L
        stats.misses shouldBeEqualTo 0L
        stats.coalesced shouldBeEqualTo 0L
    }

    @Test
    fun `missing and invalid requests return structured responses`() = testApplication {
        application {
            ktorCoroutineCacheModule(newResources("errors"))
        }
        val client = bluetape4kJsonClient(jsonFormat = KtorCoroutineCacheJson)

        val missing = client.get("/users/999")
        missing.status shouldBeEqualTo HttpStatusCode.NotFound
        val missingBody = missing.body<CoroutineUserCacheResponse>()
        missingBody.cacheStatus shouldBeEqualTo CacheStatus.NOT_FOUND
        missingBody.user shouldBeEqualTo null

        val invalid = client.get("/users/not-a-number")
        invalid.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalid.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val invalidDelay = client.get("/users/1?loadDelayMillis=1001")
        invalidDelay.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalidDelay.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"

        val invalidDate = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody(UpsertUserRequest("bad.date", "Bad", "Date", birthDate = "2026-99-99"))
        }
        invalidDate.status shouldBeEqualTo HttpStatusCode.BadRequest
        invalidDate.body<StructuredError>().code shouldBeEqualTo "INVALID_REQUEST"
    }

    private fun newResources(
        slug: String,
        producerTimeout: Duration = 5_000.milliseconds,
    ): KtorCoroutineCacheResources =
        KtorCoroutineCacheResources.create(
            databaseName = "ktor_coroutine_cache_${slug}_${UUID.randomUUID().toString().replace("-", "")}",
            cacheName = cacheName,
            producerTimeout = producerTimeout,
        )

    private fun clearCache(cacheName: String) {
        val redisson = KtorCoroutineRedissonFactory.create()
        try {
            redisson.getMap<Long, UserRecord>(cacheName).clear()
        } finally {
            redisson.shutdown()
        }
    }

    private object FailingPutCacheStore: CoroutineUserCacheRepository.CoroutineUserCacheStore {
        override suspend fun get(id: Long): UserRecord? = null

        override suspend fun put(id: Long, user: UserRecord) {
            error("simulated cache write failure")
        }

        override suspend fun remove(id: Long): Long = 0L

        override suspend fun clear(): Long = 0L
    }
}
