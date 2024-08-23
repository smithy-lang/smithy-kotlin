package software.amazon.smithy.kotlin.codegen.rendering.smoketests

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.rendering.util.render
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.smoketests.traits.SmokeTestCase
import software.amazon.smithy.smoketests.traits.SmokeTestsTrait
import kotlin.jvm.optionals.getOrNull

/**
 * Renders smoke tests runner for a service
 */
class SmokeTestsRunnerGenerator(
    private val writer: KotlinWriter,
    private val service: Symbol,
    private val operations: List<OperationShape>,
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val sdkId: String,
) {
    internal fun render() {
        writer.write("private var exitCode = 0")
        writer.write("private val regionOverride = System.getenv(\"AWS_SMOKE_TEST_REGION\")")
        writer.write("private val skipTags = System.getenv(\"AWS_SMOKE_TEST_SKIP_TAGS\")?.let { it.split(\",\").map { it.trim() }.toSet() }")
        writer.emptyLine()
        writer.withBlock("public suspend fun main() {", "}") {
            renderFunctionCalls()
            write("#L(exitCode)", RuntimeTypes.HttpClient.Interceptors.exitProcess)
        }
        writer.emptyLine()
        renderFunctions()
    }

    private fun renderFunctionCalls() {
        operations.forEach { operation ->
            operation.getTrait<SmokeTestsTrait>()?.testCases?.forEach { testCase ->
                writer.write("${testCase.id.toCamelCase()}()")
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
        writer.withBlock("private suspend fun ${testCase.id.toCamelCase()}() {", "}") {
            write("val tags = setOf<String>(${testCase.tags.joinToString(",") { it.dq()} })")
            write("if (skipTags != null && tags.any { it in skipTags }) return")
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
            if (testCase.vendorParams.isPresent) {
                testCase.vendorParams.get().members.forEach { vendorParam ->
                    if (vendorParam.key.value == "region") {
                        write("region = regionOverride ?: #L", vendorParam.value.render())
                    } else {
                        write("#L = #L", vendorParam.key.value.toCamelCase(), vendorParam.value.render())
                    }
                }
            } else {
                write("region = regionOverride")
            }
            val expectingSpecificError = testCase.expectation.failure.getOrNull()?.errorId?.getOrNull() != null
            write("interceptors.add(#T($expectingSpecificError))", RuntimeTypes.HttpClient.Interceptors.SmokeTestsInterceptor)
        }
        checkVendorParamsAreCorrect(testCase)
    }

    /**
     * Smithy IDL doesn't check that vendor params are found in vendor params shape so we have to check here.
     */
    private fun checkVendorParamsAreCorrect(testCase: SmokeTestCase) {
        if (testCase.vendorParamsShape.isPresent && testCase.vendorParams.isPresent) {
            val vendorParamsShape = model.getShape(testCase.vendorParamsShape.get()).get()
            val validVendorParams = vendorParamsShape.members().map { it.memberName }
            val vendorParams = testCase.vendorParams.get().members.map { it.key.value }

            vendorParams.forEach { vendorParam ->
                check(validVendorParams.contains(vendorParam)) {
                    "Smithy smoke test \"${testCase.id}\" contains invalid vendor param \"$vendorParam\", it was not found in vendor params shape \"${testCase.vendorParamsShape}\""
                }
            }
        }
    }

    private fun renderOperation(operation: OperationShape, testCase: SmokeTestCase) {
        val operationSymbol = symbolProvider.toSymbol(model.getShape(operation.input.get()).get())

        writer.addImport(operationSymbol)
        writer.withBlock(".use { client ->", "}") {
            withBlock("client.#L(", ")", operation.defaultName()) {
                withBlock("#L {", "}", operationSymbol.name) {
                    testCase.params.get().members.forEach { member ->
                        write("#L = #L", member.key.value.toCamelCase(), member.value.render())
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
     *
     * Some smoke tests model exceptions not found in the model, in that case we default to the generic smoke tests
     * failure exception.
     */
    private fun getFailureCriterion(testCase: SmokeTestCase): Symbol = testCase.expectation.failure.getOrNull()?.errorId?.let {
        try {
            symbolProvider.toSymbol(model.getShape(it.get()).get())
        } catch (e: Exception) {
            RuntimeTypes.HttpClient.Interceptors.SmokeTestsFailureException
        }
    } ?: RuntimeTypes.HttpClient.Interceptors.SmokeTestsFailureException

    /**
     * Renders print statement for smoke test result in accordance to design doc & test anything protocol (TAP)
     */
    private fun printTestResult(
        service: String,
        testCase: String,
        errorExpected: Boolean,
        writer: KotlinWriter,
    ) {
        val expectation = if (errorExpected) "error expected from service" else "no error expected from service"
        val testResult = "\$status $service $testCase - $expectation"
        writer.write("println(#S)", testResult)
    }
}
