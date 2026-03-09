package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.dao.id.KsuidMillisTable
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
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

/**
 * KSUID Millis(밀리초 단위 K-Sortable Unique Identifier)를 기본 키로 사용하는 테이블 예제.
 *
 * `bluetape4k-exposed` 의 [KsuidMillisTable]을 상속받아 `VARCHAR(27)` PRIMARY KEY를 자동으로
 * KSUID Millis 값으로 채웁니다. [KsuidTable]과 달리 **밀리초(millisecond) 단위** 정밀도로
 * 타임스탬프를 인코딩하여, 같은 초(second) 안에서도 정렬 가능합니다.
 *
 * KSUID Millis 특성:
 * - 길이: 27자 (Base62 인코딩)
 * - 시간 정렬 가능 (밀리초 단위, [KsuidTable]보다 세밀한 정렬)
 * - 전역 고유성 보장
 * - 고빈도 이벤트(로그, 트랜잭션 등) ID 생성에 적합
 *
 * @see KsuidTableTest 초 단위 정밀도로 충분한 경우
 * @see TimebasedUUIDTableTest UUID 형식의 시간 기반 ID가 필요한 경우
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KsuidMillisTableTest: AbstractCustomIdTableTest() {

    companion object: KLoggingChannel()

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS t_ksuid_millis (
     *      id VARCHAR(27) PRIMARY KEY,
     *      "name" VARCHAR(255) NOT NULL,
     *      age INT NOT NULL
     * )
     * ```
     */
    object T1: KsuidMillisTable("t_ksuid_millis") {
        val name = varchar("name", 255)
        val age = integer("age")
    }

    data class Record(val name: String, val age: Int)

    @Order(0)
    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `KsuidMillis id를 가진 레코드를 낱개로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
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

    @Order(1)
    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource(GET_TESTDB_AND_ENTITY_COUNT)
    fun `KsuidMillis id를 가진 레코드를 배치로 생성한다`(testDB: TestDB, recordCount: Int) = runTest {
        withTables(testDB, T1) {
            val records = List(recordCount) {
                Record(
                    name = faker.name().fullName(),
                    age = Random.nextInt(10, 80)
                )
            }

            records.chunked(100).forEach { chunk ->
                T1.batchInsert(chunk, shouldReturnGeneratedValues = false) {
                    this[T1.name] = it.name
                    this[T1.age] = it.age
                }
            }

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }

    @Order(2)
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
