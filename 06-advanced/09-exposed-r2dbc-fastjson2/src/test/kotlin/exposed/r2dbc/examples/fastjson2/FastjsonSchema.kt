package exposed.r2dbc.examples.fastjson2

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.fastjson2.fastjson
import io.bluetape4k.exposed.core.fastjson2.fastjsonb
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId

/**
 * Fastjson2 기반 JSON/JSONB 컬럼 테스트에 사용하는 공유 스키마 및 헬퍼 함수 모음.
 *
 * `bluetape4k-exposed` 의 `fastjson()` / `fastjsonb()` 확장 함수를 사용하여
 * Kotlin 객체를 JSON(텍스트) 또는 JSONB(바이너리) 형식으로 컬럼에 저장합니다.
 * Fastjson2는 Alibaba가 개발한 고성능 JSON 라이브러리로, 다음 특징을 가집니다:
 *
 * ## Fastjson2 고성능 직렬화 특징
 *
 * - **고속 직렬화/역직렬화**: Jackson 대비 2~3배 빠른 처리 속도 (벤치마크 기준)
 * - **낮은 메모리 사용량**: 바이트 레벨 최적화로 GC 압력 감소
 * - **어노테이션 불필요**: 표준 Kotlin 데이터 클래스에 `@Serializable` 없이도 동작
 * - **JSONB 지원**: 바이너리 JSON 포맷으로 저장 시 추가 파싱 오버헤드 감소
 * - **JSONPath 지원**: `.extract<T>()`, `.contains()`, `.exists()` 등 Exposed JSON 쿼리 함수와 완벽 호환
 * - **Kotlin 친화적**: Kotlin 기본 타입, nullable 타입, 컬렉션, data class 를 자연스럽게 처리
 *
 * > **참고**: 특수 직렬화가 필요한 경우 `@JSONField` 어노테이션을 사용할 수 있습니다.
 *   JSON 스키마 진화 시 `@JSONType(ignores = ["oldField"])` 로 필드를 무시할 수 있습니다.
 *
 * 제공 테이블:
 * - [FastjsonTable]: JSON 컬럼 테이블 (`fastjson_table`)
 * - [FastjsonBTable]: JSONB 컬럼 테이블 (`fastjson_b_table`)
 * - [FastjsonArrayTable]: JSON 배열 컬럼 테이블 (`fastjson_arrays`)
 * - [FastjsonBArrayTable]: JSONB 배열 컬럼 테이블 (`fastjson_b_arrays`)
 *
 * 제공 데이터 클래스:
 * - [DataHolder]: 테스트용 복합 객체 (중첩 [User] 포함)
 * - [User]: 사용자 정보 (이름, 팀)
 * - [UserGroup]: 사용자 목록 그룹
 *
 * 제공 헬퍼 함수:
 * - [withFastjsonTable]: [FastjsonTable]을 생성하고 초기 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withFastjsonBTable]: [FastjsonBTable]을 생성하고 초기 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withFastjsonArrays]: [FastjsonArrayTable]에 배열 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withFastjsonBArrays]: [FastjsonBArrayTable]에 배열 데이터를 삽입한 뒤 테스트 블록 실행
 */
@Suppress("UnusedReceiverParameter")
object FastjsonSchema {

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS fastjson_table (
     *      id SERIAL PRIMARY KEY,
     *      fastjson_column JSON NOT NULL
     * )
     * ```
     */
    object FastjsonTable: IntIdTable("fastjson_table") {
        val fastjsonColumn = fastjson<DataHolder>("fastjson_column")
    }

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS fastjson_b_table (
     *      id SERIAL PRIMARY KEY,
     *      fastjson_b_column JSONB NOT NULL
     * );
     * ```
     */
    object FastjsonBTable: IntIdTable("fastjson_b_table") {
        val fastjsonBColumn = fastjsonb<DataHolder>("fastjson_b_column")
    }

    /**
     * ```sql
     * CREATE TABLE IF NOT EXISTS fastjson_arrays (
     *      id SERIAL PRIMARY KEY,
     *      "groups" JSON NOT NULL,
     *      numbers JSON NOT NULL
     * );
     * ```
     */
    object FastjsonArrayTable: IntIdTable("fastjson_arrays") {
        val groups = fastjson<UserGroup>("groups")
        val numbers = fastjson<IntArray>("numbers")
    }

    object FastjsonBArrayTable: IntIdTable("fastjson_b_arrays") {
        val groups = fastjsonb<UserGroup>("groups")
        val numbers = fastjsonb<IntArray>("numbers")
    }


    data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

    data class User(val name: String, val team: String?)

    data class UserGroup(val users: List<User>)

    suspend fun AbstractR2dbcExposedTest.withFastjsonTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: FastjsonTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = FastjsonTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.fastjsonColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun AbstractR2dbcExposedTest.withFastjsonBTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: FastjsonBTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = FastjsonBTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.fastjsonBColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun AbstractR2dbcExposedTest.withFastjsonArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: FastjsonArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = FastjsonArrayTable

        withTables(testDB, tester) {
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)
        }
    }

    suspend fun AbstractR2dbcExposedTest.withFastjsonBArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: FastjsonBArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = FastjsonBArrayTable

        withTables(testDB, tester) {
            val singleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(listOf(User("A", "Team A")))
                it[tester.numbers] = intArrayOf(100)
            }
            val tripleId = tester.insertAndGetId {
                it[tester.groups] = UserGroup(List(3) { i -> User("${'B' + i}", "Team ${'B' + i}") })
                it[tester.numbers] = intArrayOf(3, 4, 5)
            }

            statement(tester, singleId, tripleId)
        }
    }
}
