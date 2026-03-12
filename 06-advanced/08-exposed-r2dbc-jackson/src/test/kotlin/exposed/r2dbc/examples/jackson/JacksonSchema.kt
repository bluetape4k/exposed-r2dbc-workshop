package exposed.r2dbc.examples.jackson


import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import exposed.r2dbc.shared.tests.TestDB
import exposed.r2dbc.shared.tests.withTables
import io.bluetape4k.exposed.core.jackson.jackson
import io.bluetape4k.exposed.core.jackson.jacksonb
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId

/**
 * Jackson 2.x 기반 JSON/JSONB 컬럼 테스트에 사용하는 공유 스키마 및 헬퍼 함수 모음.
 *
 * `bluetape4k-exposed` 의 `jackson()` / `jacksonb()` 확장 함수를 사용하여
 * Kotlin 객체를 JSON(텍스트) 또는 JSONB(바이너리) 형식으로 컬럼에 저장합니다.
 * `@Serializable` 어노테이션 없이 표준 Kotlin 데이터 클래스를 그대로 사용할 수 있습니다.
 *
 * ## ObjectMapper 설정 주의사항
 *
 * 내부적으로 `KotlinModule`이 자동 등록된 `ObjectMapper`를 사용합니다.
 * - `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 기본값은 `true`이므로
 *   스키마 진화가 필요한 경우 `false`로 설정하거나 `@JsonIgnoreProperties(ignoreUnknown = true)`를 사용하세요.
 * - `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` 기본값은 `true`이므로
 *   날짜를 ISO 8601 문자열로 저장하려면 `false`로 설정하세요.
 * - `ObjectMapper`는 스레드 안전(thread-safe)하므로 단일 인스턴스를 공유해도 됩니다.
 *
 * 제공 테이블:
 * - [JacksonTable]: JSON 컬럼 테이블 (`jackson_table`)
 * - [JacksonBTable]: JSONB 컬럼 테이블 (`jackson_b_table`)
 * - [JacksonArrayTable]: JSON 배열 컬럼 테이블 (`jackson_arrays`)
 * - [JacksonBArrayTable]: JSONB 배열 컬럼 테이블 (`jackson_b_arrays`)
 *
 * 제공 데이터 클래스:
 * - [DataHolder]: 테스트용 복합 객체 (중첩 [User] 포함)
 * - [User]: 사용자 정보 (이름, 팀)
 * - [UserGroup]: 사용자 목록 그룹
 *
 * 제공 헬퍼 함수:
 * - [withJacksonTable]: [JacksonTable]을 생성하고 초기 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withJacksonBTable]: [JacksonBTable]을 생성하고 초기 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withJacksonArrays]: [JacksonArrayTable]에 배열 데이터를 삽입한 뒤 테스트 블록 실행
 * - [withJacksonBArrays]: [JacksonBArrayTable]에 배열 데이터를 삽입한 뒤 테스트 블록 실행
 */
@Suppress("UnusedReceiverParameter")
object JacksonSchema {

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS jackson_table (
     *      id SERIAL PRIMARY KEY,
     *      jackson_column JSON NOT NULL
     * )
     * ```
     */
    object JacksonTable: IntIdTable("jackson_table") {
        val jacksonColumn = jackson<DataHolder>("jackson_column")
    }

    /**
     * ```sql
     * -- Postgres
     * CREATE TABLE IF NOT EXISTS jackson_b_table (
     *      id SERIAL PRIMARY KEY,
     *      jackson_b_column JSONB NOT NULL
     * );
     * ```
     */
    object JacksonBTable: IntIdTable("jackson_b_table") {
        val jacksonBColumn = jacksonb<DataHolder>("jackson_b_column")
    }

    /**
     * ```sql
     * CREATE TABLE IF NOT EXISTS jackson_arrays (
     *      id SERIAL PRIMARY KEY,
     *      "groups" JSON NOT NULL,
     *      numbers JSON NOT NULL
     * );
     * ```
     */
    object JacksonArrayTable: IntIdTable("jackson_arrays") {
        val groups = jackson<UserGroup>("groups")
        val numbers = jackson<IntArray>("numbers")
    }

    object JacksonBArrayTable: IntIdTable("jackson_b_arrays") {
        val groups = jacksonb<UserGroup>("groups")
        val numbers = jacksonb<IntArray>("numbers")
    }


    data class DataHolder(val user: User, val logins: Int, val active: Boolean, val team: String?)

    data class User(val name: String, val team: String?)

    data class UserGroup(val users: List<User>)

    suspend fun AbstractR2dbcExposedTest.withJacksonTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: JacksonTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = JacksonTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.jacksonColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun AbstractR2dbcExposedTest.withJacksonBTable(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(tester: JacksonBTable, user1: User, data1: DataHolder) -> Unit,
    ) {
        val tester = JacksonBTable

        withTables(testDB, tester) {
            val user1 = User("Admin", null)
            val data1 = DataHolder(user1, 10, true, null)

            tester.insert {
                it[tester.jacksonBColumn] = data1
            }

            statement(tester, user1, data1)
        }
    }

    suspend fun AbstractR2dbcExposedTest.withJacksonArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: JacksonArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = JacksonArrayTable

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

    suspend fun AbstractR2dbcExposedTest.withJacksonBArrays(
        testDB: TestDB,
        statement: suspend R2dbcTransaction.(
            tester: JacksonBArrayTable,
            singleId: EntityID<Int>,
            tripleId: EntityID<Int>,
        ) -> Unit,
    ) {
        // Assumptions.assumeTrue(testDB !in TestDB.ALL_H2_V1, "H2V1 does not support JSON arrays")

        val tester = JacksonBArrayTable

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
