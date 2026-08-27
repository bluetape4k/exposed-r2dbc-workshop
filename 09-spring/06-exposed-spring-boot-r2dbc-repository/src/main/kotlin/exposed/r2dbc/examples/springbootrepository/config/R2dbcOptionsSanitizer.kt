package exposed.r2dbc.examples.springbootrepository.config

import io.r2dbc.spi.ConnectionFactoryOptions

/**
 * 연결 옵션에서 자격 증명을 제외한 제한된 진단 정보만 반환합니다.
 */
internal fun ConnectionFactoryOptions.sanitizedSummary(): String = listOfNotNull(
    "driver=${getValue(ConnectionFactoryOptions.DRIVER)}",
    getValue(ConnectionFactoryOptions.PROTOCOL)?.let { "protocol=$it" },
    getValue(ConnectionFactoryOptions.HOST)?.let { "host=$it" },
    getValue(ConnectionFactoryOptions.PORT)?.let { "port=$it" },
    getValue(ConnectionFactoryOptions.DATABASE)?.let { "database=$it" },
).joinToString(", ")
