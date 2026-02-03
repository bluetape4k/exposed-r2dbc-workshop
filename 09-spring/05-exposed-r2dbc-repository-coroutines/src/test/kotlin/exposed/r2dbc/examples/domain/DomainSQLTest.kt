package exposed.r2dbc.examples.domain

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.toActorDTO
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DomainSQLTest: AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 5
    }

    @Autowired
    private val database: R2dbcDatabase = uninitialized()

    @Test
    fun `get all actors`() = runSuspendIO {
        suspendTransaction {
            val actors = ActorTable.selectAll().map { it.toActorDTO() }.toFastList()

            actors.forEach { actor ->
                log.debug { "Actor: $actor" }
            }
            actors.shouldNotBeEmpty()
        }
    }

    @Test
    fun `get all actors in coroutines`() = runSuspendIO {
        SuspendedJobTester()
            .numThreads(Runtime.getRuntime().availableProcessors())
            .roundsPerJob(Runtime.getRuntime().availableProcessors() * 4)
            .add {
                withContext(Dispatchers.IO) {
                    inTopLevelSuspendTransaction(
                        transactionIsolation = database.transactionManager.defaultIsolationLevel!!,
                        db = database
                    ) {
                        val actors = ActorTable.selectAll().map { it.toActorDTO() }.toFastList()
                        actors.shouldNotBeEmpty()
                    }
                }
            }
            .run()
    }
}
