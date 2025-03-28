package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParametersGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.EndpointParams
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.EndpointProvider
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.Name
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.Value
import software.amazon.smithy.kotlin.codegen.rendering.util.format
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.smoketests.traits.SmokeTestCase
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.jvm.optionals.getOrNull

// Section IDs
object SmokeTestSectionIds {
    object AdditionalEnvironmentVariables : SectionId
    object DefaultClientConfig : SectionId
    object HttpEngineOverride : SectionId
    object ServiceFilter : SectionId
    object SkipTags : SectionId
    object SmokeTestsFile : SectionId
    object ClientConfig : SectionId {
        val Name: SectionKey<String> = SectionKey("aws.smithy.kotlin#SmokeTestClientConfigName")
        val Value: SectionKey<String> = SectionKey("aws.smithy.kotlin#SmokeTestClientConfigValue")
        val EndpointProvider: SectionKey<Symbol> = SectionKey("aws.smithy.kotlin#SmokeTestEndpointProvider")
        val EndpointParams: SectionKey<Symbol> = SectionKey("aws.smithy.kotlin#SmokeTestClientEndpointParams")
    }
}

/**
 * Env var for smoke test runners.
 * Should be a comma-delimited list of strings that correspond to tags on the test cases.
 * If a test case is tagged with one of the tags indicated by SMOKE_TEST_SKIP_TAGS, it MUST be skipped by the smoke test runner.
 */
const val SKIP_TAGS = "SMOKE_TEST_SKIP_TAGS"

/**
 * Env var for smoke test runners.
 * Should be a comma-separated list of service identifiers to test.
 */
const val SERVICE_FILTER = "SMOKE_TEST_SERVICE_IDS"

/**
 * Renders smoke tests runner for a service
 */
