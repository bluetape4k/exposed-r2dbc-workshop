package exposed.r2dbc.workshop.springwebflux.domain

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.MovieSchema.ActorTable
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class DomainSQLTest: AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel() {
        private const val REPEAT_SIZE = 5
    }

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
            SuspendedJobTester()
                .numThreads(Runtime.getRuntime().availableProcessors() * 2)
                .roundsPerJob(Runtime.getRuntime().availableProcessors() * 2 * 4)
                .add {
                    val actors = suspendTransaction {
                        ActorTable.selectAll().map { it.toActorDTO() }.toList()
                    }
                    actors.shouldNotBeEmpty()
                }
                .run()
        }
    }
}
