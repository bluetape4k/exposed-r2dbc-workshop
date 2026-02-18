package exposed.r2dbc.workshop.springwebflux.domain

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.workshop.springwebflux.domain.model.toActorRecord
import io.bluetape4k.junit5.coroutines.SuspendedJobTester
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
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
                    .map { it.toActorRecord() }
                    .toList()
            }

            actors.shouldNotBeEmpty()
        }

        @Test
        fun `get all actors in coroutines`() = runSuspendIO {
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            SuspendedJobTester()
                .workers(availableProcessors)
                .rounds(availableProcessors * 4)
                .add {
                    withContext(Dispatchers.IO) {
                        suspendTransaction(db = database) {
                            val actors = ActorTable.selectAll()
                                .map { it.toActorRecord() }
                                .toList()
                            actors.shouldNotBeEmpty()
                        }
                    }
                }
                .run()
        }
    }
}
