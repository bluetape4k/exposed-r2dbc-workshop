import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

abstract class VerifyVirtualThreadTestExecutionTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val requestedUseDb: Property<String>

    @get:Input
    @get:Optional
    abstract val requestedUseFastDb: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val knownDialects = setOf(
            "H2",
            "H2_MYSQL",
            "H2_PSQL",
            "H2_MARIADB",
            "H2_ORACLE",
            "H2_SQLSERVER",
            "MARIADB",
            "MYSQL_V5",
            "MYSQL_V8",
            "POSTGRESQL",
        )
        val requested = requestedUseDb.orNull
        val requestedFastDbValue = requestedUseFastDb.orNull
        val useFastDb = when (requestedFastDbValue?.lowercase(Locale.ROOT)) {
            null -> false
            "true" -> true
            "false" -> false
            else -> error("useFastDB는 true 또는 false여야 실행 수를 검증할 수 있습니다.")
        }
        val selectedDialects = if (requested != null) {
            val requestedTokens = requested.split(',').map { it.trim() }
            require(requestedTokens.all { token ->
                token.isNotEmpty() && knownDialects.any { it.equals(token, ignoreCase = true) }
            }) {
                "useDB에 알 수 없거나 빈 dialect token이 포함되어 있어 실행 수를 검증할 수 없습니다."
            }
            requestedTokens.map { token ->
                knownDialects.first { it.equals(token, ignoreCase = true) }
            }.toSet()
        } else if (useFastDb) {
            setOf("H2")
        } else {
            setOf("H2", "POSTGRESQL", "MYSQL_V8")
        }

        val reports = reportDirectory.get().asFile.listFiles { file ->
            file.isFile && file.name.startsWith("TEST-") && file.extension == "xml"
        }?.sortedBy { it.name }.orEmpty()
        require(reports.isNotEmpty()) {
            "JUnit XML report가 없어 virtual-thread 실행 수를 검증할 수 없습니다."
        }

        val factory = DocumentBuilderFactory.newDefaultInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }

        fun attributeAsInt(element: Element, attribute: String, reportName: String): Int =
            element.getAttribute(attribute).toIntOrNull()?.also { value ->
                require(value >= 0) {
                    "JUnit XML $attribute 속성이 음수입니다: $reportName"
                }
            } ?: error("JUnit XML $attribute 속성이 정수가 아닙니다: $reportName")

        var total = 0
        var skipped = 0
        var failures = 0
        var errors = 0
        data class SkippedCase(val classname: String, val name: String, val type: String, val message: String)

        val skippedCases = mutableListOf<SkippedCase>()
        val caseIdentities = mutableListOf<Pair<String, String>>()
        reports.forEach { report ->
            val suite = factory.newDocumentBuilder().parse(report).documentElement
            require(suite.tagName == "testsuite") {
                "JUnit XML root가 testsuite가 아닙니다: ${report.name}"
            }
            val declaredTests = attributeAsInt(suite, "tests", report.name)
            val cases = suite.getElementsByTagName("testcase")
            require(cases.length == declaredTests) {
                "JUnit XML testcase 수가 선언값과 다릅니다: ${report.name}"
            }
            total += declaredTests
            skipped += attributeAsInt(suite, "skipped", report.name)
            failures += attributeAsInt(suite, "failures", report.name)
            errors += attributeAsInt(suite, "errors", report.name)
            for (index in 0 until cases.length) {
                val testCase = cases.item(index) as? Element
                    ?: error("JUnit XML testcase node가 element가 아닙니다: ${report.name}")
                val classname = testCase.getAttribute("classname")
                val name = testCase.getAttribute("name")
                require(classname.isNotBlank() && name.isNotBlank()) {
                    "JUnit XML testcase classname/name이 비어 있습니다: ${report.name}"
                }
                caseIdentities += classname to name

                require(testCase.getElementsByTagName("failure").length == 0) {
                    "JUnit XML testcase에 failure element가 있어 실행 수 gate를 통과할 수 없습니다: ${report.name}"
                }
                require(testCase.getElementsByTagName("error").length == 0) {
                    "JUnit XML testcase에 error element가 있어 실행 수 gate를 통과할 수 없습니다: ${report.name}"
                }

                val descendantSkippedNodes = testCase.getElementsByTagName("skipped")
                val directSkippedNodes = (0 until testCase.childNodes.length)
                    .map { testCase.childNodes.item(it) }
                    .filter { node ->
                        node.nodeType == Node.ELEMENT_NODE && node.nodeName == "skipped"
                    }
                require(descendantSkippedNodes.length == directSkippedNodes.size) {
                    "JUnit XML testcase의 skipped element는 direct child여야 합니다: ${report.name}"
                }
                require(directSkippedNodes.size <= 1) {
                    "JUnit XML testcase에 skipped element가 둘 이상입니다: ${report.name}"
                }
                if (directSkippedNodes.isNotEmpty()) {
                    val skippedNode = directSkippedNodes.single()
                    val type = skippedNode.attributes?.getNamedItem("type")?.nodeValue.orEmpty()
                    val message = skippedNode.attributes?.getNamedItem("message")?.nodeValue.orEmpty()
                    skippedCases += SkippedCase(classname, name, type, message)
                }
            }
        }

        val allowedMariaDbSkips = selectedDialects.count { it == "MARIADB" || it == "H2_MARIADB" }
        val minimumTotal = 1 + selectedDialects.size * 4
        val expectedSkipped = allowedMariaDbSkips
        val minimumExecuted = minimumTotal - expectedSkipped
        val testClassName = "exposed.r2dbc.examples.virtualthreads.Ex01_VirtualThreads"
        val parameterizedDisplayNames = listOf(
            "virtual threads 를 이용하여 순차 작업 수행하기",
            "중첩된 virtual thread 용 트랜잭션을 async로 실행",
            "다수의 비동기 작업을 수행 후 대기",
            "virtual threads 환경에서 조건 조회",
        )
        val providerSmokeName = "JDK25 provider와 runtime을 선택하고 structured scope를 닫는다"
        val expectedIdentities = selectedDialects.flatMap { dialect ->
            parameterizedDisplayNames.map { displayName -> testClassName to "$displayName $dialect" }
        } + (testClassName to providerSmokeName)
        val actualIdentityCounts = caseIdentities.groupingBy { it }.eachCount()
        val expectedIdentityCounts = expectedIdentities.groupingBy { it }.eachCount()
        require(expectedIdentityCounts.all { (identity, expectedCount) ->
            actualIdentityCounts.getOrDefault(identity, 0) == expectedCount
        }) {
            "JUnit XML에 기대한 virtual-thread testcase identity가 없거나 중복되었습니다."
        }

        val nestedTransactionDisplayName = "중첩된 virtual thread 용 트랜잭션을 async로 실행"
        val expectedSkippedNames = selectedDialects
            .filter { it == "MARIADB" || it == "H2_MARIADB" }
            .map { "$nestedTransactionDisplayName $it" }
        require(skippedCases.map { it.name }.sorted() == expectedSkippedNames.sorted()) {
            "허용된 MariaDB capability skip 수가 다릅니다: expected=$expectedSkipped actual=${skippedCases.size}"
        }
        val expectedSkipType = "org.opentest4j.TestAbortedException"
        val expectedSkipMessage =
            "org.opentest4j.TestAbortedException: Assumption failed: MariaDB-compatible nested transactions are not supported"
        require(skippedCases.all { skippedCase ->
            skippedCase.classname == testClassName &&
                skippedCase.name in expectedSkippedNames &&
                skippedCase.type == expectedSkipType &&
                skippedCase.message == expectedSkipMessage
        }) {
            "허용되지 않은 skipped testcase type/message가 발견되었습니다."
        }
        require(failures == 0 && errors == 0) {
            "테스트 failure/error가 있어 실행 수 gate를 통과할 수 없습니다: failures=$failures errors=$errors"
        }
        require(total >= minimumTotal && skipped == expectedSkipped && total - skipped >= minimumExecuted) {
            "virtual-thread test 실행 수가 최소 계약과 다릅니다: total=$total executed=${total - skipped} skipped=$skipped minimumTotal=$minimumTotal minimumExecuted=$minimumExecuted expectedSkipped=$expectedSkipped"
        }

        logger.lifecycle(
            "virtual-thread test execution verified: reports=${reports.size} total=$total executed=${total - skipped} skipped=$skipped",
        )
    }
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
    testRuntimeClasspath {
        exclude(
            group = "io.github.bluetape4k",
            module = "bluetape4k-virtualthread-jdk21",
        )
    }
}

