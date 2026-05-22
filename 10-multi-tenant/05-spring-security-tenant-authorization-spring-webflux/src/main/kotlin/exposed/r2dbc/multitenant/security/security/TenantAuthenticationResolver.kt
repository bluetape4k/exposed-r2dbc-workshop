package exposed.r2dbc.multitenant.security.security

import exposed.r2dbc.multitenant.security.tenant.TenantIdResolver
import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Extracts the tenant identity established by the current authentication.
 */
@Component
class TenantAuthenticationResolver {

    /**
     * Returns the authenticated tenant, or `null` when the principal is
     * authenticated but has no usable tenant identity.
     */
    fun resolve(authentication: Authentication): Tenant? {
        val token = authentication as? TenantAuthenticationToken
        if (token != null) {
            return token.tenant
        }

        val jwt = when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else                      -> authentication.principal as? Jwt
        }
        return TenantIdResolver.resolveAuthenticatedTenant(jwt?.getClaimAsString("tenant_id"))
    }
}
