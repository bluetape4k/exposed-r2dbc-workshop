package exposed.r2dbc.examples.types

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withDb
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Exposed R2DBC에서 Kotlin UUID(`kotlin.uuid.Uuid`)를 기본키 컬럼 타입으로 사용하는 예제.
 *
 * 주요 학습 내용:
 * - `kotlinUUID()` 컬럼 정의로 Kotlin `Uuid` 기반 기본키 생성
 * - `@OptIn(ExperimentalUuidApi::class)` 어노테이션 필요
 * - Kotlin UUID를 사용한 삽입, 조회
 * - Java UUID와 Kotlin UUID의 차이점 이해
 *
 * 주의사항:
 * - `kotlin.uuid.Uuid` API는 실험적(experimental)이므로 `@OptIn` 이 필요합니다.
 * - Kotlin UUID는 Kotlin 2.0+ 에서 사용 가능합니다.
 *
 * 모든 쿼리는 `withTables(testDB, ...)` 블록 내에서 실행됩니다.
 */
@OptIn(ExperimentalUuidApi::class)
class Ex10_KotlinUUIDColumnType: AbstractR2dbcExposedTest() {

    companion object: KLogging()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert read kotlin UUID`(testDB: TestDB) = runTest {
        val tester = object: Table("test_java_uuid") {
            val id = uuid("id")
        }
        withTables(testDB, tester) {
            val uuid = Uuid.generateV7()
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            dbUuid shouldBeEqualTo uuid
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `test kotlin UUIDColumnType`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("test_java_uuid_column_type") {
            val uuid = uuid("java_uuid")
        }

        withTables(testDB, tester) {
            val uuid = Uuid.generateV7()
            val id = tester.insertAndGetId { it[this.uuid] = uuid }
            id.shouldNotBeNull()

            val uidById = tester.selectAll()
                .where { tester.id eq id }
                .singleOrNull()
                ?.get(tester.uuid)
            uidById shouldBeEqualTo uuid

            val uidByKey = tester.selectAll()
                .where { tester.uuid eq uuid }
                .singleOrNull()
                ?.get(tester.uuid)
            uidByKey shouldBeEqualTo uuid
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `mariadb 전용 uuid 수형`(testDB: TestDB) = runTest {
        Assumptions.assumeTrue { testDB == TestDB.MARIADB }

        val tester = object: Table("test_java_uuid_mariadb") {
            val id = uuid("id")
        }

        withDb(testDB) {
            try {
                // From the version 10.7 MariaDB has own 'UUID' column type, that does not work with the current column type.
                // Even if we generate on DDL type 'BINARY(16)' we could support native UUID for IO operations.
                exec("CREATE TABLE test_java_uuid_mariadb (id UUID NOT NULL)")

                val uuid = Uuid.generateV7()
                tester.insert {
                    it[id] = uuid
                }

                val dbUuid = tester.selectAll().first()[tester.id]
                dbUuid shouldBeEqualTo uuid
            } finally {
                exec("DROP TABLE IF EXISTS test_java_uuid_mariadb")
            }
        }
    }
}
