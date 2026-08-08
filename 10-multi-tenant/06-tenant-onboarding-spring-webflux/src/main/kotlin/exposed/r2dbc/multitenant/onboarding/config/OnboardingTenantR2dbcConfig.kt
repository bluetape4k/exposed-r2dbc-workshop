package exposed.r2dbc.multitenant.onboarding.config

import exposed.r2dbc.multitenant.onboarding.tenant.ProvisioningFailureSimulator
import exposed.r2dbc.multitenant.onboarding.tenant.TenantConnectionFactoryRegistry
import exposed.r2dbc.multitenant.onboarding.tenant.TenantOnboardingProperties
import exposed.r2dbc.multitenant.onboarding.tenant.TenantRegistryRepository
import exposed.r2dbc.multitenant.onboarding.tenant.TenantRoutingConnectionFactory
import io.bluetape4k.r2dbc.pool.connectionFactoryOf
import io.bluetape4k.r2dbc.pool.connectionFactoryOptionsOf
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Configures registry and routing R2DBC resources for the onboarding example.
 */
@Configuration
@EnableConfigurationProperties(TenantOnboardingProperties::class)
class OnboardingTenantR2dbcConfig {

    @Bean(destroyMethod = "")
    fun databaseCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Bean("registryConnectionFactory")
    fun registryConnectionFactory(properties: TenantOnboardingProperties): ConnectionFactory =
        connectionFactoryOf(properties.registryUrl)

    @Bean("registryDatabase")
    fun registryDatabase(
        @Qualifier("registryConnectionFactory")
        registryConnectionFactory: ConnectionFactory,
        properties: TenantOnboardingProperties,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): R2dbcDatabase =
        R2dbcDatabase.connect(
            registryConnectionFactory,
            r2dbcConfig(properties.registryUrl, databaseCoroutineDispatcher),
        )

    @Bean
    fun dynamicTenantRoutingConnectionFactory(
        registry: TenantConnectionFactoryRegistry,
    ): TenantRoutingConnectionFactory =
        TenantRoutingConnectionFactory(registry)

    @Bean
    @Primary
    fun tenantRoutingConnectionFactory(
        routingConnectionFactory: TenantRoutingConnectionFactory,
    ): ConnectionFactory =
        routingConnectionFactory

    @Bean("tenantRoutingDatabase")
    fun tenantRoutingDatabase(
        tenantRoutingConnectionFactory: ConnectionFactory,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): R2dbcDatabase =
        R2dbcDatabase.connect(
            tenantRoutingConnectionFactory,
            R2dbcDatabaseConfig {
                dispatcher = databaseCoroutineDispatcher
                explicitDialect = H2Dialect()
            },
        )

    @Bean
    fun registryInitializer(
        registryRepository: TenantRegistryRepository,
    ): SmartInitializingSingleton =
        SmartInitializingSingleton {
            runBlocking {
                registryRepository.initializeSchema()
                registryRepository.recoverStaleRows()
            }
        }

    @Bean
    fun provisioningFailureSimulator(): ProvisioningFailureSimulator =
        ProvisioningFailureSimulator { }

    private fun r2dbcConfig(
        url: String,
        dispatcher: CoroutineDispatcher,
    ): R2dbcDatabaseConfig.Builder =
        R2dbcDatabaseConfig {
            this.dispatcher = dispatcher
            this.connectionFactoryOptions = connectionFactoryOptionsOf(url)
            this.explicitDialect = H2Dialect()
        }
}
