package exposed.r2dbc.examples.routing.datasource

import io.r2dbc.spi.ConnectionFactory

/**
 * 라우팅 키에 대응하는 [ConnectionFactory]를 등록/조회하는 레지스트리입니다.
 */
interface ConnectionFactoryRegistry {

    /**
     * [key]에 [connectionFactory]를 등록합니다.
     */
    fun register(key: String, connectionFactory: ConnectionFactory)

    /**
     * [key]에 해당하는 [ConnectionFactory]를 반환합니다.
     */
    fun get(key: String): ConnectionFactory?

    /**
     * 현재 등록된 키 목록을 반환합니다.
     */
    fun keys(): Set<String>
}
