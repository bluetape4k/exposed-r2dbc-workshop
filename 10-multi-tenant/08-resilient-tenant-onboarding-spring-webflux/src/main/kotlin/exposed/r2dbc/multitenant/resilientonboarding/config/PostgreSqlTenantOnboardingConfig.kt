package exposed.r2dbc.multitenant.resilientonboarding.config

import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlSchemaTenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlTenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeResourceFactory
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
class PostgreSqlTenantOnboardingConfig {

    @Bean
    fun postgresConnectionFactory(
        properties: PostgreSqlTenantDatabaseProperties,
    ): ConnectionFactory =
        ConnectionFactories.get(properties.connectionFactoryOptions())

    @Bean
    fun registryDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactory,
            R2dbcDatabaseConfig { explicitDialect = PostgreSQLDialect() },
        )

    @Bean
    fun tenantRuntimeResourceFactory(
        connectionFactory: ConnectionFactory,
        registryDatabase: R2dbcDatabase,
    ): TenantRuntimeResourceFactory =
        PostgreSqlSchemaTenantRuntimeResourceFactory(connectionFactory, registryDatabase)
}
