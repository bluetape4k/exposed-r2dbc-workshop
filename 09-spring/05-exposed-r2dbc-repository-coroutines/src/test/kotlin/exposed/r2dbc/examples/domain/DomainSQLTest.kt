package exposed.r2dbc.examples.domain

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.examples.domain.model.toActorDTO
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransactionAsync
import org.junit.jupiter.api.Test

class DomainSQLTest: AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 5
    }

    @Test
    fun `get all actors`() = runSuspendIO {
        suspendTransaction(Dispatchers.IO, readOnly = true) {
            val actors = ActorTable.selectAll().map { it.toActorDTO() }.toList()

            actors.forEach { actor ->
                log.debug { "Actor: $actor" }
            }
            actors.shouldNotBeEmpty()
        }
    }

    @Test
    fun `get all actors in multiple platform threads`() = runSuspendIO {
        suspendTransaction(Dispatchers.IO, readOnly = true) {
            SuspendedJobTester()
                .numThreads(Runtime.getRuntime().availableProcessors() * 2)
                .roundsPerJob(Runtime.getRuntime().availableProcessors() * 2 * 4)
                .add {
                    suspendTransactionAsync(Dispatchers.IO, readOnly = true) {
                        val actors = ActorTable.selectAll().map { it.toActorDTO() }.toList()
                        actors.shouldNotBeEmpty()
                    }
                }
                .run()
        }
    }
}
