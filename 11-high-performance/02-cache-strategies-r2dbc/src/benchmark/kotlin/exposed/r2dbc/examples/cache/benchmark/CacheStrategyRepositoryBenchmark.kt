package exposed.r2dbc.examples.cache.benchmark

import exposed.r2dbc.examples.cache.CacheStrategyApplication
import exposed.r2dbc.examples.cache.domain.model.UserCredentialsTable
import exposed.r2dbc.examples.cache.domain.model.UserTable
import exposed.r2dbc.examples.cache.domain.model.newUserCredentialsRecord
import exposed.r2dbc.examples.cache.domain.model.newUserRecord
import exposed.r2dbc.examples.cache.domain.repository.UserCacheRepository
import exposed.r2dbc.examples.cache.domain.repository.UserCredentialsCacheRepository
import io.bluetape4k.exposed.core.statements.api.toExposedBlob
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal object BenchmarkApplicationContextHolder {
    @Volatile
    private var cachedContext: ConfigurableApplicationContext? = null

    fun getOrCreate(): ConfigurableApplicationContext {
        return cachedContext?.takeIf { it.isActive } ?: synchronized(this) {
            cachedContext?.takeIf { it.isActive } ?: SpringApplicationBuilder(CacheStrategyApplication::class.java)
                .profiles("h2")
                .web(WebApplicationType.NONE)
                .properties(
                    "spring.main.web-application-type=none",
                    "spring.main.lazy-initialization=true"
                )
                .run()
                .also { cachedContext = it }
        }
    }

    fun close() {
        synchronized(this) {
            cachedContext?.close()
            cachedContext = null
        }
    }
}

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
internal open class CacheStrategyRepositoryBenchmark {
    private lateinit var userRepository: UserCacheRepository
    private lateinit var userCredentialsRepository: UserCredentialsCacheRepository
    private lateinit var r2dbcDatabase: R2dbcDatabase

    private var userIds: List<Long> = emptyList()
    private var userCredentialIds: List<String> = emptyList()
    private val missIndex = AtomicInteger()
    private val credentialIndex = AtomicInteger()

    @Setup
    fun setup() {
        val context = BenchmarkApplicationContextHolder.getOrCreate()
        userRepository = context.getBean(UserCacheRepository::class.java)
        userCredentialsRepository = context.getBean(UserCredentialsCacheRepository::class.java)
        r2dbcDatabase = context.getBean(R2dbcDatabase::class.java)

        runBlocking(Dispatchers.IO) {
            userRepository.invalidateAll()
            userCredentialsRepository.invalidateAll()

            suspendTransaction(db = r2dbcDatabase) {
                UserCredentialsTable.deleteAll()
                UserTable.deleteAll()

                userIds = insertUsers(128)
                userCredentialIds = insertUserCredentials(128)
            }

            val warmUserIds = userIds.take(32)
            val warmCredentialIds = userCredentialIds.take(32)
            userRepository.getAll(warmUserIds)
            userCredentialsRepository.getAll(warmCredentialIds)
        }
    }

    @TearDown
    fun tearDown() {
        BenchmarkApplicationContextHolder.close()
    }

    @Benchmark
    fun userCacheHitReadThrough(): Long? = runBlocking(Dispatchers.IO) {
        val userId = userIds[(missIndex.getAndIncrement() and Int.MAX_VALUE) % 32]
        userRepository.get(userId)?.id
    }

    @Benchmark
    fun userCacheMissReadThrough(): Long? = runBlocking(Dispatchers.IO) {
        val userId = userIds[(missIndex.getAndIncrement() and Int.MAX_VALUE) % userIds.size]
        userRepository.invalidate(userId)
        userRepository.get(userId)?.id
    }

    @Benchmark
    fun userCredentialsCacheHitReadOnly(): String? = runBlocking(Dispatchers.IO) {
        val id = userCredentialIds[(credentialIndex.getAndIncrement() and Int.MAX_VALUE) % 32]
        userCredentialsRepository.get(id)?.id
    }

    private suspend fun insertUsers(size: Int): List<Long> {
        val users = List(size) { newUserRecord() }
        return UserTable.batchInsert(users) {
            this[UserTable.username] = it.username
            this[UserTable.firstName] = it.firstName
            this[UserTable.lastName] = it.lastName
            this[UserTable.address] = it.address
            this[UserTable.zipcode] = it.zipcode
            this[UserTable.birthDate] = it.birthDate
            this[UserTable.avatar] = it.avatar?.toExposedBlob()
        }.map { it[UserTable.id].value }
    }

    private suspend fun insertUserCredentials(size: Int): List<String> =
        List(size) {
            val record = newUserCredentialsRecord()
            UserCredentialsTable.insertAndGetId {
                it[UserCredentialsTable.id] = record.id
                it[UserCredentialsTable.username] = record.username
                it[UserCredentialsTable.email] = record.email
                it[UserCredentialsTable.lastLoginAt] = record.lastLoginAt
            }.value
        }
}
