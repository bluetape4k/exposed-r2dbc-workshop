package exposed.r2dbc.examples.cache.utils

import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import exposed.r2dbc.examples.cache.domain.model.UserEventTable
import exposed.r2dbc.examples.cache.domain.model.UserTable
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class DataInitializer: ApplicationListener<ApplicationReadyEvent> {

    companion object: KLoggingChannel()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        runBlocking(Dispatchers.IO) {
            suspendTransaction {
                this.createTables()
            }
        }
    }

    private suspend fun R2dbcTransaction.createTables() {
        log.info { "Creating database tables..." }
        SchemaUtils.create(UserTable, UserCredentialsTable, UserEventTable)
        commit()
    }
}
