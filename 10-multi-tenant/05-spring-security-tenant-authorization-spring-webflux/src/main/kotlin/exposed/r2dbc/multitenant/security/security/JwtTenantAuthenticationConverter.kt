package exposed.r2dbc.multitenant.security.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono

/**
 * Converts a JWT to authentication without rejecting tenant-claim problems.
 */
class JwtTenantAuthenticationConverter: Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    override fun convert(source: Jwt): Mono<AbstractAuthenticationToken> =
        Mono.just(
            JwtAuthenticationToken(
                source,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
                source.subject ?: "jwt-user",
            )
        )
}
