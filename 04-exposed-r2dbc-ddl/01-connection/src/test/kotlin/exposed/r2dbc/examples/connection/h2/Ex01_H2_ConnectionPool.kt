package exposed.r2dbc.examples.connection.h2


import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * [Ex01_H2_ConnectionPool] - H2 R2DBC 커넥션 풀 동작 예제
 *
 * `r2dbc:pool:h2:mem:///...?maxSize=N` URL을 사용하여 H2 인메모리 DB에 커넥션 풀을 설정하고,
 * 풀 크기를 초과하는 동시 트랜잭션이 올바르게 처리되는지 검증합니다.
 *
 * ## 학습 내용
 * - R2DBC 커넥션 풀 URL 설정: `r2dbc:pool:h2:mem:///poolDB1?maxSize=10`
 * - 풀 크기(maxSize)를 초과하는 동시 코루틴 트랜잭션의 순차 처리
 * - `suspendTransaction`을 사용한 비동기 트랜잭션 실행
 * - 커넥션이 재활용(pool)되어 모든 요청이 완료됨을 검증
 *
 * ## 지원 DB
 * H2 (인메모리, 커넥션 풀 모드)
 *
 * @see org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
 * @see org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
 */
class Ex01_H2_ConnectionPool {

    companion object: KLoggingChannel()

    private val maximumPoolSize = 10

    private val h2PoolDB1 by lazy {
        R2dbcDatabase.connect("r2dbc:pool:h2:mem:///poolDB1?maxSize=$maximumPoolSize")
    }

    @Test
    fun `pool table starts with empty rows`() = runSuspendIO {
        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }

        suspendTransaction(db = h2PoolDB1) {
            SchemaUtils.create(TestTable)
            TestTable.selectAll().count() shouldBeEqualTo 0L
            SchemaUtils.drop(TestTable)
        }
    }

    @Test
    fun `suspend transactions exceeding pool size`() = runSuspendIO {
        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }

        suspendTransaction(db = h2PoolDB1) {
            SchemaUtils.create(TestTable)
        }

        // DataSource의 maximumPoolSize를 초과하는 트랜잭션을 실행합니다.
        val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)
        log.debug { "Exceeds pool size: $exceedsPoolSize" }

        val jobs = List(exceedsPoolSize) { index ->
            launch {
                suspendTransaction(db = h2PoolDB1) {
                    delay(100)
                    val entityId = TestTable.insertAndGetId { it[TestTable.testValue] = "test$index" }
                    log.debug { "Created test entity. entityId: $entityId" }
                }
            }
        }

        jobs.joinAll()

        // 실제 생성된 엔티티 수는 exceedsPoolSize 만큼이다. (즉 Connection 이 재활용되었다는 뜻)
        suspendTransaction(db = h2PoolDB1) {
            TestTable.selectAll().count() shouldBeEqualTo exceedsPoolSize.toLong()
            SchemaUtils.drop(TestTable)
        }
    }

    /**
     * 커넥션 풀 테스트용 테이블.
     *
     * ```sql
     * CREATE TABLE IF NOT EXISTS HIKARI_TESTER (
     *     id INT AUTO_INCREMENT PRIMARY KEY,
     *     test_value VARCHAR(32) NOT NULL
     * )
     * ```
     */
    object TestTable: IntIdTable("HIKARI_TESTER") {
        val testValue: Column<String> = varchar("test_value", 32)
    }
}
