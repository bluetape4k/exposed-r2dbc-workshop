package exposed.r2dbc.multitenant.security.config

import exposed.r2dbc.multitenant.security.security.ApiKeyAuthenticationWebFilter
import exposed.r2dbc.multitenant.security.security.AuthorizedTenantContextWebFilter
import exposed.r2dbc.multitenant.security.security.JwtTenantAuthenticationConverter
import exposed.r2dbc.multitenant.security.security.SessionTenantAuthenticationWebFilter
import exposed.r2dbc.multitenant.security.tenant.TenantIdResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Spring Security configuration for tenant-aware WebFlux requests.
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    /**
     * Builds the example security chain.
     */
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        apiKeyAuthenticationWebFilter: ApiKeyAuthenticationWebFilter,
        sessionTenantAuthenticationWebFilter: SessionTenantAuthenticationWebFilter,
        authorizedTenantContextWebFilter: AuthorizedTenantContextWebFilter,
    ): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers("/actuator/health").permitAll()
                    .pathMatchers("/actors/**").authenticated()
                    .anyExchange().permitAll()
            }
            .oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(JwtTenantAuthenticationConverter())
                }
            }
            .addFilterAt(apiKeyAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAt(sessionTenantAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAt(authorizedTenantContextWebFilter, SecurityWebFiltersOrder.LAST)
            .build()

    /**
     * Demo decoder for local examples. Production systems should validate JWTs
     * with an issuer or JWK set.
     */
    @Bean
    fun demoJwtDecoder(): ReactiveJwtDecoder =
        ReactiveJwtDecoder { token ->
            val tenantId = when {
                token.contains("english", ignoreCase = true) -> "english"
                token.contains("korean", ignoreCase = true)  -> "korean"
                token.contains("missing", ignoreCase = true) -> null
                else                                         -> token
            }
            Mono.just(
                Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("demo-user")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .apply {
                        tenantId
                            ?.takeIf { TenantIdResolver.resolveAuthenticatedTenant(it) != null }
                            ?.let { claim("tenant_id", it) }
                        }
                    .build()
            )
        }
}
