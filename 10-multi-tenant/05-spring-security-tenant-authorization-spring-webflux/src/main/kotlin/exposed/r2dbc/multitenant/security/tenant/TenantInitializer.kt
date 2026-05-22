package exposed.r2dbc.multitenant.security.tenant

import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

/**
 * Initializes all fixed tenant databases during application startup.
 */
@Component
class TenantInitializer(
    private val dataInitializer: DataInitializer,
): ApplicationListener<ApplicationReadyEvent> {

    companion object: KLoggingChannel()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        Tenants.Tenant.entries.forEach { tenant ->
            runBlocking(Dispatchers.IO) {
                dataInitializer.initialize(tenant)
            }
        }
    }
}
