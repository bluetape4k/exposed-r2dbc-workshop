package exposed.r2dbc.examples.cache.domain.repository

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import exposed.r2dbc.examples.cache.domain.model.UserEventTable
import exposed.r2dbc.examples.cache.domain.model.newUserEventRecord
import io.bluetape4k.junit5.awaitility.untilSuspending
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withPollInterval
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

class UserEventCacheRepositoryTest(
    @param:Autowired private val repository: UserEventCacheRepository,
): AbstractCacheStrategyTest() {

    companion object: KLoggingChannel()

    @BeforeEach
    fun setup() {
        runBlocking {
            repository.invalidateAll()
            suspendTransaction {
                UserEventTable.deleteAll()
            }
        }
    }

    @Test
    fun `write behind 로 대량의 데이테를 추가한다`() = runSuspendIO {
        val totalCount = 1000

        flow {
            repeat(totalCount) {
                emit(newUserEventRecord())
            }
        }
            .chunked(100)
            .collect { chunk ->
                log.debug { "put all ${chunk.size} items" }
                repository.putAll(chunk)
            }

        // Write-Behind 이므로, DB에 반영되기까지 시간이 걸린다.
        await
            .atMost(Duration.ofSeconds(10))
            .withPollInterval(Duration.ofMillis(500))
            .untilSuspending {
                val countInDB = suspendTransaction { UserEventTable.selectAll().count() }
                log.debug { "countInDB: $countInDB" }
                countInDB == totalCount.toLong()
            }

        suspendTransaction { UserEventTable.selectAll().count() } shouldBeEqualTo totalCount.toLong()
    }
}
