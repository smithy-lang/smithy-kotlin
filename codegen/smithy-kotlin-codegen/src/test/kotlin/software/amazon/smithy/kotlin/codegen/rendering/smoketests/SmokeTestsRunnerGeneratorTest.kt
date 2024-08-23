package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.kotlin.codegen.utils.operations
import software.amazon.smithy.model.Model
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.test.Test

class SmokeTestsRunnerGeneratorTest {
    private val moneySign = "$"

    private fun codegen(model: Model): String {
        val testCtx = model.newTestContext()
        val codegenCtx = testCtx.toCodegenContext()
        val writer = testCtx.newWriter()
        SmokeTestsRunnerGenerator(
            writer,
            codegenCtx.symbolProvider.toSymbol(codegenCtx.model.expectShape(codegenCtx.settings.service)),
            codegenCtx.model.operations(codegenCtx.settings.service).filter { it.hasTrait<SmokeTestsTrait>() },
            codegenCtx.model,
            codegenCtx.symbolProvider,
            codegenCtx.settings.sdkId,
        ).render()
        return writer.toString()
    }

    @Test
    fun codegenTest() {
        val model =
            """
            ${moneySign}version: "2"
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

        val generatedCode = codegen(model)

        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private var exitCode = 0
                private val regionOverride = System.getenv("AWS_SMOKE_TEST_REGION")
                private val skipTags = System.getenv("AWS_SMOKE_TEST_SKIP_TAGS")?.let { it.split(",").map { it.trim() }.toSet() }
            """.trimIndent(),
        )

        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun main() {
                    successTest()
                    invalidMessageErrorTest()
                    failureTest()
                    aws.smithy.kotlin.runtime.http.interceptors.exitProcess(exitCode)
                }
            """.trimIndent(),
        )

        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun successTest() {
                    val tags = setOf<String>("success")
                    if (skipTags != null && tags.any { it in skipTags }) return

                    try {
                        com.test.TestClient {
                            region = regionOverride ?: "eu-central-1"
                            interceptors.add(SmokeTestsInterceptor(false))

                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "2"
                                }
                            )
                        }

                    } catch (e: Exception) {
                        val success = e is aws.smithy.kotlin.runtime.http.interceptors.SmokeTestsSuccessException
                        val status = if (success) "ok" else "not ok"
                        println("${moneySign}status Test SuccessTest - no error expected from service")
                        if (!success) exitCode = 1
                    }
                }
            """.trimIndent(),
        )

        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun invalidMessageErrorTest() {
                    val tags = setOf<String>()
                    if (skipTags != null && tags.any { it in skipTags }) return

                    try {
                        com.test.TestClient {
                            region = regionOverride
                            interceptors.add(SmokeTestsInterceptor(true))

                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }

                    } catch (e: Exception) {
                        val success = e is com.test.model.InvalidMessageError
                        val status = if (success) "ok" else "not ok"
                        println("${moneySign}status Test InvalidMessageErrorTest - error expected from service")
                        if (!success) exitCode = 1
                    }
                }
            """.trimIndent(),
        )

        generatedCode.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun failureTest() {
                    val tags = setOf<String>()
                    if (skipTags != null && tags.any { it in skipTags }) return

                    try {
                        com.test.TestClient {
                            region = regionOverride
                            interceptors.add(SmokeTestsInterceptor(false))

                        }.use { client ->
                            client.testOperation(
                                TestOperationRequest {
                                    bar = "föö"
                                }
                            )
                        }

                    } catch (e: Exception) {
                        val success = e is aws.smithy.kotlin.runtime.http.interceptors.SmokeTestsFailureException
                        val status = if (success) "ok" else "not ok"
                        println("${moneySign}status Test FailureTest - error expected from service")
                        if (!success) exitCode = 1
                    }
                }
            """.trimIndent(),
        )
    }
}
