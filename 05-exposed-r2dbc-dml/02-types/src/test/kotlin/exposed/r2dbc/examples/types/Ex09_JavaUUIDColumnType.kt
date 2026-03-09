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
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.*

/**
 * Exposed R2DBC에서 Java UUID를 기본키 컬럼 타입으로 사용하는 예제.
 *
 * 주요 학습 내용:
 * - `javaUUID()` 컬럼 정의로 `java.util.UUID` 기반 기본키 생성
 * - `UUIDTable` 상속 및 UUID 자동 생성
 * - UUID를 기반으로 한 삽입, 조회, 삭제
 * - `database-generated UUID` (DB 측 UUID 생성 함수 사용)
 *
 * 주의사항:
 * - Java UUID는 모든 지원 DB (H2, PostgreSQL, MySQL, MariaDB)에서 사용 가능합니다.
 * - DB에 따라 UUID 저장 방식이 다를 수 있습니다 (바이너리 vs 문자열).
 *
 * 모든 쿼리는 `withTables(testDB, ...)` 블록 내에서 실행됩니다.
 */
class Ex09_JavaUUIDColumnType: AbstractR2dbcExposedTest() {

    companion object: KLogging()

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `insert read java UUID`(testDB: TestDB) = runTest {
        val tester = object: Table("test_java_uuid") {
            val id = javaUUID("id")
        }
        withTables(testDB, tester) {
            val uuid = UUID.randomUUID()
            tester.insert {
                it[id] = uuid
            }
            val dbUuid = tester.selectAll().first()[tester.id]
            dbUuid shouldBeEqualTo uuid
        }
    }

    @ParameterizedTest
    @MethodSource(ENABLE_DIALECTS_METHOD)
    fun `test java UUIDColumnType`(testDB: TestDB) = runTest {
        val tester = object: IntIdTable("test_java_uuid_column_type") {
            val uuid = javaUUID("java_uuid")
        }

        withTables(testDB, tester) {
            val uuid = UUID.randomUUID()
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
            val id = javaUUID("id")
        }

        withDb(testDB) {
            try {
                // From the version 10.7 MariaDB has own 'UUID' column type, that does not work with the current column type.
                // Even if we generate on DDL type 'BINARY(16)' we could support native UUID for IO operations.
                exec("CREATE TABLE test_java_uuid_mariadb (id UUID NOT NULL)")

                val uuid = UUID.randomUUID()
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
