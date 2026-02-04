package exposed.r2dbc.examples.suspendedcache.controller

import exposed.r2dbc.examples.suspendedcache.domain.model.CountryRecord
import exposed.r2dbc.examples.suspendedcache.domain.repository.CountryR2dbcRepository
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/default/countries")
class DefaultCountryController(
    @Qualifier("countryR2dbcRepository")
    private val countryRepository: CountryR2dbcRepository,
) {
    companion object: KLoggingChannel()

    @GetMapping("/{code}")
    suspend fun getCountryByCode(@PathVariable code: String): CountryRecord? =
        countryRepository.findByCode(code)
}
