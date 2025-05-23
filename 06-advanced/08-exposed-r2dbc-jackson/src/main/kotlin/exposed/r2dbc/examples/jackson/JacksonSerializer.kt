package exposed.r2dbc.examples.jackson

import io.bluetape4k.jackson.JacksonSerializer

/**
 * Default [JacksonSerializer] instance.
 */
val DefaultJacksonSerializer: JacksonSerializer by lazy { JacksonSerializer() }
