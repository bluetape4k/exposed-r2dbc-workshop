package exposed.r2dbc.workshop.springwebflux.domain

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.MovieSchema.ActorTable
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.inTopLevelSuspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class DomainSQLTest: AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 10
    }

    @Autowired
    private val database: R2dbcDatabase = uninitialized()

    @Nested
    open inner class Coroutines {

        @RepeatedTest(REPEAT_SIZE)
        open fun `get all actors`() = runSuspendIO {
            val actors = suspendTransaction {
                ActorTable.selectAll()
                    .map { it.toActorDTO() }
                    .toList()
            }

            actors.shouldNotBeEmpty()
        }

        @Test
        fun `get all actors in multiple platform threads`() = runSuspendIO {
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            SuspendedJobTester()
                .numThreads(availableProcessors)
                .roundsPerJob(availableProcessors * 4)
                .add {
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch {
                        inTopLevelSuspendTransaction(
                            transactionIsolation = database.transactionManager.defaultIsolationLevel!!,
                            readOnly = true,
                            db = database
                        ) {
                            val actors = ActorTable.selectAll().map { it.toActorDTO() }.toList()
                            actors.shouldNotBeEmpty()
                        }
                    }.join()
                }
                .run()
        }
    }
}
