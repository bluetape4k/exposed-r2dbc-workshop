package exposed.r2dbc.multitenant.resilientonboarding.config

import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeResourceFactory
import io.bluetape4k.r2dbc.pool.connectionFactoryOf
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("h2")
class H2TenantOnboardingConfig {

    @Bean
    fun registryDatabase(properties: H2TenantDatabaseProperties): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactoryOf(properties.registryUrl),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )

    @Bean
    fun tenantRuntimeResourceFactory(): TenantRuntimeResourceFactory =
        H2TenantRuntimeResourceFactory()
}
