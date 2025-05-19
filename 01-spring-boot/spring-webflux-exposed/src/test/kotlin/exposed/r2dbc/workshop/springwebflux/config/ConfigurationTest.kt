package exposed.r2dbc.workshop.springwebflux.config

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.repository.ActorRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.uninitialized
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ConfigurationTest: AbstractSpringWebfluxTest() {

    companion object: KLogging()

    @Autowired
    private val actorRepository: ActorRepository = uninitialized()

    @Test
    fun `context loading`() {
        actorRepository.shouldNotBeNull()
    }
}
