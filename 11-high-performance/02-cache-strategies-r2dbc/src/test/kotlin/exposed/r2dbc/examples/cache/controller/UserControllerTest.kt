package exposed.r2dbc.examples.cache.controller

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import exposed.r2dbc.examples.cache.domain.model.UserRecord
import exposed.r2dbc.examples.cache.domain.model.UserTable
import exposed.r2dbc.examples.cache.domain.model.newUserRecord
import exposed.r2dbc.examples.cache.domain.repository.UserCacheRepository
import io.bluetape4k.exposed.core.statements.api.toExposedBlob
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpDelete
import io.bluetape4k.spring.tests.httpGet
import io.bluetape4k.spring.tests.httpPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.test.web.reactive.server.returnResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class UserControllerTest(
    @param:Autowired private val client: WebTestClient,
    @param:Autowired private val repository: UserCacheRepository,
): AbstractCacheStrategyTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 5
    }

    private val idsInDB = CopyOnWriteArrayList<Long>()

    @BeforeEach
    fun beforeEach() {
        runBlocking(Dispatchers.IO) {
            repository.invalidateAll()
            idsInDB.clear()

            suspendTransaction {
                UserTable.deleteAll()
                insertUsers(10)
            }
        }
    }

    private suspend fun insertUsers(size: Int) {
        val users = List(size) { newUserRecord() }
        val rows = UserTable.batchInsert(users) {
            this[UserTable.username] = it.username
            this[UserTable.firstName] = it.firstName
            this[UserTable.lastName] = it.lastName
            this[UserTable.address] = it.address
            this[UserTable.zipcode] = it.zipcode
            this[UserTable.birthDate] = it.birthDate
            this[UserTable.avatar] = it.avatar!!.toExposedBlob()
        }
        idsInDB.addAll(rows.map { it[UserTable.id].value }.sorted())
    }

    @Test
    fun `모든 사용자를 조회`() = runSuspendIO {
        val users = client
            .httpGet("/users")
            .expectStatus().is2xxSuccessful
            .expectBodyList<UserRecord>()
            .returnResult().responseBody
            .shouldNotBeNull()

        users shouldHaveSize idsInDB.size
    }

    @Test
    fun `User ID로 User를 Read-Through 방식으로 조회`() = runSuspendIO {
        idsInDB.forEach { userId ->
            val user = client
                .httpGet("/users/$userId")
                .expectStatus().is2xxSuccessful
                .returnResult<UserRecord>().responseBody
                .awaitSingle()

            user.id shouldBeEqualTo userId
        }
    }

    @Test
    fun `복수의 User ID로 User를 Read-Through 방식으로 조회`() = runSuspendIO {
        val userIds = idsInDB.shuffled().take(5)
        log.debug { "User IDs to search: $userIds" }

        val users = client
            .httpGet("/users/all?ids=${userIds.joinToString(",")}")
            .expectStatus().is2xxSuccessful
            .expectBodyList<UserRecord>()
            .returnResult().responseBody
            .shouldNotBeNull()

        users shouldHaveSize userIds.size
        users.map { it.id } shouldContainSame userIds
    }

    @Test
    fun `새로운 User를 write through 로 저장하기`() = runSuspendIO {
        val newUser = newUserRecord(Random.nextLong(1000L, 9999L))
        val user = client
            .httpPost("/users", newUser)
            .expectStatus().is2xxSuccessful
            .returnResult<UserRecord>().responseBody
            .awaitSingle()

        user.id shouldBeEqualTo newUser.id
    }

    @Test
    fun `invalidate specified id cached user`() = runSuspendIO {
        repository.getAll(idsInDB)

        val invalidatedId = idsInDB.shuffled().take(3)
        val ids = invalidatedId.joinToString(",")

        val invalidedCount = client
            .httpDelete("/users/invalidate?ids=$ids")
            .expectStatus().is2xxSuccessful
            .returnResult<Long>().responseBody
            .awaitSingle()

        invalidedCount shouldBeEqualTo invalidatedId.size.toLong()
    }
}
