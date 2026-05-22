package exposed.r2dbc.multitenant.onboarding.tenant

/**
 * Raised when tenant request input is syntactically invalid.
 */
class InvalidTenantRequestException(
    message: String,
): RuntimeException(message)

/**
 * Raised when a tenant is already active or currently being provisioned.
 */
class DuplicateTenantException(
    val tenantId: TenantId,
): RuntimeException("Tenant '${tenantId.value}' already exists")

/**
 * Raised when a request references a tenant that is not active.
 */
class UnknownTenantException(
    val tenantId: TenantId,
): RuntimeException("Unknown tenant '${tenantId.value}'")

/**
 * Raised when tenant provisioning cannot complete.
 */
class TenantProvisioningException(
    message: String,
    cause: Throwable? = null,
): RuntimeException(message, cause)

/**
 * Raised when the workshop tenant cap has been reached.
 */
class TenantLimitExceededException(
    val maxTenants: Int,
): RuntimeException("Tenant limit exceeded: $maxTenants")
