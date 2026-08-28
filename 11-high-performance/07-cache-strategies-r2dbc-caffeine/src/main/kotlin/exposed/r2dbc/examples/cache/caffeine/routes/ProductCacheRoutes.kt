package exposed.r2dbc.examples.cache.caffeine.routes

import exposed.r2dbc.examples.cache.caffeine.domain.UpdateProductRequest
import exposed.r2dbc.examples.cache.caffeine.service.ProductCacheService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/** 상품 cache adapter의 읽기·쓰기·무효화·health route를 설치합니다. */
fun Application.productCacheRoutes(service: ProductCacheService) {
    routing {
        get("/") {
            call.respondText("Exposed R2DBC Caffeine cache example")
        }
        get("/products") {
            call.respond(service.findAllFromDb())
        }
        get("/products/{sku}") {
            call.respond(service.get(call.parameters["sku"] ?: ""))
        }
        put("/products/{sku}") {
            val request = call.receive<UpdateProductRequest>()
            call.respond(service.update(call.parameters["sku"] ?: "", request))
        }
        delete("/products/{sku}/cache") {
            service.invalidate(call.parameters["sku"] ?: "")
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/products/cache") {
            service.clear()
            call.respond(HttpStatusCode.NoContent)
        }
        get("/cache/health") {
            call.respond(service.health())
        }
    }
}
