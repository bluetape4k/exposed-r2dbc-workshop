package exposed.r2dbc.examples.suspendedcache.controller

import exposed.r2dbc.examples.suspendedcache.domain.CountryDTO
import exposed.r2dbc.examples.suspendedcache.domain.repository.CountryR2dbcRepository
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

    @GetMapping("/{code}")
    suspend fun getCountryByCode(@PathVariable code: String): CountryDTO? =
        countryRepository.findByCode(code)
}
