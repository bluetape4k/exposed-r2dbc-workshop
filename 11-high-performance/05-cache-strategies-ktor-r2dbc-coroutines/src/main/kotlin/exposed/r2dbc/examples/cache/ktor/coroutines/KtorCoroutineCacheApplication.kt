package exposed.r2dbc.examples.cache.ktor.coroutines

import exposed.r2dbc.examples.cache.ktor.coroutines.config.KtorCoroutineCacheResources
import exposed.r2dbc.examples.cache.ktor.coroutines.config.installKtorCoroutineCachePlugins
import exposed.r2dbc.examples.cache.ktor.coroutines.domain.CoroutineUserCacheRepository
import exposed.r2dbc.examples.cache.ktor.coroutines.routes.coroutineUserCacheRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

/**
 * Installs the Ktor R2DBC coroutine cache workshop module.
 */
fun Application.ktorCoroutineCacheModule(
    resources: KtorCoroutineCacheResources = KtorCoroutineCacheResources.create(),
) {
    monitor.subscribe(ApplicationStopped) {
        resources.close()
    }

    installKtorCoroutineCachePlugins()

    val repository = CoroutineUserCacheRepository(
        database = resources.database,
        redissonClient = resources.redissonClient,
        cacheName = resources.cacheName,
        observation = resources.observation,
        loader = resources.loader,
        awaitReady = resources::awaitInitialized,
    )
    coroutineUserCacheRoutes(repository, resources.observation)
}
