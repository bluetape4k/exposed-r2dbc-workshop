package exposed.r2dbc.examples.cache.ktor

import exposed.r2dbc.examples.cache.ktor.config.KtorCacheResources
import exposed.r2dbc.examples.cache.ktor.config.installKtorCachePlugins
import exposed.r2dbc.examples.cache.ktor.domain.CacheObservation
import exposed.r2dbc.examples.cache.ktor.domain.UserCacheRepository
import exposed.r2dbc.examples.cache.ktor.domain.UserDataInitializer
import exposed.r2dbc.examples.cache.ktor.routes.userCacheRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.runBlocking

/**
 * Installs the Ktor R2DBC cache strategy workshop module.
 */
fun Application.ktorCacheStrategiesModule(
    resources: KtorCacheResources = KtorCacheResources.create(),
) {
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }

    installKtorCachePlugins()

    // Startup-only seed initialization; request paths remain suspend-first.
    runBlocking {
        UserDataInitializer(resources.database).initialize()
    }

    val observation = CacheObservation()
    val repository = UserCacheRepository(
        database = resources.database,
        redissonClient = resources.redissonClient,
        cacheName = resources.cacheName,
        observation = observation,
    )
    userCacheRoutes(repository, observation)
}
