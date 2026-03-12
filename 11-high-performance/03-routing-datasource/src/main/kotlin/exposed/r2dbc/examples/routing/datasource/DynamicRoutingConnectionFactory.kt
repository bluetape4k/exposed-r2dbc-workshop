package exposed.r2dbc.examples.routing.datasource

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * Reactor Context에서 라우팅 키를 읽어 적절한 [ConnectionFactory]로 DB 연결을 위임하는 동적 라우터입니다.
 *
 * `ConnectionFactory` 인터페이스를 구현하며, [RoutingKeyResolver]가 계산한 키로
 * [ConnectionFactoryRegistry]에서 대상 [ConnectionFactory]를 조회합니다.
 * 커넥션 요청 시점에 Reactor Context를 참조하므로, 요청마다 다른 DB 인스턴스로 라우팅할 수 있습니다.
 *
 * ## 동작 흐름
 * ```
 * create()
 *   └─ Mono.deferContextual { context ->
 *         key = keyResolver.currentLookupKey(context)   // e.g. "acme:ro"
 *         target = registry.get(key)                    // ConnectionFactory 조회
 *         Mono.from(target.create())                    // 실제 연결 생성
 *      }
 * ```
 *
 * ## 라우팅 키 예시
 * | ReactorContext 상태                        | 키          | 연결 대상           |
 * |--------------------------------------------|-------------|---------------------|
 * | 기본 (헤더 없음)                            | `default:rw` | 기본 RW DB          |
 * | `TENANT=acme`, `READ_ONLY=false`           | `acme:rw`   | acme 테넌트 RW DB   |
 * | `TENANT=acme`, `READ_ONLY=true`            | `acme:ro`   | acme 테넌트 RO DB   |
 *
 * @param registry 라우팅 키 → [ConnectionFactory] 매핑 레지스트리
 * @param keyResolver Reactor Context에서 라우팅 키를 계산하는 전략
 * @param defaultKey 기본 연결 키 (메타데이터 조회 및 폴백용)
 * @see RoutingKeyResolver
 * @see ConnectionFactoryRegistry
 * @see ContextAwareRoutingKeyResolver
 */
class DynamicRoutingConnectionFactory(
    private val registry: ConnectionFactoryRegistry,
    private val keyResolver: RoutingKeyResolver,
    private val defaultKey: String,
): ConnectionFactory {

    private val defaultFactory: ConnectionFactory =
        registry.get(defaultKey) ?: error("No ConnectionFactory for default key=$defaultKey")

    /**
     * Reactor Context에서 라우팅 키를 읽어 대상 [ConnectionFactory]에서 커넥션을 생성합니다.
     *
     * `Mono.deferContextual`을 사용하여 구독 시점의 Context를 참조합니다.
     * 키에 해당하는 [ConnectionFactory]가 없으면 에러를 발생시킵니다.
     *
     * @return 라우팅된 DB 연결을 담은 [Publisher]
     */
    override fun create(): Publisher<out Connection> =
        Mono.deferContextual { context ->
            val key = keyResolver.currentLookupKey(context)
            val target = registry.get(key)
                ?: error("No ConnectionFactory for key=$key. keys=${registry.keys().sorted()}")
            Mono.from(target.create())
        }

    override fun getMetadata(): ConnectionFactoryMetadata = defaultFactory.metadata
}
