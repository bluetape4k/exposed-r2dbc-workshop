package exposed.r2dbc.examples.ecosystem.bigquery

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.select
import java.io.Serializable

object BigQueryDryRunSource: Table("bigquery_dry_run_source") {
    val eventDate = varchar("event_date", 10)
    val region = varchar("region", 32)
    val revenue = decimal("revenue", precision = 14, scale = 2)
}

data class BigQueryDryRunProfile(
    val projectId: String,
    val dataset: String,
    val location: String = "US",
    val labels: Map<String, String> = emptyMap(),
    val priority: String = "INTERACTIVE",
    val timeoutMs: Long = 30_000,
): Serializable {

    init {
        projectId.requireNotBlank("projectId")
        dataset.requireNotBlank("dataset")
        location.requireNotBlank("location")
        priority.requireNotBlank("priority")
        require(timeoutMs in 1..300_000) { "timeoutMs must be in 1..300000" }
        labels.forEach { (key, value) ->
            key.requireNotBlank("labels key")
            value.requireNotBlank("labels[$key]")
        }
    }

    val defaultDataset: String = "$projectId.$dataset"

    fun toDryRunRequest(sql: String): BigQueryDryRunRequest =
        BigQueryDryRunRequest(
            sql = sql.requireNotBlank("sql"),
            defaultDataset = defaultDataset,
            location = location,
            labels = labels,
            priority = priority,
            timeoutMs = timeoutMs,
        )
}

data class BigQueryDryRunRequest(
    val sql: String,
    val defaultDataset: String,
    val location: String,
    val labels: Map<String, String>,
    val priority: String,
    val timeoutMs: Long,
    val dryRun: Boolean = true,
    val useLegacySql: Boolean = false,
): Serializable

fun R2dbcTransaction.renderBigQueryCandidateSql(): String =
    BigQueryDryRunSource
        .select(
            BigQueryDryRunSource.eventDate,
            BigQueryDryRunSource.region,
            BigQueryDryRunSource.revenue,
        )
        .prepareSQL(this, prepared = false)

private fun String.requireNotBlank(name: String): String {
    require(isNotBlank()) { "$name must not be blank" }
    return this
}
