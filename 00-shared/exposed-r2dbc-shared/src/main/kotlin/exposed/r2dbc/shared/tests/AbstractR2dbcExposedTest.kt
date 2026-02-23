package exposed.r2dbc.shared.tests

import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Schema
import java.util.*

/**
 * Exposed R2DBC 예제 테스트에서 공통으로 사용하는 기반 클래스입니다.
 *
 * 테스트 실행 시 타임존을 UTC로 고정하여 DB별 시간 관련 차이를 줄입니다.
 */
abstract class AbstractR2dbcExposedTest {

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    companion object: KLogging() {
        @JvmStatic
        val faker = Fakers.faker

        /**
         * 현재 실행 환경에서 활성화 가능한 테스트 DB dialect 목록을 제공합니다.
         */
        @JvmStatic
        fun enableDialects() = TestDB.enabledDialects()

        const val ENABLE_DIALECTS_METHOD = "enableDialects"
    }


    /**
     * 현재 dialect가 `IF NOT EXISTS` 구문을 지원하면 해당 절을 반환합니다.
     */
    fun addIfNotExistsIfSupported() = if (currentDialectTest.supportsIfNotExists) {
        "IF NOT EXISTS "
    } else {
        ""
    }

    /**
     * 스키마 테스트에 사용할 [Schema] 객체를 생성합니다.
     *
     * @param schemaName 생성할 스키마 이름
     */
    protected fun prepareSchemaForTest(schemaName: String): Schema = Schema(
        schemaName,
        defaultTablespace = "USERS",
        temporaryTablespace = "TEMP ",
        quota = "20M",
        on = "USERS"
    )

}
