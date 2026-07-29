package exposed.r2dbc.examples.cache.ktor.coroutines.routes

import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineCacheObservation
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineCacheReadResult
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.InvalidationResponse
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.UpsertUserRequest
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheRepository
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/**
 * 코루틴 기반 캐시 hit/miss/coalescing과 무효화 결과를 관찰할 수 있는 사용자 route를 등록합니다.
 */
fun Application.coroutineUserCacheRoutes(
    repository: CoroutineUserCacheRepository,
    observation: CoroutineCacheObservation,
) {
    routing {
        get("/users") {
            call.respond(repository.findAllFromDb())
        }

        get("/users/{id}") {
            val id = call.pathUserId()
            val result = repository.findCached(id, call.loadDelayMillis())
            observation.record(result)
            when (result) {
                is CoroutineCacheReadResult.Hit -> call.respond(
                    CoroutineUserCacheResponse(cacheStatus = CacheStatus.HIT, user = result.user)
                )

                is CoroutineCacheReadResult.Miss -> call.respond(
                    CoroutineUserCacheResponse(cacheStatus = CacheStatus.MISS, user = result.user)
                )

                is CoroutineCacheReadResult.Coalesced -> call.respond(
                    CoroutineUserCacheResponse(cacheStatus = CacheStatus.COALESCED, user = result.user)
                )

                CoroutineCacheReadResult.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    CoroutineUserCacheResponse(cacheStatus = CacheStatus.NOT_FOUND, user = null)
                )
            }
        }

        post("/users") {
            val user = repository.create(call.receive<UpsertUserRequest>())
            call.respond(
                HttpStatusCode.Created,
                CoroutineUserCacheResponse(cacheStatus = CacheStatus.WRITTEN, user = user)
            )
        }

        put("/users/{id}") {
            val id = call.pathUserId()
            val user = repository.update(id, call.receive<UpsertUserRequest>())
            call.respond(CoroutineUserCacheResponse(cacheStatus = CacheStatus.WRITTEN, user = user))
        }

        delete("/users/{id}/cache") {
            val id = call.pathUserId()
            call.respond(InvalidationResponse(repository.invalidate(id)))
        }

        delete("/users/cache") {
            call.respond(InvalidationResponse(repository.clearCache()))
        }

        get("/cache/stats") {
            call.respond(observation.snapshot())
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.pathUserId(): Long =
    parameters["id"]?.toLongOrNull()
        ?: throw IllegalArgumentException("id must be a numeric identifier")

private fun io.ktor.server.application.ApplicationCall.loadDelayMillis(): Long {
    val delayMillis = request.queryParameters["loadDelayMillis"] ?: return 0L
    return delayMillis.toLongOrNull()
        ?: throw IllegalArgumentException("loadDelayMillis must be a number")
}
