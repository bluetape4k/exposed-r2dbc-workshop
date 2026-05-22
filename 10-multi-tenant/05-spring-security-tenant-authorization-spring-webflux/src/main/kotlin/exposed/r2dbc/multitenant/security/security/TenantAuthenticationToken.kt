package exposed.r2dbc.multitenant.security.security

import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Authentication token for fixed workshop API-key and demo-session tenants.
 */
class TenantAuthenticationToken(
    private val source: String,
    val tenant: Tenant,
): AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_USER"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = "$source:${tenant.id}"
}
