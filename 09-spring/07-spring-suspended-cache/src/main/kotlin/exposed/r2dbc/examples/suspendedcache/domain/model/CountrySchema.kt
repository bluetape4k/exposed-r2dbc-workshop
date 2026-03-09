package exposed.r2dbc.examples.suspendedcache.domain.model

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * 국가 정보를 저장하는 Exposed R2DBC 테이블 정의.
 *
 * ```sql
 * CREATE TABLE IF NOT EXISTS countries (
 *     id SERIAL PRIMARY KEY,
 *     code VARCHAR(2) NOT NULL UNIQUE,
 *     name VARCHAR(255) NOT NULL,
 *     description TEXT NULL
 * );
 * ```
 *
 * - [code]: ISO 3166-1 alpha-2 국가 코드 (예: "KR", "US")
 * - [description]: 지연 로딩 없이 즉시 로딩 (`eagerLoading = true`)
 */
object CountryTable: IntIdTable("countries") {
    val code = varchar("code", 2).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description", eagerLoading = true).nullable()
}

/**
 * 국가 정보를 담는 불변 데이터 전송 객체(DTO).
 *
 * [CountryTable]의 조회 결과를 애플리케이션 레이어에 전달할 때 사용합니다.
 * 캐시 키로 [code]를 사용하며, [toCountryRecord] 확장 함수로 [ResultRow]에서 변환합니다.
 */
data class CountryRecord(
    val code: String,
    val name: String,
    val description: String? = null,
)

/**
 * [ResultRow]를 [CountryRecord]로 변환하는 확장 함수.
 *
 * [code]는 항상 대문자로 정규화합니다. [description]이 공백 문자열인 경우 `null`로 처리합니다.
 */
fun ResultRow.toCountryRecord(): CountryRecord {
    return CountryRecord(
        code = this[CountryTable.code].uppercase(),
        name = this[CountryTable.name],
        description = this[CountryTable.description]?.takeIf { it.isNotBlank() },
    )
}
