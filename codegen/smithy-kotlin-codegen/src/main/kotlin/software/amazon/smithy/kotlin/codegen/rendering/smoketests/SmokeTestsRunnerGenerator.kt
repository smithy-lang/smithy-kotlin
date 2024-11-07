package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.isStringEnumShape
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParametersGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.stringToNumber
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.EndpointParams
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.EndpointProvider
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.Name
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestSectionIds.ClientConfig.Value
import software.amazon.smithy.kotlin.codegen.rendering.util.format
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.kotlin.codegen.utils.toPascalCase
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.node.*
import software.amazon.smithy.model.shapes.*
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
        writer.write("private var exitCode = 0")
        renderEnvironmentVariables()
        writer.declareSection(SmokeTestSectionIds.AdditionalEnvironmentVariables)
        writer.write("")
        writer.withBlock("public suspend fun main() {", "}") {
            renderFunctionCalls()
            write("#T(exitCode)", RuntimeTypes.Core.SmokeTests.exitProcess)
        }
        writer.write("")
        renderFunctions()
    }

    private fun renderEnvironmentVariables() {
        // Skip tags
        writer.writeInline(
            "private val skipTags = #T.System.getenv(",
            RuntimeTypes.Core.Utils.PlatformProvider,
        )
        writer.declareSection(SmokeTestSectionIds.SkipTags) {
            writer.writeInline("#S", SKIP_TAGS)
        }
        writer.write(
            ")?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
            ",",
        )

        // Service filter
        writer.writeInline(
            "private val serviceFilter = #T.System.getenv(",
            RuntimeTypes.Core.Utils.PlatformProvider,
        )
        writer.declareSection(SmokeTestSectionIds.ServiceFilter) {
            writer.writeInline("#S", SERVICE_FILTER)
        }
        writer.write(
            ")?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
            ",",
        )
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
            withBlock("catch (exception: Exception) {", "}") {
                renderCatchBlock(testCase)
            }
        }
    }

    private fun renderClient(testCase: SmokeTestCase) {
        writer.withInlineBlock("#L {", "}", service) {
            renderClientConfig(testCase)
        }
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

        testCase.clientConfig!!.forEach { config ->
            val name = config.key.value.toCamelCase()
            val value = config.value.format()

            writer.declareSection(
                SmokeTestSectionIds.ClientConfig,
                mapOf(
                    Name to name,
                    Value to value,
                    EndpointProvider to EndpointProviderGenerator.getSymbol(settings),
                    EndpointParams to EndpointParametersGenerator.getSymbol(settings),
                ),
            ) {
                writer.writeInline("#L = #L", name, value)
            }
        }
    }

    private fun renderOperation(operation: OperationShape, testCase: SmokeTestCase) {
        val operationSymbol = symbolProvider.toSymbol(model.getShape(operation.input.get()).get())

        writer.withBlock(".#T { client ->", "}", RuntimeTypes.Core.IO.use) {
            withBlock("client.#L(", ")", operation.defaultName()) {
                withBlock("#L {", "}", operationSymbol) {
                    renderOperationParameters(operation, testCase)
                }
            }
        }
    }

    private fun renderOperationParameters(operation: OperationShape, testCase: SmokeTestCase) {
        if (!testCase.hasOperationParameters) return

        val paramsToShapes = mapOperationParametersToModeledShapes(operation)

        testCase.operationParameters.forEach { param ->
            val paramName = param.key.value.toCamelCase()
            writer.writeInline("#L = ", paramName)
            val paramShape = paramsToShapes[paramName] ?: throw IllegalArgumentException("Unable to find shape for operation parameter '$paramName' in smoke test '${testCase.functionName}'.")
            renderOperationParameter(paramName, param.value, paramShape, testCase)
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
            write("#T(exception)", RuntimeTypes.Core.SmokeTests.printExceptionStackTrace)
            write("exitCode = 1")
        }
    }

    // Helpers
    /**
     * Renders a [SmokeTestCase] operation parameter
     */
    private fun renderOperationParameter(
        paramName: String,
        node: Node,
        shape: Shape,
        testCase: SmokeTestCase,
    ) {
        when {
            // String enum
            node is StringNode && shape.isStringEnumShape -> {
                val enumSymbol = symbolProvider.toSymbol(shape)
                val enumValue = node.value.toPascalCase()
                writer.write("#T.#L", enumSymbol, enumValue)
            }
            // Int enum
            node is NumberNode && shape is IntEnumShape -> {
                val enumSymbol = symbolProvider.toSymbol(shape)
                val enumValue = node.format()
                writer.write("#T.fromValue(#L.toInt())", enumSymbol, enumValue)
            }
            // Number
            node is NumberNode && shape is NumberShape -> writer.write("#L.#L", node.format(), stringToNumber(shape))
            // Object
            node is ObjectNode -> {
                val shapeSymbol = symbolProvider.toSymbol(shape)
                writer.withBlock("#T {", "}", shapeSymbol) {
                    node.members.forEach { member ->
                        val memberName = member.key.value.toCamelCase()
                        val memberShape = shape.allMembers[member.key.value] ?: throw IllegalArgumentException("Unable to find shape for operation parameter '$paramName' in smoke test '${testCase.functionName}'.")
                        writer.writeInline("#L = ", memberName)
                        renderOperationParameter(memberName, member.value, memberShape, testCase)
                    }
                }
            }
            // List
            node is ArrayNode && shape is CollectionShape -> {
                writer.withBlock("listOf(", ")") {
                    node.elements.forEach { element ->
                        renderOperationParameter(paramName, element, model.expectShape(shape.member.target), testCase)
                        writer.write(",")
                    }
                }
            }
            // Everything else
            else -> writer.write("#L", node.format())
        }
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

    /**
     * Maps an operations parameters to their shapes
     */
    private fun mapOperationParametersToModeledShapes(operation: OperationShape): Map<String, Shape> =
        model.getShape(operation.inputShape).get().allMembers.map { (key, value) ->
            key.toCamelCase() to model.getShape(value.target).get()
        }.toMap()

    /**
     * Derives a function name for a [SmokeTestCase]
     */
    private val SmokeTestCase.functionName: String
        get() = this.id.toCamelCase()

    /**
     * Get the operation parameters for a [SmokeTestCase]
     */
    private val SmokeTestCase.operationParameters: Map<StringNode, Node>
        get() = this.params.get().members

    /**
     * Checks if there are operation parameters for a [SmokeTestCase]
     */
    private val SmokeTestCase.hasOperationParameters: Boolean
        get() = this.params.isPresent

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
    private val SmokeTestCase.clientConfig: MutableMap<StringNode, Node>?
        get() = this.vendorParams.get().members

    // Constants
    private val model = ctx.model
    private val settings = ctx.settings
    private val sdkId = settings.sdkId
    private val symbolProvider = ctx.symbolProvider
    private val service = symbolProvider.toSymbol(model.expectShape(settings.service))
    private val operations = model.topDownOperations(settings.service).filter { it.hasTrait<SmokeTestsTrait>() }
}
