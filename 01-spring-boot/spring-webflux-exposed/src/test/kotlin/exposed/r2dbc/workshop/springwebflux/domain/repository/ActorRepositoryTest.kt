package exposed.r2dbc.workshop.springwebflux.domain.repository

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.ActorDTO
import exposed.r2dbc.workshop.springwebflux.domain.toActorDTO
import io.bluetape4k.junit5.coroutines.runSuspendTest
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ActorRepositoryTest(
    @Autowired private val actorRepository: ActorRepository,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel() {
        fun newActorDTO() = ActorDTO(
            firstName = faker.name().firstName(),
            lastName = faker.name().lastName(),
            birthday = faker.timeAndDate().birthday(20, 80).toString()
        )
    }

    @Test
    fun `find actor by id`() = runSuspendTest {
        suspendTransaction(readOnly = true) {
            val actorId = 1L

            val actor = actorRepository.findById(actorId)?.toActorDTO()

            log.debug { "Actor: $actor" }
            actor.shouldNotBeNull()
            actor.id shouldBeEqualTo actorId
        }
    }

    /**
     * ```sql
     * SELECT ACTORS.ID, ACTORS.FIRST_NAME, ACTORS.LAST_NAME, ACTORS.BIRTHDAY
     *   FROM ACTORS
     *  WHERE ACTORS.LAST_NAME = 'Depp'
     * ```
     */
    @Test
    fun `search actors by lastName`() = runSuspendTest {
        suspendTransaction(readOnly = true) {
            val params = mapOf("lastName" to "Depp")
            val actors = actorRepository.searchActor(params).toList()

            actors.shouldNotBeEmpty()
            actors.forEach {
                log.debug { "actor: $it" }
            }
        }
    }

    /**
     * ```sql
     * SELECT ACTORS.ID, ACTORS.FIRST_NAME, ACTORS.LAST_NAME, ACTORS.BIRTHDAY
     *   FROM ACTORS
     *  WHERE ACTORS.FIRST_NAME = 'Angelina'
     * ```
     */
    @Test
    fun `search actors by firstName`() = runSuspendTest {
        suspendTransaction(readOnly = true) {
            val params = mapOf("firstName" to "Angelina")
            val actors = actorRepository.searchActor(params).toList()

            actors.shouldNotBeEmpty()
            actors.forEach {
                log.debug { "actor: $it" }
            }
        }
    }

    @Test
    fun `create new actor`() = runSuspendTest {
        suspendTransaction {
            val prevCount = actorRepository.count()

            val actor = newActorDTO()

            val savedActor = actorRepository.create(actor).toActorDTO()
            savedActor shouldBeEqualTo actor.copy(id = savedActor.id)

            val newActor = actorRepository.findById(savedActor.id!!)?.toActorDTO()
            newActor shouldBeEqualTo savedActor

            actorRepository.count() shouldBeEqualTo prevCount + 1L
        }
    }

    @Test
    fun `delete actor by id`() = runSuspendTest {
        suspendTransaction {
            val actor = newActorDTO()
            val savedActor = actorRepository.create(actor).toActorDTO()
            savedActor.shouldNotBeNull()
            savedActor.id.shouldNotBeNull()

            val deletedCount = actorRepository.deleteById(savedActor.id)
            deletedCount shouldBeEqualTo 1
        }
    }
}
