package exposed.r2dbc.examples.suspendedcache.controller

import io.bluetape4k.logging.coroutines.KLoggingChannel

class CachedCountryControllerTest: AbstractCountryControllerTest() {

    companion object: KLoggingChannel()

    override val basePath: String = "cached"

}
