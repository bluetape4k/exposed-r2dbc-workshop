package exposed.r2dbc.examples.springbootrepository.config

import exposed.r2dbc.examples.springbootrepository.AbstractProductRepositoryTest
import exposed.r2dbc.examples.springbootrepository.repository.ProductR2dbcRepository
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 지정한 base package에서 provider repository proxy가 생성되는지 검증합니다.
 */
class RepositoryScanTest : AbstractProductRepositoryTest() {

    @Autowired
    private lateinit var repository: ProductR2dbcRepository

    @Test
    fun `provider scans product repository`() {
        repository.shouldNotBeNull()
    }
}
