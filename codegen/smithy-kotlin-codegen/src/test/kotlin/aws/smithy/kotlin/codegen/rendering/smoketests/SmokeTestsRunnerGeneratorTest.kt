/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.smoketests

import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.newWriter
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toCodegenContext
import aws.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestsRunnerGenerator
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
                private val skipTags = platform.getenv("SMOKE_TEST_SKIP_TAGS")?.let { it.split(",").map { it.trim() }.toSet() } ?: emptySet()
                private val serviceFilter = platform.getenv("SMOKE_TEST_SERVICE_IDS")?.let { it.split(",").map { it.trim() }.toSet() }
            """.formatForTest(),
        )
    }

    @Test
    fun mainTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun main() {
                    val success = SmokeTestRunner().runAllTests()
                    if (!success) {
                        exitProcess(1)
                    }
                }
            """.trimIndent(),
        )
    }

    @Test
    fun runnerClassTest() {
        generatedCode.shouldContainOnlyOnceWithDiff(
            "public class SmokeTestRunner(private val platform: PlatformProvider = PlatformProvider.System, private val printer: Appendable = DefaultPrinter) {",
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
                    val tags = setOf<String>("success")
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        printer.appendLine("ok Test SuccessTest - no error expected from service # skip")
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
                                    this.bar = "2"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val success: Boolean = exception is SmokeTestsSuccessException
                        val status: String = if (success) "ok" else "not ok"
                        printer.appendLine("${'$'}status Test SuccessTest - no error expected from service ")
                        if (!success) {
                            printer.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        success
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
                    val tags = setOf<String>()
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        printer.appendLine("ok Test InvalidMessageErrorTest - error expected from service # skip")
                        return true
                    }
                
                    return try {
                        TestClient {
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    this.bar = "föö"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val success: Boolean = exception is InvalidMessageError
                        val status: String = if (success) "ok" else "not ok"
                        printer.appendLine("${'$'}status Test InvalidMessageErrorTest - error expected from service ")
                        if (!success) {
                            printer.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        success
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
                    val tags = setOf<String>()
                    if ((serviceFilter.isNotEmpty() && "Test" !in serviceFilter) || tags.any { it in skipTags }) {
                        printer.appendLine("ok Test FailureTest - error expected from service # skip")
                        return true
                    }
                
                    return try {
                        TestClient {
                            interceptors.add(SmokeTestsInterceptor())
                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    this.bar = "föö"
                                }
                            )
                        }
                
                        error("Unexpectedly completed smoke test operation without throwing exception")
                
                    } catch (exception: Exception) {
                        val success: Boolean = exception is SmokeTestsFailureException
                        val status: String = if (success) "ok" else "not ok"
                        printer.appendLine("${'$'}status Test FailureTest - error expected from service ")
                        if (!success) {
                            printer.appendLine(exception.stackTraceToString().prependIndent("# "))
                        }
                
                        success
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
