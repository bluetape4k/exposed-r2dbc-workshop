package exposed.examples.batch.r2dbc

import exposed.r2dbc.shared.tests.AbstractR2dbcExposedTest
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.batch.api.BatchProcessor
import io.bluetape4k.batch.api.BatchReport
import io.bluetape4k.batch.api.BatchStatus
import io.bluetape4k.batch.api.BatchWriter
import io.bluetape4k.batch.api.SkipPolicy
import io.bluetape4k.batch.jdbc.tables.BatchJobExecutionTable
import io.bluetape4k.batch.jdbc.tables.BatchStepExecutionTable
import io.bluetape4k.workflow.api.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Test

class R2dbcBatchWorkshopTest : AbstractR2dbcExposedTest() {

    @Test
    fun `normal execution persists transformed rows and checkpoint`() = runTest {
        val database = preparedDatabase("normal", 1..8)

        val report = runCheckpointableR2dbcBatch(
            database,
            R2dbcBatchOptions(chunkSize = 3),
        )

        report shouldBeInstanceOf BatchReport.Success::class
        report.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED
        report.stepReports.single().readCount shouldBeEqualTo 8L
        report.stepReports.single().writeCount shouldBeEqualTo 8L
        report.stepReports.single().skipCount shouldBeEqualTo 0L
        report.stepReports.single().checkpoint shouldBeEqualTo 8L
        targetRows(database).map { it.sourceId } shouldBeEqualTo (1L..8L).toList()
        targetRows(database).single { it.sourceId == 1L } shouldBeEqualTo R2dbcTargetRecord(
            sourceId = 1L,
            sourceName = "ITEM-1",
            transformedValue = 2,
        )
    }

    @Test
    fun `failed execution is surfaced with metadata and committed chunks`() = runTest {
        val database = preparedDatabase("failure", 1..8)
        val options = R2dbcBatchOptions(
            jobName = "checkpointable-r2dbc-failure",
            chunkSize = 3,
        )
        val failingWriter = FailOnceWriter(r2dbcTargetWriter(database))

        val report = checkpointableR2dbcBatchJob(
            database = database,
            options = options,
            writer = failingWriter,
        ).run()

        report shouldBeInstanceOf BatchReport.Failure::class
        report.stepReports.single().status shouldBeEqualTo BatchStatus.FAILED
        report.stepReports.single().writeCount shouldBeEqualTo 3L
        failingWriter.attempts shouldBeEqualTo 2
        targetRows(database).map { it.sourceId } shouldBeEqualTo listOf(1L, 2L, 3L)
        jobStatus(database) shouldBeEqualTo BatchStatus.FAILED
        stepStatus(database) shouldBeEqualTo BatchStatus.FAILED
    }

    @Test
    fun `processor errors are skipped and successful records are written`() = runTest {
        val database = preparedDatabase("skip", 1..10)
        val processor = BatchProcessor<R2dbcSourceRecord, R2dbcTargetRecord> { source ->
            if (source.value % 2 == 0) {
                throw IllegalArgumentException("even value")
            }
            defaultR2dbcProcessor.process(source)
        }

        val report = checkpointableR2dbcBatchJob(
            database = database,
            options = R2dbcBatchOptions(
                jobName = "checkpointable-r2dbc-skip",
                chunkSize = 4,
                skipPolicy = SkipPolicy.ALL,
            ),
            processor = processor,
        ).run()

        report shouldBeInstanceOf BatchReport.PartiallyCompleted::class
        report.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED_WITH_SKIPS
        report.stepReports.single().skipCount shouldBeEqualTo 5L
        report.stepReports.single().writeCount shouldBeEqualTo 5L
        targetRows(database).map { it.sourceId } shouldBeEqualTo listOf(1L, 3L, 5L, 7L, 9L)
    }

