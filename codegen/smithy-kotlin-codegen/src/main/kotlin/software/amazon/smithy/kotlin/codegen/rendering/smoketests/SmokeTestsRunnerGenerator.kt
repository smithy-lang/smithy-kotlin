package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParametersGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointProviderGenerator
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.Param.ParamName
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.Param.ParamShape
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.Param.Parameter
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestUriValue.EndpointParameters
import software.amazon.smithy.kotlin.codegen.rendering.smoketests.SmokeTestUriValue.EndpointProvider
import software.amazon.smithy.kotlin.codegen.rendering.util.format
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.kotlin.codegen.utils.topDownOperations
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.smoketests.traits.SmokeTestCase
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.jvm.optionals.getOrNull

// TODO: Organize all these section IDs
object SmokeTestsRunner : SectionId
object SmokeTestAdditionalEnvVars : SectionId
object SmokeTestDefaultConfig : SectionId
object SmokeTestRegionDefault : SectionId
object SmokeTestUseDualStackKey : SectionId
object SmokeTestSigv4aRegionSetKey : SectionId
object SmokeTestSigv4aRegionSetValue : SectionId
object SmokeTestAccountIdBasedRoutingKey : SectionId
object SmokeTestAccountIdBasedRoutingValue : SectionId
object SmokeTestHttpEngineOverride : SectionId
object SmokeTestUseAccelerateKey : SectionId
object SmokeTestUseMultiRegionAccessPointsKey : SectionId
object SmokeTestUseMultiRegionAccessPointsValue : SectionId
object SmokeTestUseGlobalEndpoint : SectionId
object SmokeTestUriKey : SectionId
object SmokeTestUriValue : SectionId {
    val EndpointProvider: SectionKey<Symbol> = SectionKey("EndpointProvider")
    val EndpointParameters: SectionKey<Symbol> = SectionKey("EndpointParameters")
}

object Param : SectionId {
    val ParamShape: SectionKey<Shape?> = SectionKey("SmokeTestsOperationParamKey2") // TODO: Rename section keys
    val Parameter: SectionKey<Node> = SectionKey("SmokeTestsOperationParamKey3")
    val ParamName: SectionKey<String> = SectionKey("SmokeTestsOperationParamKey4")
}

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
    private val settings = ctx.settings
    private val sdkId = settings.sdkId
    private val symbolProvider = ctx.symbolProvider
    private val service = symbolProvider.toSymbol(model.expectShape(settings.service))
    private val operations = model.topDownOperations(settings.service).filter { it.hasTrait<SmokeTestsTrait>() }

    internal fun render() {
        writer.declareSection(SmokeTestsRunner) {
            write("private var exitCode = 0")
            write(
                "private val skipTags = #T.System.getenv(#S)?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
                RuntimeTypes.Core.Utils.PlatformProvider,
                SKIP_TAGS,
                ",",
            )
            write(
                "private val serviceFilter = #T.System.getenv(#S)?.let { it.split(#S).map { it.trim() }.toSet() } ?: emptySet()",
                RuntimeTypes.Core.Utils.PlatformProvider,
                SERVICE_FILTER,
                ",",
            )
            declareSection(SmokeTestAdditionalEnvVars)
            write("")
            withBlock("public suspend fun main() {", "}") {
                renderFunctionCalls()
                write("#T(exitCode)", RuntimeTypes.Core.SmokeTests.exitProcess)
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
            withBlock("catch (exception: Exception) {", "}") {
                renderCatchBlock(testCase)
            }
        }
    }

    private fun renderClient(testCase: SmokeTestCase) {
        writer.withInlineBlock("#L {", "}", service) {
            if (testCase.vendorParams.isPresent) {
                testCase.vendorParams.get().members.forEach { vendorParam ->
                    // TODO: Fix this to only use two sections
                    when (vendorParam.key.value) {
                        "region" -> {
                            writeInline("#L = ", vendorParam.key.value.toCamelCase())
                            declareSection(SmokeTestRegionDefault)
                            write("#L", vendorParam.value.format())
                        }
                        "sigv4aRegionSet" -> {
                            declareSection(SmokeTestSigv4aRegionSetKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            writeInline(" = ")
                            declareSection(SmokeTestSigv4aRegionSetValue) {
                                write("#L", vendorParam.value.format())
                            }
                        }
                        "uri" -> {
                            declareSection(SmokeTestUriKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            writeInline(" = ")
                            declareSection(
                                SmokeTestUriValue,
                                mapOf(
                                    EndpointProvider to EndpointProviderGenerator.getSymbol(settings),
                                    EndpointParameters to EndpointParametersGenerator.getSymbol(settings),
                                ),
                            ) {
                                write("#L", vendorParam.value.format())
                            }
                        }
                        "useDualstack" -> {
                            declareSection(SmokeTestUseDualStackKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            write(" = #L", vendorParam.value.format())
                        }
                        "useAccountIdRouting" -> {
                            declareSection(SmokeTestAccountIdBasedRoutingKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            writeInline(" = ")
                            declareSection(SmokeTestAccountIdBasedRoutingValue) {
                                write("#L", vendorParam.value.format())
                            }
                        }
                        "useAccelerate" -> {
                            declareSection(SmokeTestUseAccelerateKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            write(" = #L", vendorParam.value.format())
                        }
                        "useMultiRegionAccessPoints" -> {
                            declareSection(SmokeTestUseMultiRegionAccessPointsKey) {
                                writeInline("#L", vendorParam.key.value.toCamelCase())
                            }
                            writeInline(" = ")
                            declareSection(SmokeTestUseMultiRegionAccessPointsValue) {
                                write("#L", vendorParam.value.format())
                            }
                        }
                        "useGlobalEndpoint" -> {
                            declareSection(SmokeTestUseGlobalEndpoint) {
                                write("#L = #L", vendorParam.key.value.toCamelCase(), vendorParam.value.format())
                            }
                        }
                        else -> write("#L = #L", vendorParam.key.value.toCamelCase(), vendorParam.value.format())
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

        writer.withBlock(".#T { client ->", "}", RuntimeTypes.Core.IO.use) {
            withBlock("client.#L(", ")", operation.defaultName()) {
                withBlock("#L {", "}", operationSymbol) {
                    renderOperationParameters(operation, testCase)
                }
            }
        }
    }

    private fun renderOperationParameters(operation: OperationShape, testCase: SmokeTestCase) {
        if (!testCase.operationParametersArePresent) return

        val paramsToShapes = mapOperationParametersToModeledShapes(operation)

        testCase.operationParameters.forEach { param ->
            val name = param.key.value.toCamelCase()
            val value = param.value.format()

            writer.writeInline("#S = ", name)
            writer.declareSection(
                Param,
                mapOf(
                    ParamName to name,
                    ParamShape to paramsToShapes[name],
                    Parameter to param.value,
                ),
            ) {
                writer.write("#L", value )
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
            write("#T(exception)", RuntimeTypes.Core.SmokeTests.printExceptionStackTrace)
            write("exitCode = 1")
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
     * TODO:
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
     * TODO:
     */
    private val SmokeTestCase.operationParameters: Map<StringNode, Node>
        get() = this.params.get().members

    /**
     * TODO:
     */
    private val SmokeTestCase.operationParametersArePresent: Boolean
        get() = this.params.isPresent
}
