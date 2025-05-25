package exposed.r2dbc.multitenant.webflux.config

import exposed.r2dbc.multitenant.webflux.tenant.DataInitializer
import exposed.r2dbc.multitenant.webflux.tenant.TenantInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TenantConfig {

    @Bean
    fun tenantInitializer(dataInitializer: DataInitializer): TenantInitializer =
        TenantInitializer(dataInitializer)
}
