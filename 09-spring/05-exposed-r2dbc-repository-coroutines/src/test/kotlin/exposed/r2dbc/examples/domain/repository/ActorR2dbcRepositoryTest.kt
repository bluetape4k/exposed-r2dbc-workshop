package exposed.r2dbc.examples.domain.repository

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.dto.ActorDTO
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ActorR2dbcRepositoryTest(
    @Autowired private val actorRepository: ActorR2dbcRepository,
): AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel() {
        private fun newActorDTO() = ActorDTO(
            id = 0L,
            firstName = faker.name().firstName(),
            lastName = faker.name().lastName(),
            birthday = faker.timeAndDate().birthday(20, 80).toString()
        )
    }

    @Test
    fun `find actor by id`() = runSuspendIO {
        suspendTransaction(context = Dispatchers.IO, readOnly = true) {
            val actorId = 1L

            val actor = actorRepository.findById(actorId)

            log.debug { "Actor: $actor" }
            actor.shouldNotBeNull()
            actor.id shouldBeEqualTo actorId
        }
    }

    @Test
    fun `search actors by lastName`() = runSuspendIO {
        suspendTransaction(context = Dispatchers.IO, readOnly = true) {
            val params = mapOf("lastName" to "Depp")
            val actors = actorRepository.searchActors(params).toList()

            actors.forEach {
                log.debug { "actor: $it" }
            }
            actors.shouldNotBeEmpty()
        }
    }

    @Test
    fun `search actors by firstName`() = runSuspendIO {
        suspendTransaction(context = Dispatchers.IO, readOnly = true) {
            val params = mapOf("firstName" to "Angelina")
            val actors = actorRepository.searchActors(params).toList()

            actors.forEach {
                log.debug { "actor: $it" }
            }
            actors.shouldNotBeEmpty()
        }
    }

    @Test
    fun `save new actor`() = runSuspendIO {
        suspendTransaction(Dispatchers.IO) {
            val prevCount = actorRepository.count()

            val actor = newActorDTO()
            val savedActor = actorRepository.save(actor)
            savedActor shouldBeEqualTo actor.copy(id = savedActor.id)

            log.debug { "Saved Actor: $savedActor" }

            actorRepository.count() shouldBeEqualTo prevCount + 1
        }
    }

    @Test
    fun `delete actor by id`() = runSuspendIO {
        suspendTransaction(Dispatchers.IO) {
            val actor = newActorDTO()
            val savedActor = actorRepository.save(actor)
            savedActor shouldBeEqualTo actor.copy(id = savedActor.id)

            log.debug { "Saved Actor: $savedActor" }

            val deletedCount = actorRepository.deleteById(savedActor.id)
            deletedCount shouldBeEqualTo 1
        }
    }
}
