package exposed.r2dbc.examples.routing.ktor.config

import exposed.r2dbc.examples.routing.ktor.domain.RoutingMarkerRepository
import exposed.r2dbc.examples.routing.ktor.routing.RoutingDatabase
import exposed.r2dbc.examples.routing.ktor.routing.RoutingDatabaseRegistry
import exposed.r2dbc.examples.routing.ktor.routing.RoutingMode
import exposed.r2dbc.examples.routing.ktor.routing.RoutingRequest
import exposed.r2dbc.examples.routing.ktor.routing.RoutingTenant
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

/**
 * Owns the routing datasource pools for one Ktor application instance.
 */
class KtorRoutingDatasourceResources private constructor(
    private val databases: List<RoutingDatabase>,
    val registry: RoutingDatabaseRegistry,
): AutoCloseable {

    private val initializerJob = SupervisorJob()
    private val initializerScope = CoroutineScope(
        initializerJob + Dispatchers.IO + CoroutineName("ktor-routing-datasource-initializer")
    )
    private val initialized = initializerScope.async {
        val repository = RoutingMarkerRepository(registry = registry, awaitReady = {})
        RoutingTenant.entries.forEach { tenant ->
            RoutingMode.entries.forEach { mode ->
                val route = RoutingRequest(tenant = tenant, readOnly = mode == RoutingMode.READ_ONLY)
                repository.resetMarker(route, route.expectedMarker)
            }
        }
    }

    suspend fun awaitInitialized() {
        initialized.await()
    }

    override fun close() {
        // AutoCloseable has no suspend close hook; this joins only the app-owned initializer.
        runBlocking {
            initializerJob.cancelAndJoin()
        }
        databases.forEach { it.close() }
    }

    companion object {
        /**
         * Creates four H2-backed routing targets: default/acme x rw/ro.
         */
        fun create(
            databasePrefix: String = "ktor_routing_datasource",
            maxPoolSize: Int = 4,
        ): KtorRoutingDatasourceResources {
            val databases = RoutingTenant.entries.flatMap { tenant ->
                RoutingMode.entries.map { mode ->
                    val route = RoutingRequest(tenant = tenant, readOnly = mode == RoutingMode.READ_ONLY)
                    RoutingDatabase.create(
                        routingKey = route.key,
                        databaseName = "${databasePrefix}_${tenant.id}_${mode.suffix}",
                        maxPoolSize = maxPoolSize,
                    )
                }
            }
            return KtorRoutingDatasourceResources(
                databases = databases,
                registry = RoutingDatabaseRegistry(databases.associateBy { it.routingKey }),
            )
        }
    }
}
