package exposed.r2dbc.examples.controller

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.dto.ActorDTO
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.spring.tests.httpDelete
import io.bluetape4k.spring.tests.httpGet
import io.bluetape4k.spring.tests.httpPost
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

class ActorControllerTest(
    @param:Autowired private val client: WebTestClient,
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
    fun `find all actors`() = runSuspendIO {
        val actors = client
            .httpGet("/actors")
            .expectStatus().is2xxSuccessful
            .returnResult<ActorDTO>().responseBody
            .asFlow()
            .toFastList()

        actors.forEach {
            log.debug { "Actor: $it" }
        }
        actors.shouldNotBeEmpty()
    }

    @Test
    fun `find actor by id`() = runSuspendIO {
        val actorId = 1L

        val actor = client
            .httpGet("/actors/$actorId")
            .expectStatus().is2xxSuccessful
            .returnResult<ActorDTO>().responseBody
            .awaitSingle()

        log.debug { "Actor: $actor" }
        actor.id shouldBeEqualTo actorId
    }

    @Test
    fun `find actors by firstName`() = runSuspendIO {
        val firstName = "Angelina"

        val angelinas = client
            .httpGet("/actors/search?firstName=$firstName")
            .expectStatus().is2xxSuccessful
            .returnResult<ActorDTO>().responseBody
            .asFlow()
            .toFastList()

        angelinas.forEach {
            log.debug { "Angelians: $it" }
        }
        angelinas shouldHaveSize 2
        angelinas.all { it.firstName == firstName }.shouldBeTrue()
    }

    @Test
    fun `save new actor`() = runSuspendIO {
        val actor = newActorDTO()

        val newActor = client
            .httpPost("/actors", actor)
            .expectStatus().is2xxSuccessful
            .returnResult<ActorDTO>().responseBody
            .awaitSingle()

        log.debug { "newActor=$newActor" }

        newActor.shouldNotBeNull()
        newActor shouldBeEqualTo actor.copy(id = newActor.id)
    }

    @Test
    fun `delete actor`() = runSuspendIO {
        val actor = newActorDTO()

        val newActor = client
            .httpPost("/actors", actor)
            .expectStatus().is2xxSuccessful
            .returnResult<ActorDTO>().responseBody
            .awaitSingle()

        log.debug { "newActor=$newActor" }
        newActor.shouldNotBeNull()

        val deletedCount = client
            .httpDelete("/actors/${newActor.id}")
            .expectStatus().is2xxSuccessful
            .returnResult<Int>().responseBody
            .awaitSingle()

        log.debug { "deletedCount=$deletedCount" }
        deletedCount shouldBeEqualTo 1
    }
}
