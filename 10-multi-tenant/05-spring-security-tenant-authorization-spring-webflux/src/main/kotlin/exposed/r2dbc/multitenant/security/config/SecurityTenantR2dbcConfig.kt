package exposed.r2dbc.multitenant.security.config

import exposed.r2dbc.multitenant.security.tenant.TenantConnectionFactoryRegistry
import exposed.r2dbc.multitenant.security.tenant.TenantRoutingConnectionFactory
import exposed.r2dbc.multitenant.security.tenant.Tenants
import exposed.r2dbc.multitenant.security.tenant.Tenants.Tenant
import io.bluetape4k.r2dbc.pool.connectionFactoryOptionsOf
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Duration

/**
 * Configures tenant-owned R2DBC connection pools and Exposed databases.
 */
@Configuration
@EnableConfigurationProperties(
    TenantConnectionFactoryProperties::class,
    TenantConnectionPoolProperties::class,
)
class SecurityTenantR2dbcConfig {

    /**
     * Provides the dispatcher convention used by the existing R2DBC workshop modules.
     */
    @Bean(destroyMethod = "")
    fun databaseCoroutineDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Creates the registry that owns one bounded [ConnectionPool] per tenant.
     */
    @Bean
    fun tenantConnectionFactoryRegistry(
        properties: TenantConnectionFactoryProperties,
        poolProperties: TenantConnectionPoolProperties,
    ): TenantConnectionFactoryRegistry {
        val pools = Tenants.Tenant.entries.associateWith { tenant ->
            val definition = properties.definitionFor(tenant)
            poolConfiguration(definition.url, poolProperties)
                .also { it.warmup().block(poolProperties.maxCreateConnectionTime) }
        }
        return TenantConnectionFactoryRegistry(pools)
    }

    /**
     * Exposes the routing connection factory as the primary Spring R2DBC factory.
     */
    @Bean
    @Primary
    fun tenantRoutingConnectionFactory(
        properties: TenantConnectionFactoryProperties,
        registry: TenantConnectionFactoryRegistry,
    ): ConnectionFactory {
        val defaultTenant = properties.defaultTenant()
        val routingConnectionFactory = TenantRoutingConnectionFactory()
        routingConnectionFactory.setTargetConnectionFactories(registry.targetConnectionFactories())
        routingConnectionFactory.setDefaultTargetConnectionFactory(registry.get(defaultTenant))
        routingConnectionFactory.setLenientFallback(false)
        routingConnectionFactory.afterPropertiesSet()
        return routingConnectionFactory
    }

    /**
     * Exposed database used by request-path transactions.
     */
    @Bean("tenantRoutingDatabase")
    fun tenantRoutingDatabase(
        tenantRoutingConnectionFactory: ConnectionFactory,
        properties: TenantConnectionFactoryProperties,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): R2dbcDatabase {
        val defaultUrl = properties.definitionFor(properties.defaultTenant()).url
        return R2dbcDatabase.connect(
            tenantRoutingConnectionFactory,
            r2dbcConfig(defaultUrl, databaseCoroutineDispatcher),
        )
    }

    /**
     * Exposed databases used only for startup initialization of each tenant DB.
     */
    @Bean("tenantInitializerDatabases")
    fun tenantInitializerDatabases(
        registry: TenantConnectionFactoryRegistry,
        properties: TenantConnectionFactoryProperties,
        databaseCoroutineDispatcher: CoroutineDispatcher,
    ): Map<Tenant, R2dbcDatabase> =
        Tenants.Tenant.entries.associateWith { tenant ->
            R2dbcDatabase.connect(
                registry.get(tenant),
                r2dbcConfig(properties.definitionFor(tenant).url, databaseCoroutineDispatcher),
            )
        }

    private fun poolConfiguration(
        url: String,
        properties: TenantConnectionPoolProperties,
    ): ConnectionPool = connectionPoolOf(connectionFactoryOptionsOf(url)) {
        maxIdleTime = properties.maxIdleTime
        maxLifeTime = properties.maxLifeTime
        maxCreateConnectionTime = properties.maxCreateConnectionTime
        maxAcquireTime = properties.maxAcquireTime
        maxSize = properties.maxSize
        initialSize = properties.initialSize
        minIdle = properties.minIdle
        acquireRetry = properties.acquireRetry
        backgroundEvictionInterval = properties.backgroundEvictionInterval
    }

    private fun r2dbcConfig(
        url: String,
        dispatcher: CoroutineDispatcher,
    ): R2dbcDatabaseConfig.Builder =
        R2dbcDatabaseConfig {
            this.dispatcher = dispatcher
            this.connectionFactoryOptions = connectionFactoryOptionsOf(url)
        }
}

/**
 * Tenant R2DBC connection definitions.
 */
@ConfigurationProperties(prefix = "app.tenants")
class TenantConnectionFactoryProperties {

    /** Default tenant used outside the mandatory HTTP tenant-header path. */
    var defaultTenant: String = Tenants.DEFAULT_TENANT.id

    /** Tenant ID to R2DBC connection definition. */
    var definitions: MutableMap<String, TenantConnectionDefinition> = linkedMapOf()

    fun defaultTenant(): Tenant =
        Tenants.getById(defaultTenant)

    fun definitionFor(tenant: Tenant): TenantConnectionDefinition =
        definitions[tenant.id] ?: error("Missing R2DBC definition for tenant '${tenant.id}'")
}

/**
 * R2DBC URL for one tenant.
 */
class TenantConnectionDefinition {

    /** R2DBC URL used by this tenant. */
    lateinit var url: String
}

/**
 * Shared pool settings applied to every tenant-owned pool.
 */
@ConfigurationProperties(prefix = "app.r2dbc.pool")
class TenantConnectionPoolProperties {

    /** Maximum pooled connections per tenant. */
    var maxSize: Int = 8

    /** Eager connections per tenant. */
    var initialSize: Int = 1

    /** Minimum idle connections per tenant. */
    var minIdle: Int = 1

    /** Maximum connection idle time before eviction. */
    var maxIdleTime: Duration = Duration.ofMinutes(10)

    /** Maximum connection lifetime. */
    var maxLifeTime: Duration = Duration.ofMinutes(30)

    /** Maximum time allowed for creating a connection. */
    var maxCreateConnectionTime: Duration = Duration.ofSeconds(10)

    /** Maximum time allowed for acquiring a pooled connection. */
    var maxAcquireTime: Duration = Duration.ofSeconds(3)

    /** Retries when acquiring a pooled connection. */
    var acquireRetry: Int = 3

    /** Background eviction interval. */
    var backgroundEvictionInterval: Duration = Duration.ofMinutes(1)
}
