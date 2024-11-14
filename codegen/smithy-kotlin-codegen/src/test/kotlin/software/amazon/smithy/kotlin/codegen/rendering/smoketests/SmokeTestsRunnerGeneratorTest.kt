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
                            region: "eu-central-1"
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
                private var exitCode = 0
                private val skipTags = PlatformProvider.System.getenv("SMOKE_TEST_SKIP_TAGS")?.let { it.split(",").map { it.trim() }.toSet() } ?: emptySet()
                private val serviceFilter = PlatformProvider.System.getenv("SMOKE_TEST_SERVICE_IDS")?.let { it.split(",").map { it.trim() }.toSet() }
            """.trimIndent(),
        )
    }

    @Test
    fun mainTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun main() {
                    successTest()
                    invalidMessageErrorTest()
                    failureTest()
                    exitProcess(exitCode)
                }
            """.trimIndent(),
        )
    }

    @Test
    fun successTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun successTest() {
                    val tags = setOf<String>("success")
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        println("ok Test SuccessTest - no error expected from service # skip")
                        return
                    }

                    try {
                        TestClient {
                            interceptors.add(SmokeTestsInterceptor())
                            region = "eu-central-1"
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "2"
                                }
                            )
                        }

                    } catch (exception: Exception) {
                        val success: Boolean = exception is SmokeTestsSuccessException
                        val status: String = if (success) "ok" else "not ok"
                        println("${'$'}status Test SuccessTest - no error expected from service ")
                        if (!success) {
                            printExceptionStackTrace(exception)
                            exitCode = 1
                        }
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun invalidMessageErrorTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun invalidMessageErrorTest() {
                    val tags = setOf<String>()
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        println("ok Test InvalidMessageErrorTest - error expected from service # skip")
                        return
                    }
                
                    try {
                        TestClient {
                
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }
                
                    } catch (exception: Exception) {
                        val success: Boolean = exception is InvalidMessageError
                        val status: String = if (success) "ok" else "not ok"
                        println("${'$'}status Test InvalidMessageErrorTest - error expected from service ")
                        if (!success) {
                            printExceptionStackTrace(exception)
                            exitCode = 1
                        }
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun failureTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun failureTest() {
                    val tags = setOf<String>()
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        println("ok Test FailureTest - error expected from service # skip")
                        return
                    }
                
                    try {
                        TestClient {
                            interceptors.add(SmokeTestsInterceptor())
                
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }
                
                    } catch (exception: Exception) {
                        val success: Boolean = exception is SmokeTestsFailureException
                        val status: String = if (success) "ok" else "not ok"
                        println("${'$'}status Test FailureTest - error expected from service ")
                        if (!success) {
                            printExceptionStackTrace(exception)
                            exitCode = 1
                        }
                    }
                }
            """.trimIndent(),
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
