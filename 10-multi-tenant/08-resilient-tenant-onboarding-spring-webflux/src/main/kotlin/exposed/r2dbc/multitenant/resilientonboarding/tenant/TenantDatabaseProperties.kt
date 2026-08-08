package exposed.r2dbc.multitenant.resilientonboarding.tenant

import exposed.r2dbc.shared.config.R2dbcServerCredentials
import exposed.r2dbc.shared.config.postgresConnectionFactoryOptions
import io.r2dbc.spi.ConnectionFactoryOptions
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.resilient-onboarding.h2")
data class H2TenantDatabaseProperties(
    val registryUrl: String =
        "r2dbc:h2:mem:///resilient_tenant_registry;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
)

@ConfigurationProperties("app.resilient-onboarding.postgres")
data class PostgreSqlTenantDatabaseProperties(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "postgres",
    val username: String = "postgres",
    val password: String = "postgres",
) {
    fun connectionFactoryOptions(): ConnectionFactoryOptions =
        postgresConnectionFactoryOptions(
            credentials = R2dbcServerCredentials(host, port, username, password),
            database = database,
        )

    override fun toString(): String =
        "PostgreSqlTenantDatabaseProperties(host=$host, port=$port, database=$database, " +
            "username=$username, password=****)"
}
