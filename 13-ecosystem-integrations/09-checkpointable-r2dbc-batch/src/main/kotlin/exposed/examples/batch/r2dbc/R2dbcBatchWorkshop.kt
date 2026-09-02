package exposed.examples.batch.r2dbc

import io.bluetape4k.batch.BatchDefaults
import io.bluetape4k.batch.CheckpointJson
import io.bluetape4k.batch.api.BatchProcessor
import io.bluetape4k.batch.api.BatchReader
import io.bluetape4k.batch.api.BatchReport
import io.bluetape4k.batch.api.BatchWriter
import io.bluetape4k.batch.api.SkipPolicy
import io.bluetape4k.batch.core.BatchJob
import io.bluetape4k.batch.core.dsl.batchJob
import io.bluetape4k.batch.r2dbc.tables.BatchJobExecutionTable
import io.bluetape4k.batch.r2dbc.tables.BatchStepExecutionTable
import io.bluetape4k.batch.r2dbc.ExposedR2dbcBatchJobRepository
import io.bluetape4k.batch.r2dbc.ExposedR2dbcBatchReader
import io.bluetape4k.batch.r2dbc.ExposedR2dbcBatchWriter
import io.bluetape4k.workflow.api.RetryPolicy
import kotlin.time.Duration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** R2DBC batch workshop 입력 테이블입니다. */
object R2dbcBatchSourceTable : Table("r2dbc_batch_source") {
    /** 증가하는 keyset 읽기 기준입니다. */
    val id = long("id").autoIncrement()

    /** 변환할 원본 이름입니다. */
    val name = varchar("name", 255)

    /** 변환할 정수 값입니다. */
    val value = integer("value")

    override val primaryKey = PrimaryKey(id)
}

/** R2DBC batch workshop 출력 테이블입니다. */
object R2dbcBatchTargetTable : Table("r2dbc_batch_target") {
    /** 재시작 시 중복을 관찰할 source key입니다. */
    val sourceId = long("source_id")

    /** 대문자로 변환한 원본 이름입니다. */
    val sourceName = varchar("source_name", 255)

    /** 두 배로 변환한 정수 값입니다. */
    val transformedValue = integer("transformed_value")

    override val primaryKey = PrimaryKey(sourceId)
}

/** 입력 테이블에서 읽는 불변 레코드입니다. */
data class R2dbcSourceRecord(
    val id: Long,
    val name: String,
    val value: Int,
)

/** 출력 테이블에 쓰는 불변 레코드입니다. */
data class R2dbcTargetRecord(
    val sourceId: Long,
    val sourceName: String,
    val transformedValue: Int,
)

/** R2DBC checkpointable batch 예제의 실행 옵션입니다. */
data class R2dbcBatchOptions(
    /** 재시작 식별에 사용하는 job 이름입니다. */
    val jobName: String = "checkpointable-r2dbc-batch",
    /** 같은 데이터셋 실행을 재사용할 때 사용하는 파라미터입니다. */
    val parameters: Map<String, Any> = mapOf("dataset" to "workshop"),
    /** 한 번에 writer로 전달할 item 수입니다. */
    val chunkSize: Int = 3,
    /** keyset reader가 한 번에 조회할 page 크기입니다. */
    val pageSize: Int = chunkSize,
    /** processor 또는 writer 예외를 허용할 정책입니다. */
    val skipPolicy: SkipPolicy = SkipPolicy.NONE,
    /** writer 실패 재시도 정책입니다. */
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    /** 한 chunk의 commit timeout입니다. */
    val commitTimeout: Duration = BatchDefaults.COMMIT_TIMEOUT,
) {
    init {
        require(jobName.isNotBlank()) { "jobName must not be blank" }
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(pageSize > 0) { "pageSize must be positive" }
    }
}

/** provider metadata와 workshop source/target table 목록입니다. */
val r2dbcBatchMetadataTables: List<Table> = listOf(
    BatchJobExecutionTable,
    BatchStepExecutionTable,
    R2dbcBatchSourceTable,
    R2dbcBatchTargetTable,
)

/** caller-owned R2DBC database에 provider와 workshop schema를 생성합니다. */
suspend fun createR2dbcBatchSchema(database: R2dbcDatabase) {
    suspendTransaction(db = database) {
        SchemaUtils.create(*r2dbcBatchMetadataTables.toTypedArray())
    }
}

/** source를 대문자 이름과 두 배 값으로 변환하는 기본 processor입니다. */
val defaultR2dbcProcessor = BatchProcessor<R2dbcSourceRecord, R2dbcTargetRecord> { source ->
    R2dbcTargetRecord(
        sourceId = source.id,
        sourceName = source.name.uppercase(),
        transformedValue = source.value * 2,
    )
}

private fun r2dbcSourceReader(
    database: R2dbcDatabase,
    pageSize: Int,
): BatchReader<R2dbcSourceRecord> = ExposedR2dbcBatchReader(
    database = database,
    table = R2dbcBatchSourceTable,
    keyColumn = R2dbcBatchSourceTable.id,
    pageSize = pageSize,
    rowMapper = { row ->
        R2dbcSourceRecord(
            id = row[R2dbcBatchSourceTable.id],
            name = row[R2dbcBatchSourceTable.name],
            value = row[R2dbcBatchSourceTable.value],
        )
    },
    keyExtractor = R2dbcSourceRecord::id,
    keyClass = Long::class,
)

/** target table에 R2DBC batch insert를 수행하는 provider writer입니다. */
fun r2dbcTargetWriter(database: R2dbcDatabase): BatchWriter<R2dbcTargetRecord> =
    ExposedR2dbcBatchWriter(database, R2dbcBatchTargetTable) { target ->
        this[R2dbcBatchTargetTable.sourceId] = target.sourceId
        this[R2dbcBatchTargetTable.sourceName] = target.sourceName
        this[R2dbcBatchTargetTable.transformedValue] = target.transformedValue
    }

/** provider DSL을 직접 조합한 checkpointable R2DBC batch job을 생성합니다. */
fun checkpointableR2dbcBatchJob(
    database: R2dbcDatabase,
    options: R2dbcBatchOptions = R2dbcBatchOptions(),
    processor: BatchProcessor<R2dbcSourceRecord, R2dbcTargetRecord> = defaultR2dbcProcessor,
    writer: BatchWriter<R2dbcTargetRecord> = r2dbcTargetWriter(database),
): BatchJob = batchJob(options.jobName) {
    repository(ExposedR2dbcBatchJobRepository(database, CheckpointJson.jackson3()))
    params(options.parameters)
    step<R2dbcSourceRecord, R2dbcTargetRecord>("transform-and-write") {
        reader(r2dbcSourceReader(database, options.pageSize))
        processor(processor)
        writer(writer)
        chunkSize(options.chunkSize)
        skipPolicy(options.skipPolicy)
        retryPolicy(options.retryPolicy)
        commitTimeout(options.commitTimeout)
    }
}

/** 기본 R2DBC batch job을 실행합니다. database와 pool lifecycle은 호출자가 소유합니다. */
suspend fun runCheckpointableR2dbcBatch(
    database: R2dbcDatabase,
    options: R2dbcBatchOptions = R2dbcBatchOptions(),
): BatchReport = checkpointableR2dbcBatchJob(database, options).run()
