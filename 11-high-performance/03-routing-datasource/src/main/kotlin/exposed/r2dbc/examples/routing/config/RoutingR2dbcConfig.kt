package exposed.r2dbc.examples.routing.config

import exposed.r2dbc.examples.routing.datasource.ConnectionFactoryRegistry
import exposed.r2dbc.examples.routing.datasource.ContextAwareRoutingKeyResolver
import exposed.r2dbc.examples.routing.datasource.DynamicRoutingConnectionFactory
import exposed.r2dbc.examples.routing.datasource.InMemoryConnectionFactoryRegistry
import exposed.r2dbc.examples.routing.datasource.RoutingKeyResolver
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * `routing.r2dbc.*` 설정으로 라우팅 ConnectionFactory 및 Exposed R2DBC를 구성합니다.
 */
@Configuration
@EnableConfigurationProperties(RoutingR2dbcProperties::class)
class RoutingR2dbcConfig {

    /**
     * 라우팅 대상 ConnectionFactory 레지스트리를 생성합니다.
     */
    @Bean
    fun connectionFactoryRegistry(properties: RoutingR2dbcProperties): ConnectionFactoryRegistry {
        val registry = InMemoryConnectionFactoryRegistry()

        properties.tenants.forEach { (tenantId, tenant) ->
            val rwFactory = ConnectionFactories.get(tenant.rw)
            val roFactory = ConnectionFactories.get(tenant.ro ?: tenant.rw)

            registry.register("$tenantId:rw", rwFactory)
            registry.register("$tenantId:ro", roFactory)
        }
        return registry
    }

    /**
     * 컨텍스트 기반 라우팅 키 해석기를 생성합니다.
     */
    @Bean
    fun routingKeyResolver(properties: RoutingR2dbcProperties): RoutingKeyResolver =
        ContextAwareRoutingKeyResolver(defaultTenant = properties.defaultTenant)

    /**
     * 애플리케이션 기본 [ConnectionFactory]를 동적 라우팅 구현으로 제공합니다.
     */
    @Bean
    @Primary
    fun routingConnectionFactory(
        properties: RoutingR2dbcProperties,
        registry: ConnectionFactoryRegistry,
        resolver: RoutingKeyResolver,
    ): ConnectionFactory = DynamicRoutingConnectionFactory(
        registry = registry,
        keyResolver = resolver,
        defaultKey = "${properties.defaultTenant}:rw",
    )

    /**
     * 라우팅된 ConnectionFactory를 사용하는 Exposed R2DBC 데이터베이스를 생성합니다.
     */
    @Bean
    fun routingR2dbcDatabase(
        connectionFactory: ConnectionFactory,
        properties: RoutingR2dbcProperties,
    ): R2dbcDatabase {
        val defaultTenant = properties.tenants[properties.defaultTenant]
            ?: error("No default tenant config for '${properties.defaultTenant}'")
        val defaultOptions = ConnectionFactoryOptions.parse(defaultTenant.rw)

        val config = R2dbcDatabaseConfig {
            dispatcher = Dispatchers.IO
            connectionFactoryOptions = defaultOptions
        }
        return R2dbcDatabase.connect(connectionFactory, config)
    }

    /**
     * 초기 데이터 적재를 위해 각 라우팅 키별 Exposed 데이터베이스 인스턴스를 제공합니다.
     */
    @Bean
    fun routingNodeDatabases(
        properties: RoutingR2dbcProperties,
        registry: ConnectionFactoryRegistry,
    ): Map<String, R2dbcDatabase> {
        val urlByKey = buildMap {
            properties.tenants.forEach { (tenantId, tenant) ->
                put("$tenantId:rw", tenant.rw)
                put("$tenantId:ro", tenant.ro ?: tenant.rw)
            }
        }

        return urlByKey.entries.associate { (key, url) ->
            val connectionFactory = registry.get(key) ?: error("No ConnectionFactory for key=$key")
            val options = ConnectionFactoryOptions.parse(url)
            val config = R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions = options
            }
            key to R2dbcDatabase.connect(connectionFactory, config)
        }
    }
}

/**
 * 라우팅 R2DBC 구성 루트 프로퍼티입니다.
 */
@ConfigurationProperties(prefix = "routing.r2dbc")
class RoutingR2dbcProperties {

    /**
     * tenant 정보가 없을 때 사용할 기본 tenant입니다.
     */
    var defaultTenant: String = "default"

    /**
     * tenant별 read-write/read-only 연결 URL입니다.
     */
    var tenants: MutableMap<String, TenantConnectionProperties> = linkedMapOf()
}

/**
 * 단일 tenant의 라우팅 연결 정보입니다.
 */
class TenantConnectionProperties {

    /**
     * read-write 라우팅 URL입니다.
     */
    lateinit var rw: String

    /**
     * read-only 라우팅 URL입니다. 미지정 시 [rw]를 재사용합니다.
     */
    var ro: String? = null
}
