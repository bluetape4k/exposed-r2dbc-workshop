package exposed.r2dbc.examples.cache.utils

import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import exposed.r2dbc.examples.cache.domain.model.UserEventTable
import exposed.r2dbc.examples.cache.domain.model.UserTable
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 시작 시 데이터베이스 테이블을 자동으로 생성하는 초기화 컴포넌트입니다.
 *
 * [ApplicationReadyEvent]를 수신하여 [UserTable], [UserCredentialsTable], [UserEventTable]을
 * 생성합니다. DB I/O 바운드 작업이므로 [Dispatchers.IO] 컨텍스트를 사용합니다.
 */
@Component
class DataInitializer(
    private val r2dbcDatabase: R2dbcDatabase,
): ApplicationListener<ApplicationReadyEvent> {

    companion object: KLoggingChannel()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        runBlocking(Dispatchers.IO) {
            suspendTransaction(db = r2dbcDatabase) {
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
