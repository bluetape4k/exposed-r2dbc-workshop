package exposed.r2dbc.examples.suspendedcache.domain.repository

import io.bluetape4k.support.uninitialized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class DefaultCountryR2dbcRepositoryTest: AbstractCountryR2dbcRepositoryTest() {

    @Autowired
    @Qualifier("defaultCountryR2dbcRepository")
    override val countryRepository: CountryR2dbcRepository = uninitialized()

}
