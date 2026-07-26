package exposed.r2dbc.multitenant.resilientonboarding.config

import exposed.r2dbc.multitenant.resilientonboarding.ResilientTenantOnboardingApp
import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.ResilientTenantProvisioner
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleReconciler
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantLifecycleRepository
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeRegistry
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeResourceFactory
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(TenantLifecycleProperties::class)
class ResilientTenantOnboardingConfig {

    @Bean
    fun lifecycleClock(): Clock = ResilientTenantOnboardingApp.lifecycleClock()

    @Bean
    fun registryDatabase(properties: TenantLifecycleProperties): R2dbcDatabase =
        R2dbcDatabase.connect(
            ConnectionFactories.get(properties.registryUrl),
            R2dbcDatabaseConfig { explicitDialect = H2Dialect() },
        )

    @Bean
    fun tenantLifecycleRepository(
        registryDatabase: R2dbcDatabase,
        properties: TenantLifecycleProperties,
    ): TenantLifecycleRepository =
        TenantLifecycleRepository(registryDatabase, properties.leaseDuration)

    @Bean
    fun tenantRuntimeRegistry(): TenantRuntimeRegistry = TenantRuntimeRegistry()

    @Bean
    fun tenantRuntimeResourceFactory(): TenantRuntimeResourceFactory = H2TenantRuntimeResourceFactory()

    @Bean
    fun resilientTenantProvisioner(
        repository: TenantLifecycleRepository,
        registry: TenantRuntimeRegistry,
        resourceFactory: TenantRuntimeResourceFactory,
        properties: TenantLifecycleProperties,
        clock: Clock,
    ): ResilientTenantProvisioner =
        ResilientTenantProvisioner(repository, registry, resourceFactory, properties, clock)

    @Bean
    fun tenantLifecycleReconciler(
        repository: TenantLifecycleRepository,
        registry: TenantRuntimeRegistry,
        resourceFactory: TenantRuntimeResourceFactory,
    ): TenantLifecycleReconciler = TenantLifecycleReconciler(repository, registry, resourceFactory)

    @Bean
    fun lifecycleInitializer(
        repository: TenantLifecycleRepository,
        reconciler: TenantLifecycleReconciler,
        clock: Clock,
    ): SmartInitializingSingleton =
        SmartInitializingSingleton {
            runBlocking {
                repository.initializeSchema()
                reconciler.reconcile(clock.instant())
            }
        }
}