    @Test
    fun `writer failure retries with bounded backoff`() = runTest {
        val database = preparedDatabase("retry", 1..3)
        val retryingWriter = FailFirstWriter(r2dbcTargetWriter(database))

        val report = checkpointableR2dbcBatchJob(
            database = database,
            options = R2dbcBatchOptions(
                jobName = "checkpointable-r2dbc-retry",
                retryPolicy = RetryPolicy(maxAttempts = 2, delay = 1.milliseconds),
            ),
            writer = retryingWriter,
        ).run()

        report shouldBeInstanceOf BatchReport.Success::class
        report.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED
        retryingWriter.attempts shouldBeEqualTo 2
        targetRows(database).size shouldBeEqualTo 3
    }

    @Test
    fun `commit timeout skips one timed out chunk without partial target rows`() = runTest {
        val database = preparedDatabase("timeout", 1..3)
        val slowWriter = SlowWriter(r2dbcTargetWriter(database), 50.milliseconds)

        val report = checkpointableR2dbcBatchJob(
            database = database,
            options = R2dbcBatchOptions(
                jobName = "checkpointable-r2dbc-timeout",
                chunkSize = 3,
                commitTimeout = 5.milliseconds,
                skipPolicy = SkipPolicy.maxSkips(3),
            ),
            writer = slowWriter,
        ).run()

        report shouldBeInstanceOf BatchReport.PartiallyCompleted::class
        report.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED_WITH_SKIPS
        report.stepReports.single().skipCount shouldBeEqualTo 3L
        report.stepReports.single().writeCount shouldBeEqualTo 0L
        targetRows(database).size shouldBeEqualTo 0
    }

    @Test
    fun `cancellation persists STOPPED and restart resumes after the saved checkpoint`() = runTest {
        val database = preparedDatabase("cancel", 1..8)
        val firstWriteCompleted = CompletableDeferred<Unit>()
        val secondWriteStarted = CompletableDeferred<Unit>()
        val blockingWriter = CancellationWriter(
            delegate = r2dbcTargetWriter(database),
            firstWriteCompleted = firstWriteCompleted,
            secondWriteStarted = secondWriteStarted,
        )
        val options = R2dbcBatchOptions(
            jobName = "checkpointable-r2dbc-cancel",
            chunkSize = 3,
        )
        val running = async {
            checkpointableR2dbcBatchJob(database, options, writer = blockingWriter).run()
        }

        firstWriteCompleted.await()
        secondWriteStarted.await()
        withTimeout(5.seconds) {
            while (stepCheckpoint(database) == null) {
                delay(1.milliseconds)
            }
        }

        running.cancel()
        assertFailsWith<CancellationException> { running.await() }

        jobStatus(database) shouldBeEqualTo BatchStatus.STOPPED
        stepStatus(database) shouldBeEqualTo BatchStatus.STOPPED
        stepCheckpoint(database)?.contains("\"className\":\"java.lang.Long\"") shouldBeEqualTo true
        stepCheckpoint(database)?.contains("\"payload\":\"3\"") shouldBeEqualTo true

        val restarted = runCheckpointableR2dbcBatch(database, options)

        restarted shouldBeInstanceOf BatchReport.Success::class
        restarted.stepReports.single().status shouldBeEqualTo BatchStatus.COMPLETED
        targetRows(database).map { it.sourceId } shouldBeEqualTo (1L..8L).toList()
        targetRows(database).map { it.sourceId }.distinct().size shouldBeEqualTo 8
    }

    @Test
    fun `schema creates provider metadata and duplicate-observing target primary key`() = runTest {
        val database = h2Database("schema")

        createR2dbcBatchSchema(database)

        val tableCounts = suspendTransaction(db = database) {
            r2dbcBatchMetadataTables.associate { table ->
                table.tableName to table.selectAll().count()
            }
        }
        tableCounts.keys shouldBeEqualTo r2dbcBatchMetadataTables.map { it.tableName }.toSet()
        R2dbcBatchTargetTable.primaryKey.columns.single() shouldBeEqualTo R2dbcBatchTargetTable.sourceId
    }

    @Test
    fun `options reject invalid execution boundaries`() {
        assertFailsWith<IllegalArgumentException> { R2dbcBatchOptions(jobName = " ") }
        assertFailsWith<IllegalArgumentException> { R2dbcBatchOptions(chunkSize = 0) }
        assertFailsWith<IllegalArgumentException> { R2dbcBatchOptions(pageSize = 0) }
    }

