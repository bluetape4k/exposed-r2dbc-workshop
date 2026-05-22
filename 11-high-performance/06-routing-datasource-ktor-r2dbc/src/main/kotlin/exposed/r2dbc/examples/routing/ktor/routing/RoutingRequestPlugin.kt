package exposed.r2dbc.examples.routing.ktor.routing

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

private val RoutingRequestAttributeKey = AttributeKey<RoutingRequest>("RoutingRequest")

/**
 * Resolves routing headers once per call and stores the validated value.
 */
val RoutingRequestPlugin = createApplicationPlugin(name = "RoutingRequestPlugin") {
    onCall { call ->
        call.attributes.put(RoutingRequestAttributeKey, call.resolveRoutingRequest())
    }
}

fun ApplicationCall.routingRequest(): RoutingRequest =
    attributes[RoutingRequestAttributeKey]

private fun ApplicationCall.resolveRoutingRequest(): RoutingRequest {
    val tenant = request.headers[TENANT_HEADER]
        ?.trim()
        ?.takeUnless { it.isBlank() }
        ?.let { tenantId ->
            RoutingTenant.from(tenantId)
                ?: throw InvalidRoutingRequestException("Unknown tenant id: $tenantId")
        }
        ?: RoutingTenant.DEFAULT

    val explicitReadOnly = request.headers[READ_ONLY_HEADER]
        ?.trim()
        ?.let { raw ->
            raw.toBooleanStrictOrNull()
                ?: throw InvalidRoutingRequestException("$READ_ONLY_HEADER must be true or false")
        }
        ?: false

    val readOnlyPath = request.path().endsWith("/readonly")
    return RoutingRequest(
        tenant = tenant,
        readOnly = explicitReadOnly || readOnlyPath,
    )
}

const val TENANT_HEADER: String = "X-Tenant-Id"
const val READ_ONLY_HEADER: String = "X-Read-Only"
