package exposed.r2dbc.shared.tests

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Schema
import java.util.*

/**
 * [AbstractR2dbcExposedTest] - Exposed R2DBC 예제 테스트의 공통 기반 클래스
 *
 * 모든 Exposed R2DBC 테스트 클래스는 이 클래스를 상속하여 일관된 테스트 환경을 구성합니다.
 *
 * ## 주요 기능
 * - 테스트 실행 시 JVM 타임존을 UTC로 고정 (DB별 시간 처리 차이 제거)
 * - [enableDialects] 메서드를 통해 활성화된 DB 목록 제공 (`@MethodSource` 연동)
 * - Faker 인스턴스 제공 (테스트 데이터 생성용)
 *
 * ## 사용 예제
 * ```kotlin
 * class MyExposedTest: AbstractR2dbcExposedTest() {
 *
 *     companion object: KLoggingChannel()
 *
 *     @ParameterizedTest
 *     @MethodSource(ENABLE_DIALECTS_METHOD)
 *     fun `select example`(testDB: TestDB) = runTest {
 *         withTables(testDB, MyTable) {
 *             // Exposed R2DBC DSL 사용
 *             MyTable.insert { it[name] = "test" }
 *             val rows = MyTable.selectAll().toList()
 *             rows shouldHaveSize 1
 *         }
 *     }
 * }
 * ```
 *
 * ## 지원 DB
 * H2, H2_MYSQL, H2_PSQL, H2_MARIADB, MARIADB, MYSQL_V8, POSTGRESQL
 *
 * @see TestDB
 * @see withDb
 * @see withTables
 */
abstract class AbstractR2dbcExposedTest {

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    companion object: KLogging() {
        @JvmStatic
        val faker = Fakers.faker

        /**
         * 현재 실행 환경에서 활성화 가능한 테스트 DB dialect 목록을 반환합니다.
         *
         * JUnit5 `@MethodSource` 어노테이션과 연동되어 파라미터화 테스트를 구동합니다.
         *
         * 활성 DB는 다음 Gradle 프로퍼티로 제어합니다:
         * - `-PuseDB=H2,POSTGRESQL` — 지정한 DB만 테스트
         * - `-PuseFastDB=true` — H2 인메모리만 테스트
         * - 기본값: H2, POSTGRESQL, MYSQL_V8
         *
         * @return 활성화된 [TestDB] 집합
         * @see TestDB.enabledDialects
         */
        @JvmStatic
        fun enableDialects() = TestDB.enabledDialects()

        /**
         * `@MethodSource` 어노테이션에 사용할 메서드 이름 상수.
         *
         * ```kotlin
         * @ParameterizedTest
         * @MethodSource(ENABLE_DIALECTS_METHOD)
         * fun myTest(testDB: TestDB) = runTest { ... }
         * ```
         */
        const val ENABLE_DIALECTS_METHOD = "enableDialects"
    }


    /**
     * 현재 dialect가 `IF NOT EXISTS` 구문을 지원하면 해당 절을 반환합니다.
     *
     * DDL 문 생성 시 DB별 호환성을 유지하는 데 활용합니다.
     *
     * @return `"IF NOT EXISTS "` 또는 `""`
     */
    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

    /**
     * 스키마 테스트에 사용할 [Schema] 객체를 생성합니다.
     *
     * Oracle 호환 스키마 옵션(tablespace, quota)이 포함되며,
     * H2 Oracle 모드 및 실제 Oracle DB 테스트에 사용합니다.
     *
     * @param schemaName 생성할 스키마 이름
     * @return 설정이 적용된 [Schema] 인스턴스
     */
    protected fun prepareSchemaForTest(schemaName: String): Schema = Schema(
        schemaName,
        defaultTablespace = "USERS",
        temporaryTablespace = "TEMP ",
        quota = "20M",
        on = "USERS"
    )

}
