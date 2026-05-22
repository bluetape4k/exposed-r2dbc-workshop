package exposed.r2dbc.examples.production.ktor.persistence

import org.jetbrains.exposed.v1.core.Table

internal object KtorProductionTables {
    object Accounts: Table("ktor_prod_accounts") {
        val id = varchar("id", 64)
        val username = varchar("username", 80)
        val apiKey = varchar("api_key", 120).uniqueIndex()
        val permission = varchar("permission", 80)
        val sessionToken = varchar("session_token", 120)
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
        override val primaryKey = PrimaryKey(sequence)
    }

    object OutboundRequests: Table("ktor_prod_outbound_requests") {
        val id = varchar("id", 64)
        val idempotencyKey = varchar("idempotency_key", 120).uniqueIndex()
        val targetUrl = varchar("target_url", 300)
        val payload = varchar("payload", 500)
        val status = varchar("status", 40)
        override val primaryKey = PrimaryKey(id)
    }

    object Diagnostics: Table("ktor_prod_diagnostics") {
        val name = varchar("name", 80)
        val status = varchar("status", 40)
        val details = varchar("details", 500)
        override val primaryKey = PrimaryKey(name)
    }
}
