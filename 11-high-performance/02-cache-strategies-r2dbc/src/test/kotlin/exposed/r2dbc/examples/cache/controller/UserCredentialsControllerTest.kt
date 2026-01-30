package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import exposed.r2dbc.examples.cache.domain.model.UserCredentialsDTO
import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import exposed.r2dbc.examples.cache.domain.repository.UserCredentialsCacheRepository
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpDelete
import io.bluetape4k.spring.tests.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult
import java.time.Instant

class UserCredentialsControllerTest(
    @param:Autowired private val client: WebTestClient,
    @param:Autowired private val repository: UserCredentialsCacheRepository,
): AbstractCacheStrategyTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 5
    }

    private val idsInDB = mutableListOf<String>()

    @BeforeEach
    fun beforeEach() {
        runBlocking(Dispatchers.IO) {
            repository.invalidateAll()
            idsInDB.clear()

            suspendTransaction {
                UserCredentialsTable.deleteAll()

                repeat(10) {
                    idsInDB.add(insertUserCredentials())
                }
            }
        }
    }

    private suspend fun insertUserCredentials(): String {
        return suspendTransaction {
            UserCredentialsTable.insertAndGetId {
                it[UserCredentialsTable.username] = faker.credentials().username()
                it[UserCredentialsTable.email] = faker.internet().emailAddress()
                it[UserCredentialsTable.lastLoginAt] = Instant.now()
            }.value
        }
    }

    @Test
    fun `findAll user credentials`() = runSuspendIO {
        val ucs = client
            .httpGet("/user-credentials")
            .expectStatus().is2xxSuccessful
            .expectBodyList<UserCredentialsDTO>()
            .returnResult().responseBody
            .shouldNotBeNull()

        ucs shouldHaveSize idsInDB.size
    }

    @Test
    fun `find by id with read-through`() = runSuspendIO {
        idsInDB.forEach { id ->
            val uc = client
                .httpGet("/user-credentials/$id")
                .expectStatus().is2xxSuccessful
                .returnResult<UserCredentialsDTO>().responseBody
                .awaitSingle()

            log.debug { "user credentials: $uc" }
            uc.id shouldBeEqualTo id
        }
    }

    @Test
    fun `복수의 ID로 UserCredentials를 Read-Through 방식으로 조회`() = runSuspendIO {
        val ids = idsInDB.shuffled().take(5)
        log.debug { "User credentials IDs to search: $ids" }

        val ucs = client
            .httpGet("/user-credentials/all?ids=${ids.joinToString(",")}")
            .expectStatus().is2xxSuccessful
            .expectBodyList<UserCredentialsDTO>()
            .returnResult().responseBody
            .shouldNotBeNull()

        ucs shouldHaveSize ids.size
        ucs.map { it.id } shouldContainSame ids
    }

    @Test
    fun `invalidate specified cached user credentials`() = runSuspendIO {
        repository.getAll(idsInDB)

        val invalidatedIds = idsInDB.shuffled().take(3)
        val ids = invalidatedIds.joinToString(",")
        
        val invalidateCount = client
            .httpDelete("/user-credentials/invalidate?ids=$ids")
            .expectStatus().is2xxSuccessful
            .returnResult<Long>().responseBody
            .awaitSingle()

        invalidateCount shouldBeEqualTo invalidatedIds.size.toLong()
    }
}
