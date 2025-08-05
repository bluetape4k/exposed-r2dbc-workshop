@file:OptIn(ExperimentalTime::class)

package exposed.r2dbc.examples.kotlin.datetime

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.datetime.KotlinOffsetDateTimeColumnType
import java.time.OffsetDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

internal val dbTimestampNow: CustomFunction<OffsetDateTime>
    get() = object: CustomFunction<OffsetDateTime>("now", KotlinOffsetDateTimeColumnType()) {}
