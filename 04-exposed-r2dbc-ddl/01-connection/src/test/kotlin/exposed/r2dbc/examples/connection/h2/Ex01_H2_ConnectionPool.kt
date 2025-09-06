package exposed.r2dbc.examples.connection.h2


import exposed.r2dbc.shared.tests.TestDB
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

class Ex01_H2_ConnectionPool {

    companion object: KLoggingChannel()

    private val maximumPoolSize = 10

    private val h2PoolDB1 by lazy {
        R2dbcDatabase.connect("r2dbc:pool:h2:mem:///poolDB1?maxSize=$maximumPoolSize")
    }

    @Test
    fun `suspend transactions exceeding pool size`() = runTest {
        Assumptions.assumeTrue { TestDB.H2 in TestDB.enabledDialects() }

        suspendTransaction(db = h2PoolDB1) {
            SchemaUtils.create(TestTable)
        }

        // DataSource의 maximumPoolSize를 초과하는 트랜잭션을 실행합니다.
        val exceedsPoolSize = (maximumPoolSize * 2 + 1).coerceAtMost(50)
        log.debug { "Exceeds pool size: $exceedsPoolSize" }

        val tasks = List(exceedsPoolSize) { index ->
            launch {
                suspendTransaction(db = h2PoolDB1) {
                    delay(100)
                    val entity = TestTable.insert { it[TestTable.testValue] = "test$index" }
                    log.debug { "Created test entity: $entity" }
                }
            }
            testScheduler.advanceUntilIdle()
        }

        // 실제 생성된 엔티티 수는 exceedsPoolSize 만큼이다. (즉 Connection 이 재활용되었다는 뜻)
        suspendTransaction(db = h2PoolDB1) {
            TestTable.selectAll().count() shouldBeEqualTo exceedsPoolSize.toLong()

            SchemaUtils.drop(TestTable)
        }
    }

    object TestTable: IntIdTable("HIKARI_TESTER") {
        val testValue = varchar("test_value", 32)
    }

//    class TestEntity(id: EntityID<Int>): IntEntity(id) {
//        companion object: IntEntityClass<TestEntity>(TestTable)
//
//        var testValue: String by TestTable.testValue
//
//        override fun equals(other: Any?): Boolean = idEquals(other)
//        override fun hashCode(): Int = idHashCode()
//        override fun toString(): String = toStringBuilder()
//            .add("testValue", testValue)
//            .toString()
//    }
}