    private suspend fun preparedDatabase(name: String, values: IntRange): R2dbcDatabase {
        val database = h2Database(name)
        createR2dbcBatchSchema(database)
        suspendTransaction(db = database) {
            R2dbcBatchSourceTable.batchInsert(values.toList(), shouldReturnGeneratedValues = false) { value ->
                this[R2dbcBatchSourceTable.name] = "item-$value"
                this[R2dbcBatchSourceTable.value] = value
            }
        }
        return database
    }

    private fun h2Database(name: String): R2dbcDatabase = R2dbcDatabase.connect(
        "r2dbc:pool:h2:mem:///checkpointable-r2dbc-$name;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    )

    private suspend fun targetRows(database: R2dbcDatabase): List<R2dbcTargetRecord> =
        suspendTransaction(db = database) {
            R2dbcBatchTargetTable.selectAll().map { row ->
                R2dbcTargetRecord(
                    sourceId = row[R2dbcBatchTargetTable.sourceId],
                    sourceName = row[R2dbcBatchTargetTable.sourceName],
                    transformedValue = row[R2dbcBatchTargetTable.transformedValue],
                )
            }.toList().sortedBy { it.sourceId }
        }

    private suspend fun jobStatus(database: R2dbcDatabase): BatchStatus =
        suspendTransaction(db = database) {
            BatchJobExecutionTable.selectAll().map { it[BatchJobExecutionTable.status] }.single()
        }

    private suspend fun stepStatus(database: R2dbcDatabase): BatchStatus =
        suspendTransaction(db = database) {
            BatchStepExecutionTable.selectAll().map { it[BatchStepExecutionTable.status] }.single()
        }

    private suspend fun stepCheckpoint(database: R2dbcDatabase): String? =
        suspendTransaction(db = database) {
            BatchStepExecutionTable.selectAll().map { it[BatchStepExecutionTable.checkpoint] }.single()
        }

    private class FailOnceWriter(
        private val delegate: BatchWriter<R2dbcTargetRecord>,
    ) : BatchWriter<R2dbcTargetRecord> {
        var attempts: Int = 0
            private set

        override suspend fun open() = delegate.open()

        override suspend fun write(items: List<R2dbcTargetRecord>) {
            attempts++
            if (attempts == 2) {
                throw IllegalStateException("fail the second chunk once")
            }
            delegate.write(items)
        }

        override suspend fun close() = delegate.close()
    }

    private class FailFirstWriter(
        private val delegate: BatchWriter<R2dbcTargetRecord>,
    ) : BatchWriter<R2dbcTargetRecord> {
        var attempts: Int = 0
            private set

        override suspend fun open() = delegate.open()

        override suspend fun write(items: List<R2dbcTargetRecord>) {
            attempts++
            if (attempts == 1) {
                throw IllegalStateException("fail the first write")
            }
            delegate.write(items)
        }

        override suspend fun close() = delegate.close()
    }

    private class SlowWriter(
        private val delegate: BatchWriter<R2dbcTargetRecord>,
        private val writeDelay: Duration,
    ) : BatchWriter<R2dbcTargetRecord> {
        override suspend fun open() = delegate.open()

        override suspend fun write(items: List<R2dbcTargetRecord>) {
            delay(writeDelay)
            delegate.write(items)
        }

        override suspend fun close() = delegate.close()
    }

    private class CancellationWriter(
        private val delegate: BatchWriter<R2dbcTargetRecord>,
        private val firstWriteCompleted: CompletableDeferred<Unit>,
        private val secondWriteStarted: CompletableDeferred<Unit>,
    ) : BatchWriter<R2dbcTargetRecord> {
        private var calls = 0

        override suspend fun open() = delegate.open()

        override suspend fun write(items: List<R2dbcTargetRecord>) {
            calls++
            if (calls == 1) {
                delegate.write(items)
                firstWriteCompleted.complete(Unit)
            } else {
                secondWriteStarted.complete(Unit)
                awaitCancellation()
            }
        }

        override suspend fun close() = delegate.close()
    }
}
