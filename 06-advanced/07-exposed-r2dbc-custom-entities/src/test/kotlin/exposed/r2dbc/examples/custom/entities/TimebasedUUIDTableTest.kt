package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.dao.id.TimebasedUUIDTable
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

/**
 * 시간 기반 UUID(UUIDv1)를 기본 키로 사용하는 테이블 예제.
 *
 * `bluetape4k-exposed` 의 [TimebasedUUIDTable]을 상속받아 `UUID` PRIMARY KEY를 자동으로
 * 시간 기반 UUID(버전 1) 값으로 채웁니다. UUIDv1은 현재 시간과 노드 정보를 조합하여
 * 생성되므로 시간 순 정렬이 가능하며, RFC 4122 UUID 표준을 준수합니다.
 *
 * TimebasedUUID(UUIDv1) 특성:
 * - 표준 UUID 형식 (예: `550e8400-e29b-41d4-a716-446655440000`)
 * - 시간 정렬 가능 (100나노초 단위, 매우 세밀)
 * - RFC 4122 표준 준수 — UUID를 기본 키로 요구하는 시스템과 호환
 * - 노드 정보(MAC 주소 또는 랜덤) 포함으로 전역 고유성 보장
 *
 * @see TimebasedUUIDBase62TableTest 더 짧은 22자 Base62 표현이 필요한 경우
 * @see KsuidTableTest 문자열 형식의 정렬 가능 ID가 필요한 경우
 */
@TestMethodOrder(MethodOrderer.MethodName::class)
class TimebasedUUIDTableTest: AbstractCustomIdTableTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t_timebased_uuid (
     *      id uuid PRIMARY KEY,
     *      "name" VARCHAR(255) NOT NULL,
     *      age INT NOT NULL
     * )
     * ```
     */
    object T1: TimebasedUUIDTable("t_timebased_uuid") {
        val name = varchar("name", 255)
        val age = integer("age")
    }

    data class Record(val name: String, val age: Int)

    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `TimebasedUUID id를 가진 레코드를 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
        withTables(testDB, T1) {
            repeat(recordCount) {
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
    fun `TimebasedUUID id를 가진 레코드를 배치로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
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

            records
                .chunked(100)
                .map { chunk ->
                    launch {
                        inTopLevelSuspendTransaction(
                            db = testDB.db,
                            transactionIsolation = testDB.db?.transactionManager?.defaultIsolationLevel,
                        ) {
                            T1.batchInsert(chunk, shouldReturnGeneratedValues = false) {
                                this[T1.name] = it.name
                                this[T1.age] = it.age
                            }
                        }
                    }
                }
                .joinAll()

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }
}
