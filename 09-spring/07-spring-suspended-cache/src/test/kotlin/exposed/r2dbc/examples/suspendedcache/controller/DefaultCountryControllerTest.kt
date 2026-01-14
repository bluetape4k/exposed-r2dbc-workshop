package exposed.r2dbc.examples.suspendedcache.controller

import io.bluetape4k.logging.coroutines.KLoggingChannel

class DefaultCountryControllerTest: AbstractCountryControllerTest() {

    companion object: KLoggingChannel()

    override val basePath: String = "default"

}
