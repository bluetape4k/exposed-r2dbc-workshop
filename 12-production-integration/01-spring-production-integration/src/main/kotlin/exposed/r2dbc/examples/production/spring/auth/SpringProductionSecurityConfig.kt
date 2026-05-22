package exposed.r2dbc.examples.production.spring.auth

import exposed.r2dbc.examples.production.spring.app.SpringProductionRepository
import kotlinx.coroutines.reactor.mono
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository

/**
 * Spring WebFlux Security configuration backed by Exposed R2DBC account rows.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SpringProductionSecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(repository: SpringProductionRepository): ReactiveUserDetailsService =
        ReactiveUserDetailsService { username ->
            mono {
                val account = repository.findAccount(username)
                    ?: throw UsernameNotFoundException(username)
                User.withUsername(account.username)
                    .password(account.passwordHash)
                    .roles(*account.roles.toTypedArray())
                    .build()
            }
        }

    @Bean
    fun springProductionSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
        http
            .csrf { it.disable() }
            .httpBasic(Customizer.withDefaults())
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .authorizeExchange {
                it.pathMatchers("/production/admin").hasRole("ADMIN")
                    .pathMatchers("/production/profile", "/production/sessions").authenticated()
                    .anyExchange().permitAll()
            }
            .build()
}
