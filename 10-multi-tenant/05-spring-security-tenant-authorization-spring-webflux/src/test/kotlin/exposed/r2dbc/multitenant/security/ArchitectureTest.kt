package exposed.r2dbc.multitenant.security

import io.bluetape4k.assertions.shouldBeEmpty
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.readText

class ArchitectureTest {

    @Test
    fun `production request code does not call bare suspendTransaction`() {
        val moduleRoot = Path.of(".").toAbsolutePath().normalize()
        val allowed = setOf(
            "src/main/kotlin/exposed/r2dbc/multitenant/security/tenant/TenantTransactionExecutor.kt",
            "src/main/kotlin/exposed/r2dbc/multitenant/security/tenant/DataInitializer.kt",
        )
        val offenders = Files
            .walk(moduleRoot.resolve("src/main/kotlin"))
            .use { stream ->
                stream
                    .filter { it.name.endsWith(".kt") }
                    .filter { path ->
                        val relativePath = moduleRoot.relativize(path).invariantSeparatorsPathString
                        relativePath !in allowed && BareSuspendTransactionRegex.containsMatchIn(path.readText())
                    }
                    .map { moduleRoot.relativize(it).invariantSeparatorsPathString }
                    .toList()
            }

        offenders.shouldBeEmpty()
    }

    @Test
    fun `integration tests avoid bare default database transactions`() {
        val moduleRoot = Path.of(".").toAbsolutePath().normalize()
        val offenders = Files
            .walk(moduleRoot.resolve("src/test/kotlin"))
            .use { stream ->
                stream
                    .filter { it.name.endsWith(".kt") }
                    .filter { path -> BareSuspendTransactionRegex.containsMatchIn(path.readText()) }
                    .map { moduleRoot.relativize(it).invariantSeparatorsPathString }
                    .toList()
            }

        offenders.shouldBeEmpty()
    }

    @Test
    fun `only authorized tenant filter writes request tenant reactor context`() {
        val moduleRoot = Path.of(".").toAbsolutePath().normalize()
        val allowed = setOf(
            "src/main/kotlin/exposed/r2dbc/multitenant/security/security/AuthorizedTenantContextWebFilter.kt",
        )
        val offenders = Files
            .walk(moduleRoot.resolve("src/main/kotlin"))
            .use { stream ->
                stream
                    .filter { it.name.endsWith(".kt") }
                    .filter { path ->
                        val relativePath = moduleRoot.relativize(path).invariantSeparatorsPathString
                        relativePath !in allowed &&
                            path.readText().contains("put(TenantContextKeys.TENANT_ID")
                    }
                    .map { moduleRoot.relativize(it).invariantSeparatorsPathString }
                    .toList()
            }

        offenders.shouldBeEmpty()
    }

    private companion object {
        val BareSuspendTransactionRegex = Regex("""\bsuspendTransaction\s*\(""")
    }
}
