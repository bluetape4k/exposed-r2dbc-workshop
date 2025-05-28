package exposed.r2dbc.examples.cache.config

import exposed.r2dbc.examples.cache.AbstractCacheStrategyTest
import exposed.r2dbc.examples.cache.domain.repository.UserCacheRepository
import exposed.r2dbc.examples.cache.domain.repository.UserCredentialsCacheRepository
import exposed.r2dbc.examples.cache.domain.repository.UserEventCacheRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.uninitialized
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ExposedR2dbcConfigTest: AbstractCacheStrategyTest() {

    companion object: KLoggingChannel()

    @Autowired
    private val userRepository: UserCacheRepository = uninitialized()

    @Autowired
    private val userCredentialsRepository: UserCredentialsCacheRepository = uninitialized()

    @Autowired
    private val userEventRepository: UserEventCacheRepository = uninitialized()

    @Test
    fun `context loading`() {
        userRepository.shouldNotBeNull()
        userCredentialsRepository.shouldNotBeNull()
        userEventRepository.shouldNotBeNull()
    }

}
