package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import kotlin.test.Test

class SmokeTestsRunnerGeneratorTest {
    val model =
        """
            ${'$'}version: "2"
            namespace com.test
            
            use smithy.test#smokeTests

            service Test {
                version: "1.0.0",
                operations: [ TestOperation ],
            }
            
            @smokeTests(
                [
                    {
                        id: "SuccessTest"
                        params: {bar: "2"}
                        tags: [
                            "success"
                        ]
                        expect: {
                            success: {}
                        }
                        vendorParamsShape: AwsVendorParams,
                        vendorParams: {
                            region: "eu-central-1",
                            uri: "https://foo.amazon.com"
                        }
                    }
                    {
                        id: "InvalidMessageErrorTest"
                        params: {bar: "föö"}
                        expect: {
                            failure: {errorId: InvalidMessageError}
                        }
                    }
                    {
                        id: "FailureTest"
                        params: {bar: "föö"}
                        expect: {
                            failure: {}
                        }
                    }
                ]
            )
            
            operation TestOperation {
                input := {
                    bar: String
                }
                errors: [
                    InvalidMessageError
                ]
            }
            
            @error("client")
            structure InvalidMessageError {}
            
            structure AwsVendorParams {
                region: String
            }
        """.toSmithyModel()

    private val generatedCode = generateSmokeTests(model)

    @Test
    fun variablesTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private val smokeTestSkipTags = smokeTestPlatform.getenv("SMOKE_TEST_SKIP_TAGS")?.let { it.split(",").map { it.trim() }.toSet() } ?: emptySet()
                private val smokeTestServiceFilter = smokeTestPlatform.getenv("SMOKE_TEST_SERVICE_IDS")?.let { it.split(",").map { it.trim() }.toSet() }
            """.formatForTest(),
        )
    }

    @Test
    fun mainTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun main() {
                    val smokeTestsSuccess = SmokeTestRunner().runAllTests()
                    if (!smokeTestsSuccess) {
                        exitProcess(1)
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun runnerClassTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            "public class SmokeTestRunner(private val smokeTestPlatform: PlatformProvider = PlatformProvider.System, private val smokeTestPrinter: Appendable = DefaultPrinter) {",
        )
    }

    @Test
    fun runAllTestsTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun runAllTests(): Boolean =
                    listOf<suspend () -> Boolean>(
                        ::successTest,
                        ::invalidMessageErrorTest,
                        ::failureTest,
                    )
                        .map { it() }
                        .all { it }
            """.formatForTest(),
        )
    }

    @Test
    fun successTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun successTest(): Boolean {
                    val smokeTestTags = setOf<String>("success")
                    if ((smokeTestServiceFilter.isNotEmpty() && "Test" !in smokeTestServiceFilter) || smokeTestTags.any { it in smokeTestSkipTags }) {
                        smokeTestPrinter.appendLine("ok Test SuccessTest - no error expected from service # skip")
                        return true
                    }
                
                    return try {
                        TestClient {
                            interceptors.add(SmokeTestsInterceptor())
                            region = "eu-central-1"
                            uri = "https://foo.amazon.com"
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "2"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val smokeTestSuccess: Boolean = exception is SmokeTestsSuccessException
                        val smokeTestStatus: String = if (smokeTestSuccess) "ok" else "not ok"
                        smokeTestPrinter.appendLine("${'$'}smokeTestStatus Test SuccessTest - no error expected from service ")
                        if (!smokeTestSuccess) {
                            smokeTestPrinter.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        smokeTestSuccess
                    }
                }
            """.formatForTest(),
        )
    }

    @Test
    fun invalidMessageErrorTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun invalidMessageErrorTest(): Boolean {
                    val smokeTestTags = setOf<String>()
                    if ((smokeTestServiceFilter.isNotEmpty() && "Test" !in smokeTestServiceFilter) || smokeTestTags.any { it in smokeTestSkipTags }) {
                        smokeTestPrinter.appendLine("ok Test InvalidMessageErrorTest - error expected from service # skip")
                        return true
                    }
                
                    return try {
                        TestClient {
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val smokeTestSuccess: Boolean = exception is InvalidMessageError
                        val smokeTestStatus: String = if (smokeTestSuccess) "ok" else "not ok"
                        smokeTestPrinter.appendLine("${'$'}smokeTestStatus Test InvalidMessageErrorTest - error expected from service ")
                        if (!smokeTestSuccess) {
                            smokeTestPrinter.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        smokeTestSuccess
                    }
                }
            """.formatForTest(),
        )
    }

    @Test
    fun failureTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun failureTest(): Boolean {
                    val smokeTestTags = setOf<String>()
                    if ((smokeTestServiceFilter.isNotEmpty() && "Test" !in smokeTestServiceFilter) || smokeTestTags.any { it in smokeTestSkipTags }) {
                        smokeTestPrinter.appendLine("ok Test FailureTest - error expected from service # skip")
                        return true
                    }
                
                    return try {
                        TestClient {
                            interceptors.add(SmokeTestsInterceptor())
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val smokeTestSuccess: Boolean = exception is SmokeTestsFailureException
                        val smokeTestStatus: String = if (smokeTestSuccess) "ok" else "not ok"
                        smokeTestPrinter.appendLine("${'$'}smokeTestStatus Test FailureTest - error expected from service ")
                        if (!smokeTestSuccess) {
                            smokeTestPrinter.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        smokeTestSuccess
                    }
                }
            """.formatForTest(),
        )
    }

    private fun generateSmokeTests(model: Model): String {
        val testCtx = model.newTestContext()
        val codegenCtx = testCtx.toCodegenContext()
        val writer = testCtx.newWriter()
        SmokeTestsRunnerGenerator(
            writer,
            codegenCtx,
        ).render()
        return writer.toString()
    }
}
