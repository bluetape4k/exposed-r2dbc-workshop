package exposed.r2dbc.examples.custom.columns

import exposed.r2dbc.shared.dml.DMLTestData.Cities.clientDefault
import io.bluetape4k.idgenerators.ksuid.Ksuid
import io.bluetape4k.idgenerators.ksuid.KsuidMillis
import io.bluetape4k.idgenerators.snowflake.Snowflakers
import io.bluetape4k.idgenerators.uuid.TimebasedUuid
import org.jetbrains.exposed.v1.core.Column
import java.util.*

fun Column<String>.ksuidGenerated(): Column<String> =
    clientDefault { Ksuid.nextIdAsString() }

fun Column<String>.ksuidMillsGenerated(): Column<String> =
    clientDefault { KsuidMillis.nextIdAsString() }

@JvmName("snowflakeGeneratedLong")
fun Column<Long>.snowflakeGenerated(): Column<Long> =
    clientDefault { Snowflakers.Global.nextId() }

@JvmName("snowflakeGeneratedString")
fun Column<String>.snowflakeGenerated(): Column<String> =
    clientDefault { Snowflakers.Global.nextIdAsString() }

@JvmName("timebasedUUIDGeneratedUUID")
fun Column<UUID>.timebasedUUIDGenerated(): Column<UUID> =
    clientDefault { TimebasedUuid.Reordered.nextId() }

@JvmName("timebasedUUIDGeneratedString")
fun Column<String>.timebasedUUIDGenerated(): Column<String> =
    clientDefault { TimebasedUuid.Reordered.nextIdAsString() }
