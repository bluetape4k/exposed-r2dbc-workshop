package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.dao.idEquals
import io.bluetape4k.exposed.dao.idHashCode
import io.bluetape4k.exposed.dao.toStringBuilder
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

@TestMethodOrder(MethodOrderer.MethodName::class)
class TimebasedUUIDBase62TableTest: AbstractCustomIdTableTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t_timebased_uuid_base62 (
     *      id VARCHAR(22) PRIMARY KEY,
     *      "name" VARCHAR(255) NOT NULL,
     *      age INT NOT NULL
     * )
     * ```
     */
    object T1: TimebasedUUIDBase62Table("t_timebased_uuid_base62") {
        val name = varchar("name", 255)
        val age = integer("age")
    }

    class E1(id: TimebasedUUIDBase62EntityID): TimebasedUUIDBase62Entity(id) {
        companion object: TimebasedUUIDBase62EntityClass<E1>(T1)

        var name by T1.name
        var age by T1.age

        override fun equals(other: Any?): Boolean = idEquals(other)
        override fun hashCode(): Int = idHashCode()
        override fun toString(): String = toStringBuilder()
            .add("name", name)
            .add("age", age)
            .toString()
    }

    data class Record(val name: String, val age: Int)

    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `TimebasedUUID Base62 id를 가진 레코드를 낱개로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
        withTables(testDB, T1) {
            List(recordCount) {
                T1.insert {
                    it[T1.name] = faker.name().fullName()
                    it[T1.age] = Random.nextInt(10, 80)
                }
            }

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }

    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `TimebasedUUID Base62 id를 가진 레코드를 배치로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
        withTables(testDB, T1) {
            val records = List(recordCount) {
                Record(
                    name = faker.name().fullName(),
                    age = Random.nextInt(10, 80)
                )
            }
            records.chunked(30).forEach { chunk ->
                T1.batchInsert(chunk, shouldReturnGeneratedValues = false) {
                    this[T1.name] = it.name
                    this[T1.age] = it.age
                }
            }

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }

    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `코루틴 환경에서 레코드를 배치로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
        withTables(testDB, T1) {
            val records = List(recordCount) {
                Record(
                    name = faker.name().fullName(),
                    age = Random.nextInt(10, 80)
                )
            }

            records.chunked(100).map { chunk ->
                launch {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = testDB.db.transactionManager.defaultIsolationLevel!!,
                        db = testDB.db
                    ) {
                        T1.batchInsert(chunk, shouldReturnGeneratedValues = false) {
                            this[T1.name] = it.name
                            this[T1.age] = it.age
                        }
                    }
                }
            }.joinAll()

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }
}
