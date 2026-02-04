package exposed.r2dbc.workshop.springwebflux.domain.repository

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.model.ActorRecord
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ActorRepositoryTest(
    @param:Autowired private val actorRepository: ActorRepository,
): AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel() {
        fun newActorRecord() = ActorRecord(
            firstName = faker.name().firstName(),
            lastName = faker.name().lastName(),
            birthday = faker.timeAndDate().birthday(20, 80).toString()
        )
    }

    @Test
    fun `find actor by id`() = runTest {
        suspendTransaction {
            val actorId = 1L

            val actor = actorRepository.findById(actorId)

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
    fun `search actors by lastName`() = runTest {
        suspendTransaction {
            val params = mapOf("lastName" to "Depp")
            val actors = actorRepository.searchActor(params).toFastList()

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
    fun `search actors by firstName`() = runTest {
        suspendTransaction {
            val params = mapOf("firstName" to "Angelina")
            val actors = actorRepository.searchActor(params).toFastList()

            actors.shouldNotBeEmpty()
            actors.forEach {
                log.debug { "actor: $it" }
            }
        }
    }

    @Test
    fun `create new actor`() = runTest {
        suspendTransaction {
            val prevCount = actorRepository.count()

            val actor = newActorRecord()
            val savedActor = actorRepository.create(actor)
            savedActor shouldBeEqualTo actor.copy(id = savedActor.id)

            val newActor = actorRepository.findById(savedActor.id!!)
            newActor shouldBeEqualTo savedActor

            actorRepository.count() shouldBeEqualTo prevCount + 1L
        }
    }

    @Test
    fun `delete actor by id`() = runTest {
        suspendTransaction {
            val actor = newActorRecord()
            val savedActor = actorRepository.create(actor)
            log.debug { "Saved actor: $savedActor" }
            savedActor.shouldNotBeNull()
            savedActor.id.shouldNotBeNull()

            val deletedCount = actorRepository.deleteById(savedActor.id)
            deletedCount shouldBeEqualTo 1
        }
    }
}
