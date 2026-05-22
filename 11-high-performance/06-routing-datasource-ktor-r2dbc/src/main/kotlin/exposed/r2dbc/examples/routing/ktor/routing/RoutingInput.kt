package exposed.r2dbc.examples.routing.ktor.routing

/**
 * Supported workshop tenants.
 */
enum class RoutingTenant(val id: String) {
    DEFAULT("default"),
    ACME("acme"),
    ;

    companion object {
        fun from(id: String): RoutingTenant? =
            entries.firstOrNull { it.id == id }
    }
}

/**
 * Read/write routing mode.
 */
enum class RoutingMode(val suffix: String) {
    READ_WRITE("rw"),
    READ_ONLY("ro"),
}

/**
 * Validated per-call routing state.
 */
data class RoutingRequest(
    val tenant: RoutingTenant,
    val readOnly: Boolean,
) {
    val mode: RoutingMode = if (readOnly) RoutingMode.READ_ONLY else RoutingMode.READ_WRITE
    val key: String = "${tenant.id}:${mode.suffix}"
    val expectedMarker: String = "${tenant.id}-${mode.suffix}"

    fun asReadWrite(): RoutingRequest = copy(readOnly = false)
}

/**
 * Raised when route or header inputs cannot be converted to a valid routing request.
 */
class InvalidRoutingRequestException(message: String): IllegalArgumentException(message)
