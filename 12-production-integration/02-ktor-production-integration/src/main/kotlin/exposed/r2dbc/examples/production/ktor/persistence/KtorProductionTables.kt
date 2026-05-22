package exposed.r2dbc.examples.production.ktor.persistence

import org.jetbrains.exposed.v1.core.Table

internal object KtorProductionTables {
    object Accounts: Table("ktor_prod_accounts") {
        val id = varchar("id", 64)
        val username = varchar("username", 80).uniqueIndex()
        val apiKey = varchar("api_key", 120).uniqueIndex()
        val passwordHash = varchar("password_hash", 120)
        val displayName = varchar("display_name", 120)
        val permission = varchar("permission", 80)
        val roles = varchar("roles", 200)
        override val primaryKey = PrimaryKey(id)
    }

    object Sessions: Table("ktor_prod_sessions") {
        val id = varchar("id", 64)
        val username = varchar("username", 80)
        val tokenHash = varchar("token_hash", 64).uniqueIndex()
        val issuedAtEpochMs = long("issued_at_epoch_ms")
        val expiresAtEpochMs = long("expires_at_epoch_ms")
        override val primaryKey = PrimaryKey(id)
    }

    object WorkItems: Table("ktor_prod_work_items") {
        val id = varchar("id", 64)
        val owner = varchar("owner", 80)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object OutboxEvents: Table("ktor_prod_outbox_events") {
        val sequence = long("sequence").autoIncrement()
        val aggregateId = varchar("aggregate_id", 64)
        val eventType = varchar("event_type", 80)
        val payload = varchar("payload", 500)
        val delivered = bool("delivered").default(false)
        val status = varchar("status", 32).default("PENDING")
        val attempts = integer("attempts").default(0)
        val lastError = varchar("last_error", 240).nullable()
        override val primaryKey = PrimaryKey(sequence)
    }

    object OutboundRequests: Table("ktor_prod_outbound_requests") {
        val id = varchar("id", 64)
        val idempotencyKey = varchar("idempotency_key", 120).uniqueIndex()
        val targetUrl = varchar("target_url", 300)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40).default("PENDING")
        val attempts = integer("attempts").default(0)
        val lastStatusCode = integer("last_status_code").nullable()
        val lastError = varchar("last_error", 240).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Diagnostics: Table("ktor_prod_diagnostics") {
        val name = varchar("name", 80)
        val status = varchar("status", 40)
        val details = varchar("details", 500)
        override val primaryKey = PrimaryKey(name)
    }
}
