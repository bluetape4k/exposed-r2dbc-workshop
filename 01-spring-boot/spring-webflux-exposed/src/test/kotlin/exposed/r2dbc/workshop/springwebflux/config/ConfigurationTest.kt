package exposed.r2dbc.workshop.springwebflux.config

import exposed.r2dbc.workshop.springwebflux.AbstractSpringWebfluxTest
import exposed.r2dbc.workshop.springwebflux.domain.repository.ActorRepository
import exposed.r2dbc.workshop.springwebflux.domain.repository.MovieRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ConfigurationTest: AbstractSpringWebfluxTest() {

    companion object: KLoggingChannel()

    @Autowired
    private val actorRepository: ActorRepository = uninitialized()

    @Autowired
    private val movieRepository: MovieRepository = uninitialized()

    @Test
    fun `context loading`() {
        actorRepository.shouldNotBeNull()
        movieRepository.shouldNotBeNull()
    }
}
