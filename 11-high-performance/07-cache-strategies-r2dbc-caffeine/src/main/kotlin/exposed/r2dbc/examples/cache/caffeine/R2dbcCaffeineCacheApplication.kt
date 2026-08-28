package exposed.r2dbc.examples.cache.caffeine

import exposed.r2dbc.examples.cache.caffeine.config.R2dbcCaffeineResources
import exposed.r2dbc.examples.cache.caffeine.config.installR2dbcCaffeinePlugins
import exposed.r2dbc.examples.cache.caffeine.routes.productCacheRoutes
import exposed.r2dbc.examples.cache.caffeine.service.ProductCacheService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking

/** 기본 실행 진입점입니다. 애플리케이션 pool은 127.0.0.1:8080에서만 엽니다. */
fun main() {
    embeddedServer(
        Netty,
        host = "127.0.0.1",
        port = 8080,
        module = Application::r2dbcCaffeineCacheModule,
    ).start(wait = true)
}

/** schema 초기화가 끝난 뒤 route를 설치하고 ApplicationStopped에서 자원을 닫습니다. */
fun Application.r2dbcCaffeineCacheModule(
    resources: R2dbcCaffeineResources = R2dbcCaffeineResources.create(),
) {
    installR2dbcCaffeinePlugins()
    try {
        runBlocking { resources.awaitInitialized() }
    } catch (cause: Throwable) {
        resources.close()
        throw cause
    }

    productCacheRoutes(ProductCacheService(resources.repository))
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }
}
