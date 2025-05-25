package exposed.r2dbc.examples.config

import exposed.r2dbc.examples.AbstractExposedR2dbcRepositoryTest
import exposed.r2dbc.examples.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.examples.domain.repository.MovieR2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ConfigurationTest: AbstractExposedR2dbcRepositoryTest() {

    companion object: KLoggingChannel()

    @Autowired
    private val actorRepository: ActorR2dbcRepository = uninitialized()

    @Autowired
    private val movieRepository: MovieR2dbcRepository = uninitialized()

    @Test
    fun `context loading`() {
        actorRepository.shouldNotBeNull()
        movieRepository.shouldNotBeNull()
    }
}
