package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.util.format
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.smoketests.traits.SmokeTestCase
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.jvm.optionals.getOrNull

object SmokeTestsRunner : SectionId
object SmokeTestAdditionalEnvVars : SectionId
object SmokeTestDefaultConfig: SectionId
object SmokeTestRegionDefault: SectionId
object SmokeTestHttpEngineOverride: SectionId

const val SKIP_TAGS = "AWS_SMOKE_TEST_SKIP_TAGS"
const val SERVICE_FILTER = "AWS_SMOKE_TEST_SERVICE_IDS"

/**
 * Renders smoke tests runner for a service
 */
class SmokeTestsRunnerGenerator(
    private val writer: KotlinWriter,
    ctx: CodegenContext,
) {
    private val model = ctx.model
    private val sdkId = ctx.settings.sdkId
    private val symbolProvider = ctx.symbolProvider
    private val service = symbolProvider.toSymbol(model.expectShape(ctx.settings.service))
    private val operations = ctx.model.topDownOperations(ctx.settings.service).filter { it.hasTrait<SmokeTestsTrait>() }

    internal fun render() {
        writer.declareSection(SmokeTestsRunner) {
            write("import kotlin.system.exitProcess")
            write("")
            write("private var exitCode = 0")
            write("private val skipTags = System.getenv(#S)?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()", SKIP_TAGS, ",")
            write("private val serviceFilter = System.getenv(#S)?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()", SERVICE_FILTER, ",")
            declareSection(SmokeTestAdditionalEnvVars)
            write("")
            withBlock("public suspend fun main() {", "}") {
                renderFunctionCalls()
                write("exitProcess(exitCode)")
            }
            write("")
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
                writer.write("")
            }
        }
    }

    private fun renderFunction(operation: OperationShape, testCase: SmokeTestCase) {
        writer.withBlock("private suspend fun ${testCase.functionName}() {", "}") {
            write("val tags = setOf<String>(${testCase.tags.joinToString(",") { it.dq()} })")
            writer.withBlock("if ((serviceFilter.isNotEmpty() && #S !in serviceFilter) || tags.any { it in skipTags }) {", "}", sdkId) {
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
            write("")
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
            if (testCase.vendorParams.isPresent) {
                testCase.vendorParams.get().members.forEach { vendorParam ->
                    if (vendorParam.key.value == "region") {
                        writeInline("#L = ", vendorParam.key.value.toCamelCase())
                        declareSection(SmokeTestRegionDefault)
                        write("#L", vendorParam.value.format())
                    } else {
                        write("#L = #L", vendorParam.key.value.toCamelCase(), vendorParam.value.format())
                    }
                }
            } else {
                declareSection(SmokeTestDefaultConfig)
            }
            val expectingSpecificError = testCase.expectation.failure.getOrNull()?.errorId?.getOrNull() != null
            if (!expectingSpecificError) {
                write("interceptors.add(#T())", RuntimeTypes.HttpClient.Interceptors.SmokeTestsInterceptor)
            }
            declareSection(SmokeTestHttpEngineOverride)
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
        val expected = if (testCase.expectation.isFailure) {
            getFailureCriterion(testCase)
        } else {
            RuntimeTypes.HttpClient.Interceptors.SmokeTestsSuccessException
        }

        writer.write("val success = e is #T", expected)
        writer.write("val status = if (success) #S else #S", "ok", "not ok")
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
