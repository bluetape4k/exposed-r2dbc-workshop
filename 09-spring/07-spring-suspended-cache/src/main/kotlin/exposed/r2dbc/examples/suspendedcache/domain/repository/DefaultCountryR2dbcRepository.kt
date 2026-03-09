package exposed.r2dbc.examples.suspendedcache.domain.repository

import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord
import exposed.r2dbc.examples.suspendedcache.domain.model.CountryTable
import exposed.r2dbc.examples.suspendedcache.domain.model.toCountryRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

/**
 * [CountryR2dbcRepository]의 기본 구현체. Exposed R2DBC DSL을 사용하여 DB에 직접 접근합니다.
 *
 * ## Repository 패턴 + Exposed R2DBC
 * Spring Data R2DBC 없이 Exposed의 [suspendTransaction] 안에서 직접 DSL을 사용합니다.
 * 모든 메서드는 `suspend` 함수로 선언되어 코루틴 컨텍스트에서 비동기적으로 실행됩니다.
 *
 * ## 캐시 전략
 * 이 구현체 자체는 캐시를 보유하지 않습니다. 캐시는 상위 레이어(서비스, 컨트롤러)에서
 * Spring Cache 추상화(예: Lettuce/Redis)를 통해 적용됩니다.
 * [evictCacheAll]은 캐시를 직접 보유한 구현체에서 오버라이드하여 캐시 무효화 로직을 추가합니다.
 *
 * @see CountryR2dbcRepository
 * @see suspendTransaction
 */
class DefaultCountryR2dbcRepository: CountryR2dbcRepository {

    companion object: KLoggingChannel()

    /**
     * 국가 코드로 국가 정보를 조회합니다.
     *
     * @param code ISO 3166-1 alpha-2 국가 코드 (예: "KR")
     * @return 해당 코드의 [CountryRecord], 없으면 `null`
     */
    override suspend fun findByCode(code: String): CountryRecord? = suspendTransaction {
        CountryTable.selectAll()
            .where { CountryTable.code eq code }
            .singleOrNull()
            ?.toCountryRecord()
    }

    /**
     * 국가 정보를 업데이트합니다. [CountryRecord.code]로 대상 행을 식별합니다.
     *
     * @return 업데이트된 행의 수
     */
    override suspend fun update(countryRecord: CountryRecord): Int = suspendTransaction {
        CountryTable.update({ CountryTable.code eq countryRecord.code }) {
            it[name] = countryRecord.name
            it[description] = countryRecord.description
        }
    }

    /**
     * 전체 캐시를 무효화합니다. 이 기본 구현체는 캐시를 보유하지 않으므로 아무 작업도 수행하지 않습니다.
     */
    override suspend fun evictCacheAll() {
        // Nothing to do.
    }
}
