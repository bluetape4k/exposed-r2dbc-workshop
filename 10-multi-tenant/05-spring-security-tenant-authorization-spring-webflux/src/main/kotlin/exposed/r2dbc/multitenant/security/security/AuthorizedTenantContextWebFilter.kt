package exposed.r2dbc.multitenant.security.security

import exposed.r2dbc.multitenant.security.tenant.TenantContextKeys
import exposed.r2dbc.multitenant.security.tenant.TenantFilter.Companion.TENANT_HEADER
import exposed.r2dbc.multitenant.security.tenant.TenantIdResolver
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Authorizes the requested tenant and exposes it to R2DBC routing context.
 */
@Component
class AuthorizedTenantContextWebFilter(
    private val tenantAuthenticationResolver: TenantAuthenticationResolver,
): WebFilter {

    companion object: KLoggingChannel()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> =
        Mono.defer {
            val requestedTenantId = TenantIdResolver.resolve(exchange.request.headers.getFirst(TENANT_HEADER))
            ReactiveSecurityContextHolder.getContext()
                .flatMap<Authentication> {
                    Mono.justOrEmpty(it.authentication)
                }
                .switchIfEmpty(Mono.error<Authentication>(AuthenticationCredentialsNotFoundException("Authentication required")))
                .flatMap { authentication ->
                    val authenticatedTenant = tenantAuthenticationResolver.resolve(authentication)
                        ?: return@flatMap Mono.error<Void>(AccessDeniedException("Authenticated tenant is missing or unknown"))

                    if (authenticatedTenant.id != requestedTenantId) {
                        return@flatMap Mono.error<Void>(AccessDeniedException("Authenticated tenant does not match request tenant"))
                    }

                    log.debug { "Authorized tenantId: $requestedTenantId" }
                    chain
                        .filter(exchange)
                        .contextWrite { it.put(TenantContextKeys.TENANT_ID, requestedTenantId) }
                }
        }
}
