package exposed.r2dbc.multitenant.webflux.config

import exposed.r2dbc.multitenant.webflux.AbstractMultitenantTest
import exposed.r2dbc.multitenant.webflux.domain.repository.ActorR2dbcRepository
import exposed.r2dbc.multitenant.webflux.domain.repository.MovieR2dbcRepository
import exposed.r2dbc.multitenant.webflux.tenant.TenantInitializer
import exposed.r2dbc.multitenant.webflux.tenant.Tenants
import exposed.r2dbc.multitenant.webflux.tenant.suspendTransactionWithTenant
import exposed.r2dbc.shared.repository.MovieSchema
import exposed.r2dbc.shared.repository.toActorDTO
import io.bluetape4k.coroutines.flow.extensions.toFastList
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.uninitialized
import kotlinx.coroutines.flow.map
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired


class ExposedR2dbcConfigTest: AbstractMultitenantTest() {

    companion object: KLoggingChannel()

    @Autowired
    private val actorRepository: ActorR2dbcRepository = uninitialized()

    @Autowired
    private val movieRepository: MovieR2dbcRepository = uninitialized()

    @Autowired
    private val tenantInitializer: TenantInitializer = uninitialized()

    @Test
    fun `context loading`() {
        actorRepository.shouldNotBeNull()
        movieRepository.shouldNotBeNull()
        tenantInitializer.shouldNotBeNull()
    }

    @ParameterizedTest
    @EnumSource(Tenants.Tenant::class)
    fun `load all actors by tenant`(tenant: Tenants.Tenant) = runSuspendIO {
        suspendTransactionWithTenant(tenant) {
            val actors = MovieSchema.ActorTable.selectAll().map { it.toActorDTO() }.toFastList()
            actors.shouldNotBeEmpty()

            actors.forEach { actor ->
                log.debug { "tenant:${tenant.id}, Actor: $actor" }
            }
        }
    }
}
