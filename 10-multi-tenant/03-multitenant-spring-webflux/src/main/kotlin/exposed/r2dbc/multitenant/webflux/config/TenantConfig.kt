package exposed.r2dbc.multitenant.webflux.config

import exposed.r2dbc.multitenant.webflux.tenant.DataInitializer
import exposed.r2dbc.multitenant.webflux.tenant.TenantInitializer
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 멀티테넌시 관련 Bean을 등록하는 Spring 설정 클래스입니다.
 *
 * [TenantInitializer]를 Bean으로 등록하여 애플리케이션 시작 시
 * 각 테넌트별 스키마와 샘플 데이터를 초기화합니다.
 */
@Configuration
class TenantConfig {

    companion object: KLoggingChannel()

    /**
     * 테넌트별 스키마 생성 및 초기 데이터 삽입을 수행하는 [TenantInitializer]를 등록합니다.
     */
    @Bean
    fun tenantInitializer(dataInitializer: DataInitializer): TenantInitializer =
        TenantInitializer(dataInitializer)
}
