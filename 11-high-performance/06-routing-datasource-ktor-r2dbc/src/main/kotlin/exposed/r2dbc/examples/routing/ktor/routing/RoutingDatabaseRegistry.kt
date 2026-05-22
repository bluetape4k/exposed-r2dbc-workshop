package exposed.r2dbc.examples.routing.ktor.routing

import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

/**
 * Explicit routing key to R2DBC database registry for the Ktor example.
 */
class RoutingDatabaseRegistry(
    private val databases: Map<String, RoutingDatabase>,
) {
    fun database(route: RoutingRequest): R2dbcDatabase =
        databases[route.key]?.database
            ?: error("No routing database for key=${route.key}. keys=${databases.keys.sorted()}")
}
