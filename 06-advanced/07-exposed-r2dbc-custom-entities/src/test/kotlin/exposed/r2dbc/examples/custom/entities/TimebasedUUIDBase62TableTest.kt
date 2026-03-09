package exposed.r2dbc.examples.custom.entities

import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.coroutines.flow.extensions.flowFromSuspend
import io.bluetape4k.exposed.core.dao.id.TimebasedUUIDBase62Table
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

/**
 * 시간 기반 UUID를 Base62 인코딩한 22자 문자열을 기본 키로 사용하는 테이블 예제.
 *
 * `bluetape4k-exposed` 의 [TimebasedUUIDBase62Table]을 상속받아 `VARCHAR(22)` PRIMARY KEY를
 * 자동으로 Base62 인코딩된 UUID 값으로 채웁니다. [TimebasedUUIDTable]의 128비트 UUID를
 * 대·소문자 알파벳과 숫자만으로 이루어진 22자 문자열로 압축하므로, URL 안전하고 사람이 읽기 쉽습니다.
 *
 * TimebasedUUID Base62 특성:
 * - 길이: 22자 (`[0-9A-Za-z]` 문자만 사용)
 * - 시간 정렬 가능 (UUIDv1 기반)
 * - UUID(36자)보다 짧아 저장 공간 및 인덱스 효율 향상
 * - URL, 파일명, 경로에 그대로 사용 가능 (특수문자 없음)
 *
 * 추가 테스트:
 * - [insertIgnore as flow]: Kotlin Flow를 활용한 병렬 `INSERT IGNORE` 패턴 (MySQL/PostgreSQL 전용)
 *
 * @see TimebasedUUIDTableTest 표준 UUID 형식이 필요한 경우
 * @see KsuidTableTest 순수 문자열 정렬 ID가 필요한 경우
 */
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
                }.joinAll()

            T1.selectAll().count() shouldBeEqualTo recordCount.toLong()
        }
    }


    /**
     * ```sql
     * INSERT INTO T1 (ID, "name", AGE)
     * VALUES ('1efe3b58-c940-6036-9ee6-897d7aeb3be7', 'Miss Hung Kautzer', 30) ON CONFLICT DO NOTHING
     * ```
     */
    @ParameterizedTest(name = "{0} - {1}개 레코드")
    @MethodSource("getTestDBAndEntityCount")
    fun `insertIgnore as flow`(testDB: TestDB, entityCount: Int) = runSuspendIO {
        Assumptions.assumeTrue { testDB in TestDB.ALL_MYSQL_MARIADB + TestDB.POSTGRESQL }

        withTables(testDB, T1) {
            val entities: Sequence<Pair<String, Int>> = generateSequence {
                val name = faker.name().fullName()
                val age = faker.number().numberBetween(8, 80)
                name to age
            }

            entities.asFlow()
                .buffer(16)
                .take(entityCount)
                .flatMapMerge(16) { (name, age) ->
                    flowFromSuspend {
                        inTopLevelSuspendTransaction(
                            db = testDB.db,
                            transactionIsolation = testDB.db?.transactionManager?.defaultIsolationLevel,
                        ) {
                            T1.insertIgnore {
                                it[T1.name] = name
                                it[T1.age] = age
                            }
                        }
                    }
                }
                .collect()

            T1.selectAll().count().toInt() shouldBeEqualTo entityCount
        }
    }
}
