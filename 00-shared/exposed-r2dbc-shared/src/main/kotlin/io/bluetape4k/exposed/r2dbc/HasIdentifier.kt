package io.bluetape4k.exposed.r2dbc

interface HasIdentifier<ID: Any>: java.io.Serializable {
    val id: ID
}
