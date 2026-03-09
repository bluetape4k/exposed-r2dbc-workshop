package exposed.r2dbc.shared.tests

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils

/**
 * 지정한 [dialect] DB에서 주어진 [schemas]를 생성한 후 [statement]를 실행하고, 완료 후 스키마를 정리합니다.
 *
 * 현재 dialect가 스키마 생성을 지원하지 않으면 아무 작업도 수행하지 않습니다.
 * 테스트 실행 후 `finally` 블록에서 `CASCADE` 옵션으로 스키마를 삭제하여
 * 테스트 간 격리를 보장합니다.
 *
 * @param dialect 테스트에 사용할 데이터베이스
 * @param schemas 테스트에서 사용할 스키마 목록
 * @param configure 데이터베이스 구성 커스터마이징
 * @param statement 스키마가 준비된 트랜잭션에서 실행할 테스트 코드
 */
suspend fun withSchemas(
    dialect: TestDB,
    vararg schemas: Schema,
    configure: (DatabaseConfig.Builder.() -> Unit)? = {},
    statement: suspend R2dbcTransaction.() -> Unit,
) {
    withDb(dialect, configure = configure) {
        if (currentDialectTest.supportsCreateSchema) {
            SchemaUtils.createSchema(*schemas)
            try {
                statement()
                commit()     // Need commit to persist data before drop schemas
            } finally {
                SchemaUtils.dropSchema(*schemas, cascade = true)
                commit()
            }
        }
    }
}
