package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.traits.FailedResponseTrait
import software.amazon.smithy.kotlin.codegen.model.traits.SuccessResponseTrait
import software.amazon.smithy.kotlin.codegen.rendering.util.format
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.operations
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.smoketests.traits.SmokeTestCase
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.jvm.optionals.getOrNull

object SmokeTestsRunner : SectionId

// Env var constants
const val SKIP_TAGS = "AWS_SMOKE_TEST_SKIP_TAGS"
const val SERVICE_FILTER = "AWS_SMOKE_TEST_SERVICE_IDS"

/**
 * Renders smoke tests runner for a service
 */
class SmokeTestsRunnerGenerator(
    private val writer: KotlinWriter,
    ctx: CodegenContext,
) {
    // Generator config
    private val model = ctx.model
    private val sdkId = ctx.settings.sdkId
    private val symbolProvider = ctx.symbolProvider
    private val service = symbolProvider.toSymbol(model.expectShape(ctx.settings.service))
    private val operations = ctx.model.operations(ctx.settings.service).filter { it.hasTrait<SmokeTestsTrait>() }

    // Test config
    private val hasSuccessResponseTrait = model.expectShape<ServiceShape>(ctx.settings.service).hasTrait(SuccessResponseTrait.ID)
    private val hasFailedResponseTrait = model.expectShape<ServiceShape>(ctx.settings.service).hasTrait(FailedResponseTrait.ID)
    init {
        check(!(hasSuccessResponseTrait && hasFailedResponseTrait)) {
            "A service can't have both the success response trait and the failed response trait."
        }
    }

    internal fun render() {
        writer.declareSection(SmokeTestsRunner) {
            writer.write("import kotlin.system.exitProcess")
            writer.emptyLine()
            writer.write("private var exitCode = 0")
            writer.write("private val regionOverride = System.getenv(#S)", "AWS_SMOKE_TEST_REGION")
            writer.write("private val skipTags = System.getenv(#S)?.let { it.split(\",\").map { it.trim() }.toSet() } ?: emptySet()", SKIP_TAGS)
            writer.write("private val serviceFilter = System.getenv(#S)?.let { it.split(\",\").map { it.trim() }.toSet() }", SERVICE_FILTER)
            writer.emptyLine()
            writer.withBlock("public suspend fun main() {", "}") {
                renderFunctionCalls()
                write("exitProcess(exitCode)")
            }
            writer.emptyLine()
            renderFunctions()
        }
    }

    private fun renderFunctionCalls() {
        operations.forEach { operation ->
            operation.getTrait<SmokeTestsTrait>()?.testCases?.forEach { testCase ->
                writer.write("${testCase.functionName}()")
            }
        }
    }

    private fun renderFunctions() {
        operations.forEach { operation ->
            operation.getTrait<SmokeTestsTrait>()?.testCases?.forEach { testCase ->
                renderFunction(operation, testCase)
                writer.emptyLine()
            }
        }
    }

    private fun renderFunction(operation: OperationShape, testCase: SmokeTestCase) {
        writer.withBlock("private suspend fun ${testCase.functionName}() {", "}") {
            write("val tags = setOf<String>(${testCase.tags.joinToString(",") { it.dq()} })")
            writer.withBlock("if ((serviceFilter != null && #S !in serviceFilter) || tags.any { it in skipTags }) {", "}", sdkId) {
                printTestResult(
                    sdkId.filter { !it.isWhitespace() },
                    testCase.id,
                    testCase.expectation.isFailure,
                    writer,
                    "ok",
                    "# skip",
                )
                writer.write("return")
            }
            emptyLine()
            withInlineBlock("try {", "} ") {
                renderClient(testCase)
                renderOperation(operation, testCase)
            }
            withBlock("catch (e: Exception) {", "}") {
                renderCatchBlock(testCase)
            }
        }
    }

    private fun renderClient(testCase: SmokeTestCase) {
        writer.withInlineBlock("#L {", "}", service) {
            // Client config
            if (testCase.vendorParams.isPresent) {
                testCase.vendorParams.get().members.forEach { vendorParam ->
                    if (vendorParam.key.value == "region") {
                        write("region = regionOverride ?: #L", vendorParam.value.format())
                    } else {
                        write("#L = #L", vendorParam.key.value.toCamelCase(), vendorParam.value.format())
                    }
                }
            } else {
                write("region = regionOverride")
            }
            val expectingSpecificError = testCase.expectation.failure.getOrNull()?.errorId?.getOrNull() != null
            if (!expectingSpecificError) {
                write("interceptors.add(#T())", RuntimeTypes.HttpClient.Interceptors.SmokeTestsInterceptor)
            }

            // Test config
            if (hasSuccessResponseTrait) {
                write("httpClient = #T()", RuntimeTypes.HttpTest.TestEngine)
            }
            if (hasFailedResponseTrait) {
                withBlock("httpClient = #T(", ")", RuntimeTypes.HttpTest.TestEngine) {
                    withBlock("roundTripImpl = { _, request ->", "}") {
                        write(
                            "val resp = #T(#T.BadRequest, #T.Empty, #T.Empty)",
                            RuntimeTypes.Http.Response.HttpResponse,
                            RuntimeTypes.Http.StatusCode,
                            RuntimeTypes.Http.Headers,
                            RuntimeTypes.Http.HttpBody,
                        )
                        write("val now = #T.now()", RuntimeTypes.Core.Instant)
                        write("#T(request, resp, now, now)", RuntimeTypes.Http.HttpCall)
                    }
                }
            }
        }
    }

    private fun renderOperation(operation: OperationShape, testCase: SmokeTestCase) {
        val operationSymbol = symbolProvider.toSymbol(model.getShape(operation.input.get()).get())

        writer.withBlock(".use { client ->", "}") {
            withBlock("client.#L(", ")", operation.defaultName()) {
                withBlock("#L {", "}", operationSymbol) {
                    testCase.params.get().members.forEach { member ->
                        write("#L = #L", member.key.value.toCamelCase(), member.value.format())
                    }
                }
            }
        }
    }

    private fun renderCatchBlock(testCase: SmokeTestCase) {
        val successCriterion = RuntimeTypes.HttpClient.Interceptors.SmokeTestsSuccessException
        val failureCriterion = getFailureCriterion(testCase)
        val expected = if (testCase.expectation.isFailure) {
            failureCriterion
        } else {
            successCriterion
        }

        writer.write("val success = e is #L", expected)
        writer.write("val status = if (success) \"ok\" else \"not ok\"")
        printTestResult(
            sdkId.filter { !it.isWhitespace() },
            testCase.id,
            testCase.expectation.isFailure,
            writer,
        )
        writer.write("if (!success) exitCode = 1")
    }

    /**
     * Tries to get the specific exception required in the failure criterion of a test.
     * If no specific exception is required we default to the generic smoke tests failure exception.
     */
    private fun getFailureCriterion(testCase: SmokeTestCase): Symbol =
        testCase.expectation.failure.getOrNull()?.errorId?.getOrNull()?.let {
            symbolProvider.toSymbol(model.getShape(it).get())
        } ?: RuntimeTypes.HttpClient.Interceptors.SmokeTestsFailureException

    /**
     * Renders print statement for smoke test result in accordance to design doc & test anything protocol (TAP)
     */
    private fun printTestResult(
        service: String,
        testCase: String,
        errorExpected: Boolean,
        writer: KotlinWriter,
        statusOverride: String? = null,
        directive: String? = "",
    ) {
        val expectation = if (errorExpected) "error expected from service" else "no error expected from service"
        val status = statusOverride ?: "\$status"
        val testResult = "$status $service $testCase - $expectation $directive"
        writer.write("println(#S)", testResult)
    }
}

/**
 * Derives a function name for a [SmokeTestCase]
 */
private val SmokeTestCase.functionName: String
    get() = this.id.toCamelCase()
