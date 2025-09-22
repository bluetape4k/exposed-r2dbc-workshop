package exposed.r2dbc.examples.cache.domain.repository

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class UserCredentialsCacheRepositoryTest(
    @param:Autowired private val repository: UserCredentialsCacheRepository,
): AbstractCacheStrategyTest() {

    companion object: KLoggingChannel()

    private val idsInDB = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun setup() {
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
        return UserCredentialsTable.insertAndGetId {
            it[UserCredentialsTable.username] = faker.internet().username()
            it[UserCredentialsTable.email] = faker.internet().emailAddress()
            it[UserCredentialsTable.lastLoginAt] = Instant.now()
        }.value
    }

    @Test
    fun `Read Through 로 기존 DB정보를 캐시에서 읽어오기`() = runSuspendIO {
        idsInDB.forEach { ucId ->
            val userCredentialsFromCache = repository.get(ucId)
            log.debug { "Loaded user credentials from cache. id=$ucId, $userCredentialsFromCache" }
            userCredentialsFromCache.shouldNotBeNull()
            userCredentialsFromCache.id shouldBeEqualTo ucId

            suspendTransaction {
                userCredentialsFromCache.username shouldBeEqualTo UserCredentialsTable.selectAll()
                    .where { UserCredentialsTable.id eq ucId }
                    .single()[UserCredentialsTable.username]
            }
        }
    }

    @Test
    fun `Read Through 로 검색해서 가져오기`() = runSuspendIO {
        val userCredentialsFromCache = repository
            .findAll {
                UserCredentialsTable.id inList idsInDB
            }
            .toList()
        userCredentialsFromCache shouldHaveSize idsInDB.size
    }

    @Test
    fun `Read Through 로 모든 ID 가져오기`() = runSuspendIO {
        val userCredentialsFromCache = repository.getAll(idsInDB, 1)
        userCredentialsFromCache.shouldNotBeEmpty()
        userCredentialsFromCache shouldHaveSize idsInDB.size
    }
}