class SmokeTestsRunnerGenerator(
    private val writer: KotlinWriter,
    ctx: CodegenContext,
) {
    internal fun render() {
        writer.declareSection(SmokeTestSectionIds.SmokeTestsFile) {
            write("")

            withBlock("public suspend fun main() {", "}") {
                write("val success = SmokeTestRunner().runAllTests()")
                withBlock("if (!success) {", "}") {
                    write("#T(1)", RuntimeTypes.Core.SmokeTests.exitProcess)
                }
            }
            write("")
            renderRunnerClass()
        }
    }

    private fun renderRunnerClass() {
        writer.withBlock(
            "public class SmokeTestRunner(private val platform: #1T = #1T.System, private val printer: #2T = #3T) {",
            "}",
            RuntimeTypes.Core.Utils.PlatformProvider,
            KotlinTypes.Text.Appendable,
            RuntimeTypes.Core.SmokeTests.DefaultPrinter,
        ) {
            renderEnvironmentVariables()
            declareSection(SmokeTestSectionIds.AdditionalEnvironmentVariables)
            write("")

            withBlock("public suspend fun runAllTests(): Boolean =", "") {
                withBlock("listOf<suspend () -> Boolean>(", ")") {
                    renderFunctionReferences()
                }
                indent()
                write(".map { it() }")
                write(".none { !it }")
                dedent()
            }
            renderFunctions()
        }
    }

    private fun renderEnvironmentVariables() {
        // Skip tags
        writer.writeInline("private val skipTags = platform.getenv(")
        writer.declareSection(SmokeTestSectionIds.SkipTags) {
            writer.writeInline("#S", SKIP_TAGS)
        }
        writer.write(
            ")?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
            ",",
        )

        // Service filter
        writer.writeInline("private val serviceFilter = platform.getenv(")
        writer.declareSection(SmokeTestSectionIds.ServiceFilter) {
            writer.writeInline("#S", SERVICE_FILTER)
        }
        writer.write(
            ")?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
            ",",
        )
    }

    private fun renderFunctionReferences() {
        operations.forEach { operation ->
            operation.getTrait<SmokeTestsTrait>()?.testCases?.forEach { testCase ->
                writer.write("::${testCase.functionName},")
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
        writer.withBlock("private suspend fun ${testCase.functionName}(): Boolean {", "}") {
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
                writer.write("return true")
            }
            write("")
            withInlineBlock("return try {", "} ") {
                renderTestCase(operation, testCase)
            }
            withBlock("catch (exception: Exception) {", "}") {
                renderCatchBlock(testCase)
            }
        }
    }

    private fun renderTestCase(operation: OperationShape, testCase: SmokeTestCase) {
        writer.withBlock("#T {", "}", service) {
            renderClientConfig(testCase)
            closeAndOpenBlock("}.#T { client ->", RuntimeTypes.Core.IO.use)
            renderOperation(operation, testCase)
        }
        writer.write("")
        writer.write("error(#S)", "Unexpectedly completed smoke test operation without throwing exception")
    }

    private fun renderClientConfig(testCase: SmokeTestCase) {
        if (!testCase.expectingSpecificError) {
            writer.write("interceptors.add(#T())", RuntimeTypes.HttpClient.Interceptors.SmokeTestsInterceptor)
        }

        writer.declareSection(SmokeTestSectionIds.HttpEngineOverride)

        if (!testCase.hasClientConfig) {
            writer.declareSection(SmokeTestSectionIds.DefaultClientConfig)
            return
        }

        testCase.clientConfig.forEach { (name, unformattedValue) ->
            val value = unformattedValue.format()
            writer.declareSection(
                SmokeTestSectionIds.ClientConfig,
                mapOf(
                    Name to name,
                    Value to value,
                    EndpointProvider to EndpointProviderGenerator.getSymbol(settings),
                    EndpointParams to EndpointParametersGenerator.getSymbol(settings),
                ),
            ) {
                writer.write("#L = #L", name, value)
            }
        }
    }

    private fun renderOperation(operation: OperationShape, testCase: SmokeTestCase) {
        val inputParams = testCase.params.getOrNull()

        writer.writeInline("client.#L", operation.defaultName())

        if (inputParams == null) {
            writer.write("()")
        } else {
            writer.withBlock("(", ")") {
                val inputShape = model.expectShape(operation.input.get())
                ShapeValueGenerator(model, symbolProvider).instantiateShapeInline(writer, inputShape, inputParams)
            }
        }
    }

    private fun renderCatchBlock(testCase: SmokeTestCase) {
        val expectedException = if (testCase.expectation.isFailure) {
            getFailureCriterion(testCase)
        } else {
            RuntimeTypes.HttpClient.Interceptors.SmokeTestsSuccessException
        }

        writer.write("val success: Boolean = exception is #T", expectedException)
        writer.write("val status: String = if (success) #S else #S", "ok", "not ok")

        printTestResult(
            sdkId.filter { !it.isWhitespace() },
            testCase.id,
            testCase.expectation.isFailure,
            writer,
        )

        writer.withBlock("if (!success) {", "}") {
            write("printer.appendLine(exception.stackTraceToString().prependIndent(#S))", "#")
        }

        writer.write("")
        writer.write("success")
    }

    // Helpers
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
        writer.write("printer.appendLine(#S)", testResult)
    }

    /**
     * Derives a function name for a [SmokeTestCase]
     */
    private val SmokeTestCase.functionName: String
        get() = this.id.toCamelCase()

    /**
     * Check if a [SmokeTestCase] is expecting a specific error
     */
    private val SmokeTestCase.expectingSpecificError: Boolean
        get() = this.expectation.failure.getOrNull()?.errorId?.getOrNull() != null

    /**
     * Checks if a [SmokeTestCase] requires client configuration
     */
    private val SmokeTestCase.hasClientConfig: Boolean
        get() = this.vendorParams.isPresent

    /**
     * Get the client configuration required for a [SmokeTestCase]
     */
    private val SmokeTestCase.clientConfig: Map<String, Node>
        get() = vendorParams
            .getOrNull()
            ?.members
            ?.mapKeys { (key, _) -> key.value }
            .orEmpty()

    // Constants
    private val model = ctx.model
    private val settings = ctx.settings
    private val sdkId = settings.sdkId
    private val symbolProvider = ctx.symbolProvider
    private val service = symbolProvider.toSymbol(model.expectShape(settings.service))
    private val operations = model.topDownOperations(settings.service).filter { it.hasTrait<SmokeTestsTrait>() }
}
