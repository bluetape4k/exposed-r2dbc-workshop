package exposed.r2dbc.multitenant.onboarding.tenant

import exposed.r2dbc.multitenant.onboarding.domain.model.ActorRecord
import exposed.r2dbc.multitenant.onboarding.domain.model.MovieSchema.ActorInMovieTable
import exposed.r2dbc.multitenant.onboarding.domain.model.MovieSchema.ActorTable
import exposed.r2dbc.multitenant.onboarding.domain.model.MovieSchema.MovieTable
import exposed.r2dbc.multitenant.onboarding.domain.model.MovieWithActorRecord
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.logging.warn
import io.bluetape4k.r2dbc.pool.connectionFactoryOptionsOf
import io.bluetape4k.r2dbc.pool.connectionPoolOf
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Orchestrates tenant metadata reservation, resource provisioning, and runtime registration.
 */
@Service
class TenantProvisioner(
    private val properties: TenantOnboardingProperties,
    private val registryRepository: TenantRegistryRepository,
    private val runtimeRegistry: TenantConnectionFactoryRegistry,
    private val meterRegistry: MeterRegistry,
    private val failureSimulator: ProvisioningFailureSimulator,
) {

    companion object: KLoggingChannel() {
        private const val MAX_DISPLAY_NAME_LENGTH = 128
    }

    init {
        Gauge
            .builder("tenant.onboarding.active", runtimeRegistry) { it.activeTenantCount().toDouble() }
            .description("Active runtime tenant connection pools")
            .register(meterRegistry)
    }

    suspend fun onboard(request: TenantOnboardingRequest): TenantMetadata =
        withTimeout(properties.overallTimeout.toMillis()) {
            val tenantId = TenantId.parse(request.tenantId)
            val displayName = request.displayName.trim()
            if (displayName.isBlank()) {
                throw InvalidTenantRequestException("displayName must not be blank")
            }
            if (displayName.length > MAX_DISPLAY_NAME_LENGTH) {
                throw InvalidTenantRequestException("displayName must be $MAX_DISPLAY_NAME_LENGTH characters or less")
            }

            runtimeRegistry.onboardingMutex.withLock {
                if (registryRepository.countReservedTenants() >= properties.maxTenants) {
                    throw TenantLimitExceededException(properties.maxTenants)
                }

                val databaseName = tenantDatabaseName(tenantId)
                val r2dbcUrl = tenantUrl(databaseName)
                val reserved = withTimeout(properties.reservationTimeout.toMillis()) {
                    registryRepository.reserve(tenantId, displayName, databaseName, r2dbcUrl)
                }

                var pool: ConnectionPool? = null
                var database: R2dbcDatabase? = null
                try {
                    failureSimulator.maybeFail(ProvisioningFailurePoint.AFTER_RESERVE)
                    log.info { "Tenant onboarding step=pool tenantId=$tenantId" }
                    pool = poolConfiguration(r2dbcUrl)
                    withTimeout(properties.pool.maxCreateConnectionTime.toMillis()) {
                        pool.warmup().awaitSingle()
                    }
                    failureSimulator.maybeFail(ProvisioningFailurePoint.AFTER_POOL)

                    database = R2dbcDatabase.connect(
                        pool,
                        databaseConfig(r2dbcUrl),
                    )

                    withTimeout(properties.schemaTimeout.toMillis()) {
                        createSchemaAndSeed(database, tenantId)
                    }
                    failureSimulator.maybeFail(ProvisioningFailurePoint.AFTER_SCHEMA)
                    failureSimulator.maybeFail(ProvisioningFailurePoint.AFTER_SEED)

                    runtimeRegistry.register(tenantId, pool, database)
                    failureSimulator.maybeFail(ProvisioningFailurePoint.AFTER_REGISTER)

                    val active = registryRepository.markActive(tenantId)
                    return@withTimeout active
                } catch (e: CancellationException) {
                    cleanupAfterFailure(tenantId, database, pool)
                    throw e
                } catch (e: Exception) {
                    meterRegistry.counter("tenant.onboarding.failures").increment()
                    cleanupAfterFailure(tenantId, database, pool)
                    throw TenantProvisioningException("Failed to provision tenant '${reserved.tenantId.value}'", e)
                }
            }
        }

    private suspend fun cleanupAfterFailure(
        tenantId: TenantId,
        database: R2dbcDatabase?,
        pool: ConnectionPool?,
    ) {
        withContext(NonCancellable) {
            val registeredPool = runtimeRegistry.unregister(tenantId)
            if (database != null) {
                try {
                    suspendTransaction(db = database) {
                        SchemaUtils.drop(ActorInMovieTable, MovieTable, ActorTable)
                    }
                } catch (e: Exception) {
                    log.warn(e) { "Failed to drop tenant schema during cleanup. tenantId=$tenantId" }
                }
            }
            closePoolAfterFailure(tenantId, registeredPool ?: pool)
            try {
                registryRepository.delete(tenantId)
            } catch (e: Exception) {
                log.warn(e) { "Failed to delete tenant metadata during cleanup. tenantId=$tenantId" }
            }
        }
    }

    private suspend fun closePoolAfterFailure(
        tenantId: TenantId,
        pool: ConnectionPool?,
    ) {
        try {
            pool?.disposeLater()?.awaitSingleOrNull()
        } catch (e: Exception) {
            log.warn(e) { "Failed to close tenant pool during cleanup. tenantId=$tenantId" }
        }
    }

    private suspend fun createSchemaAndSeed(
        database: R2dbcDatabase,
        tenantId: TenantId,
    ) {
        suspendTransaction(db = database) {
            SchemaUtils.create(ActorTable, MovieTable, ActorInMovieTable)

            val firstActor = ActorRecord(
                firstName = "Ada",
                lastName = tenantId.value.replaceFirstChar { it.titlecase(Locale.ROOT) },
                birthday = "1980-01-01",
            )
            val secondActor = ActorRecord(
                firstName = "Grace",
                lastName = tenantId.value.replaceFirstChar { it.titlecase(Locale.ROOT) },
                birthday = "1985-01-01",
            )
            val movie = MovieWithActorRecord(
                name = "${tenantId.value} premiere",
                producerName = firstActor.firstName,
                releaseDate = "2026-01-01",
                actors = mutableListOf(firstActor, secondActor),
            )

            ActorTable.batchInsert(movie.actors) {
                this[ActorTable.firstName] = it.firstName
                this[ActorTable.lastName] = it.lastName
                this[ActorTable.birthday] = it.birthday?.let(LocalDate::parse)
            }
            MovieTable.batchInsert(listOf(movie)) {
                this[MovieTable.name] = it.name
                this[MovieTable.producerName] = it.producerName
                this[MovieTable.releaseDate] = LocalDate.parse(it.releaseDate)
            }

            val movieId = MovieTable
                .select(MovieTable.id)
                .where { MovieTable.name eq movie.name }
                .first()[MovieTable.id]
            val actorIds = movie.actors.map { actor ->
                ActorTable
                    .select(ActorTable.id)
                    .where { ActorTable.firstName eq actor.firstName }
                    .andWhere { ActorTable.lastName eq actor.lastName }
                    .first()[ActorTable.id]
            }
            ActorInMovieTable.batchInsert(actorIds.map { movieId to it }) {
                this[ActorInMovieTable.movieId] = it.first.value
                this[ActorInMovieTable.actorId] = it.second.value
            }
        }
    }

    private fun tenantDatabaseName(tenantId: TenantId): String =
        "tenant_onboarding_${tenantId.value.replace("-", "_")}_" +
            UUID.randomUUID().toString().replace("-", "").lowercase()

    private fun tenantUrl(databaseName: String): String =
        "${properties.tenantUrlPrefix}$databaseName;${properties.tenantUrlOptions}"

    private fun poolConfiguration(url: String): ConnectionPool =
        connectionPoolOf(connectionFactoryOptionsOf(url)) {
            initialSize = properties.pool.initialSize
            maxSize = properties.pool.maxSize
            minIdle = properties.pool.minIdle
            maxIdleTime = properties.pool.maxIdleTime
            maxLifeTime = properties.pool.maxLifeTime
            maxCreateConnectionTime = properties.pool.maxCreateConnectionTime
            maxAcquireTime = properties.pool.maxAcquireTime
            acquireRetry = properties.pool.acquireRetry
            backgroundEvictionInterval = properties.pool.backgroundEvictionInterval
        }

    private fun databaseConfig(url: String): R2dbcDatabaseConfig.Builder =
        R2dbcDatabaseConfig {
            explicitDialect = H2Dialect()
            connectionFactoryOptions = connectionFactoryOptionsOf(url)
        }
}

enum class ProvisioningFailurePoint {
    AFTER_RESERVE,
    AFTER_POOL,
    AFTER_SCHEMA,
    AFTER_SEED,
    AFTER_REGISTER,
}

fun interface ProvisioningFailureSimulator {
    fun maybeFail(point: ProvisioningFailurePoint)
}
