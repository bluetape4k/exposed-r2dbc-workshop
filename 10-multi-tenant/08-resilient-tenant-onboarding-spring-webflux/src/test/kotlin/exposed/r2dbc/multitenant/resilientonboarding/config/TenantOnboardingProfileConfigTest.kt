package exposed.r2dbc.multitenant.resilientonboarding.config

import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.H2TenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlSchemaTenantRuntimeResourceFactory
import exposed.r2dbc.multitenant.resilientonboarding.tenant.PostgreSqlTenantDatabaseProperties
import exposed.r2dbc.multitenant.resilientonboarding.tenant.TenantRuntimeResourceFactory
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TenantOnboardingProfileConfigTest {

    @Test
    fun `h2 profile selects the H2 resource factory`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=h2")
            .withUserConfiguration(
                ProfilePropertiesTestConfig::class.java,
                H2TenantOnboardingConfig::class.java,
                PostgreSqlTenantOnboardingConfig::class.java,
            )
            .run { context ->
                assertIs<H2TenantRuntimeResourceFactory>(
                    context.getBean(TenantRuntimeResourceFactory::class.java),
                )
            }
    }

    @Test
    fun `postgres profile does not register the H2 factory`() {
        ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=postgres")
            .withUserConfiguration(
                ProfilePropertiesTestConfig::class.java,
                H2TenantOnboardingConfig::class.java,
                PostgreSqlTenantOnboardingConfig::class.java,
            )
            .run { context ->
                assertIs<PostgreSqlSchemaTenantRuntimeResourceFactory>(
                    context.getBean(TenantRuntimeResourceFactory::class.java),
                )
            }
    }

    @Test
    fun `postgres properties redact the password`() {
        val rendered = PostgreSqlTenantDatabaseProperties(password = "secret-value").toString()

        assertFalse(rendered.contains("secret-value"))
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    H2TenantDatabaseProperties::class,
    PostgreSqlTenantDatabaseProperties::class,
)
private class ProfilePropertiesTestConfig
