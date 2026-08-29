package exposed.r2dbc.examples.springbootrepository.domain.repository

import exposed.r2dbc.examples.springbootrepository.domain.ProductRecord
import exposed.r2dbc.examples.springbootrepository.repository.ProductR2dbcRepository
import exposed.r2dbc.examples.springbootrepository.support.AbstractProductDatabaseTest
import exposed.r2dbc.examples.springbootrepository.support.RecordingConnectionFactory
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactoryOptions
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.beans.factory.annotation.Autowired

/**
 * provider repository의 nullable ID mapping과 기본 CRUD/Flow 동작을 검증합니다.
 */
@ResourceLock("issue-204-products-h2", mode = ResourceAccessMode.READ_WRITE)
class ProductR2dbcRepositoryTest : AbstractProductDatabaseTest() {

    @Autowired
    private lateinit var repository: ProductR2dbcRepository

    @Autowired
    private lateinit var connectionPool: ConnectionPool

    @Autowired
    private lateinit var connectionFactoryOptions: ConnectionFactoryOptions

    @Test
    fun `find all and count use provider transaction`() = runTest {
        repository.count() shouldBeEqualTo 2L
        repository.findAll().toList().size shouldBeEqualTo 2
    }

    @Test
    fun `save find and delete product`() = runTest {
        val saved = repository.save(ProductRecord(null, "Pencil", "HB"))
        val id = saved.id.shouldNotBeNull()

        repository.findByIdOrNull(id) shouldBeEqualTo saved
        repository.existsById(id).shouldBeTrue()

        repository.deleteById(id)
        repository.findByIdOrNull(id).shouldBeNull()
    }

    @Test
    fun `save with existing id preserves update semantics`() = runTest {
        val existing = repository.findAll().toList().first()
        val existingId = existing.id.shouldNotBeNull()
        val updated = existing.copy(name = "Notebook updated", description = "Updated fixture")

        val saved = repository.save(updated)

        saved.id shouldBeEqualTo existingId
        repository.findByIdOrNull(existingId) shouldBeEqualTo updated
        repository.count() shouldBeEqualTo 2L
    }

    @Test
    fun `single call constraint failure rolls back the failed insert`() = runTest {
        // bluetape4k-exposed 2.x direct proxy는 reflection target exception을 그대로 재전파합니다.
        assertFailsWith<IllegalArgumentException> {
            repository.save(ProductRecord(null, "x".repeat(121), null))
        }

        repository.count() shouldBeEqualTo 2L
    }

    @Test
    fun `stream all supports bounded Flow consumption`() = runTest {
        val recorder = RecordingConnectionFactory(connectionPool)
        val options = connectionFactoryOptions
        val recordingDatabase = R2dbcDatabase.connect(
            recorder,
            R2dbcDatabaseConfig {
                dispatcher = Dispatchers.IO
                connectionFactoryOptions { from(options) }
            },
        )

        try {
            val rows = repository.streamAll(recordingDatabase).take(1).toList()

            rows.size shouldBeEqualTo 1
            rows.single().id.shouldNotBeNull()
            recorder.acquiredCount shouldBeEqualTo 1
            recorder.closedCount shouldBeEqualTo 1
            recorder.openCount shouldBeEqualTo 0
        } finally {
            // 테스트용 database manager를 전역 registry에서 제거해 다음 테스트의 primary를 보존합니다.
            TransactionManager.closeAndUnregister(recordingDatabase)
        }
    }
}
