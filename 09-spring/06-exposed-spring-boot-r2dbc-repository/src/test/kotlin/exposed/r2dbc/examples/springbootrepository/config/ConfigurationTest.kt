package exposed.r2dbc.examples.springbootrepository.config

import exposed.r2dbc.examples.springbootrepository.AbstractProductRepositoryTest
import io.bluetape4k.assertions.shouldNotBeNull
import io.r2dbc.pool.ConnectionPool
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 애플리케이션이 R2DBC pool과 Exposed database를 직접 소유하는지 검증합니다.
 */
class ConfigurationTest : AbstractProductRepositoryTest() {

    @Autowired
    private lateinit var connectionPool: ConnectionPool

    @Autowired
    private lateinit var database: R2dbcDatabase

    @Test
    fun `application owned pool and database are available`() {
        connectionPool.shouldNotBeNull()
        database.shouldNotBeNull()
    }
}
