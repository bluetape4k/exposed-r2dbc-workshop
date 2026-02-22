package exposed.r2dbc.examples.transactions

import exposed.r2dbc.shared.dml.DMLTestData
import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class Ex05_NestedTransactions: AbstractR2dbcExposedTest() {

    companion object: KLoggingChannel()

    private val db by lazy {
        R2dbcDatabase.connect(
            url = "r2dbc:h2:mem:///db1;DB_CLOSE_DELAY=-1;",
            databaseConfig = R2dbcDatabaseConfig {
                useNestedTransactions = true
                defaultMaxAttempts = 1
            }
        )
    }

    val cities = DMLTestData.Cities

    private suspend fun cityCounts(): Int = cities.selectAll().count().toInt()

    private suspend fun cityNames(): List<String> = cities.selectAll().map { it[cities.name] }.toList()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `중첩 트랜잭션 실행`(testDB: TestDB) = runTest {
        // 외부 트랜잭션
        withTables(testDB, cities, configure = { useNestedTransactions = true }) {
            cities.selectAll().toList().shouldBeEmpty()
            cities.insert { it[name] = "city1" }
            cityCounts() shouldBeEqualTo 1
            cityNames() shouldBeEqualTo listOf("city1")

            // 중첩 1
            suspendTransaction {
                cities.insert {
                    it[name] = "city2"
                }
                cityNames() shouldBeEqualTo listOf("city1", "city2")

                // 중첩 2
                suspendTransaction {
                    cities.insert { it[name] = "city3" }
                    cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")
                }
                // 중첩 2가 성공했으므로, 중접 1의 결과는 모두 반영되어야 한다.
                cityNames() shouldBeEqualTo listOf("city1", "city2", "city3")

                // 중첩1을 강제 롤백한다
                rollback()
            }

            // 중첩1과 내부 트랜잭션의 작업은 모두 취소되고, 현재 트랜잭션 결과만 반영된다.
            cityNames() shouldBeEqualTo listOf("city1")
        }
    }

    private suspend fun assertSingleRecordInNewTransactionAndReset() =
        suspendTransaction(db = db) {
            val result = cities.selectAll().single()[cities.name]
            result shouldBeEqualTo "City A"
            cities.deleteAll()
        }
}
