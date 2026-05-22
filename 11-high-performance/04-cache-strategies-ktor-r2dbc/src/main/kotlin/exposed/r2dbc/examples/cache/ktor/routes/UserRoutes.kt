package exposed.r2dbc.examples.cache.ktor.routes

import exposed.r2dbc.examples.cache.ktor.domain.CacheObservation
import exposed.r2dbc.examples.cache.ktor.domain.CacheReadResult
import exposed.r2dbc.examples.cache.ktor.domain.CacheStatus
import exposed.r2dbc.examples.cache.ktor.domain.InvalidationResponse
import exposed.r2dbc.examples.cache.ktor.domain.UpsertUserRequest
import exposed.r2dbc.examples.cache.ktor.domain.UserCacheRepository
import exposed.r2dbc.examples.cache.ktor.domain.UserCacheResponse
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
 * Installs cache-observable user routes.
 */
fun Application.userCacheRoutes(
    repository: UserCacheRepository,
    observation: CacheObservation,
) {
    routing {
        get("/users") {
            call.respond(repository.findAllFromDb())
        }

        get("/users/{id}") {
            val id = call.pathUserId()
            val result = repository.findCached(id)
            observation.record(result)
            when (result) {
                is CacheReadResult.Hit -> call.respond(
                    UserCacheResponse(cacheStatus = CacheStatus.HIT, user = result.user)
                )

                is CacheReadResult.Miss -> call.respond(
                    UserCacheResponse(cacheStatus = CacheStatus.MISS, user = result.user)
                )

                CacheReadResult.NotFound -> call.respond(
                    HttpStatusCode.NotFound,
                    UserCacheResponse(cacheStatus = CacheStatus.NOT_FOUND, user = null)
                )
            }
        }

        post("/users") {
            val user = repository.create(call.receive<UpsertUserRequest>())
            call.respond(
                HttpStatusCode.Created,
                UserCacheResponse(cacheStatus = CacheStatus.WRITTEN, user = user)
            )
        }

        put("/users/{id}") {
            val id = call.pathUserId()
            val user = repository.update(id, call.receive<UpsertUserRequest>())
            call.respond(UserCacheResponse(cacheStatus = CacheStatus.WRITTEN, user = user))
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
        ?: throw IllegalArgumentException("id must be a positive integer")