dependencies {

    testImplementation(project(":exposed-r2dbc-shared"))

    // Exposed
    testImplementation(libs.jetbrains.exposed.r2dbc)
    testImplementation(libs.exposed.r2dbc)

    // Java 25 Virtual Thread provider는 bluetape4k-dependencies BOM이 버전을 결정한다.
    testRuntimeOnly(libs.bluetape4k.virtualthread.jdk25)
    testImplementation(libs.bluetape4k.junit5)

    testRuntimeOnly(libs.h2.v2)

    testRuntimeOnly(libs.r2dbc.spi)
    testRuntimeOnly(libs.r2dbc.pool)
    testRuntimeOnly(libs.r2dbc.h2)
    testRuntimeOnly(libs.r2dbc.mariadb)
    testRuntimeOnly(libs.r2dbc.mysql)
    testRuntimeOnly(libs.r2dbc.postgresql)

    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.mariadb)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.postgresql)

    // Testcontainers 를 위한 DB 드라이버
    testImplementation(libs.mariadb.java.client)
    testImplementation(libs.mysql.connector.j)
    testImplementation(libs.postgresql.driver)

    // 코루틴 지원 의존성
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)
}

val virtualThreadTest = tasks.named<Test>("test")

tasks.register<VerifyVirtualThreadTestExecutionTask>("verifyVirtualThreadTestExecution") {
    dependsOn(virtualThreadTest)
    requestedUseDb.set(providers.gradleProperty("useDB"))
    requestedUseFastDb.set(providers.gradleProperty("useFastDB"))
    reportDirectory.set(layout.buildDirectory.dir("test-results/test"))
}
