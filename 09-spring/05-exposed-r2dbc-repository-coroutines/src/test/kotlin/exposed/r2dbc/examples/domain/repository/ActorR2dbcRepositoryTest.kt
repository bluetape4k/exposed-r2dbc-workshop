package exposed.r2dbc.examples.domain.repository

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.domain.model.ActorRecord
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ActorR2dbcRepositoryTest(
    @param:Autowired private val actorRepository: ActorR2dbcRepository,
): AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel() {
        private fun newActorRecord() = ActorRecord(
            id = 0L,
            firstName = faker.name().firstName(),
            lastName = faker.name().lastName(),
            birthday = faker.timeAndDate().birthday(20, 80).toString()
        )
    }

    @Test
    fun `find actor by id`() = runSuspendIO {
        val actorId = 1L

        val actor = suspendTransaction {
            actorRepository.findById(actorId)
        }

        log.debug { "Actor: $actor" }
        actor.shouldNotBeNull()
        actor.id shouldBeEqualTo actorId
    }

    @Test
    fun `search actors by lastName`() = runSuspendIO {

        val params = mapOf("lastName" to "Depp")
        val actors = suspendTransaction {
            actorRepository.searchActors(params).toList()
        }
        actors.forEach {
            log.debug { "actor: $it" }
        }
        actors.shouldNotBeEmpty()
    }

    @Test
    fun `search actors by firstName`() = runSuspendIO {

        val params = mapOf("firstName" to "Angelina")
        val actors = suspendTransaction {
            actorRepository.searchActors(params).toList()
        }

        actors.forEach {
            log.debug { "actor: $it" }
        }
        actors.shouldNotBeEmpty()
    }

    @Test
    fun `save new actor`() = runSuspendIO {
        suspendTransaction {
            val prevCount = suspendTransaction { actorRepository.count() }

            val actor = newActorRecord()
            val savedActor = actorRepository.save(actor)
            savedActor shouldBeEqualTo actor.copy(id = savedActor.id)

            log.debug { "Saved Actor: $savedActor" }

            actorRepository.count() shouldBeEqualTo prevCount + 1
        }
    }

    @Test
    fun `delete actor by id`() = runSuspendIO {
        val actor = newActorRecord()
        val savedActor = suspendTransaction {
            actorRepository.save(actor)
        }
        savedActor shouldBeEqualTo actor.withId(savedActor.id)

        log.debug { "Saved Actor: $savedActor" }

        val deletedCount = suspendTransaction {
            actorRepository.deleteById(savedActor.id)
        }
        deletedCount shouldBeEqualTo 1
    }
}
