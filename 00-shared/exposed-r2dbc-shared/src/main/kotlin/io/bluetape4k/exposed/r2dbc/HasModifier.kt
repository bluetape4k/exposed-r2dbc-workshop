package io.bluetape4k.exposed.r2dbc

interface HasIdentifier<ID>: java.io.Serializable {
    val id: ID
}
