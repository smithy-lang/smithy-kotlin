/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol.pt

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.closeAndOpenBlock
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.pt.ProtocolTestErrorGenerator
import software.amazon.smithy.kotlin.codegen.pt.ProtocolTestRequestGenerator
import software.amazon.smithy.kotlin.codegen.pt.ProtocolTestResponseGenerator
import software.amazon.smithy.kotlin.codegen.pt.ProtocolTestsUtils
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.TestContainmentMode
import software.amazon.smithy.kotlin.codegen.rendering.protocol.TestMemberDelta
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.protocoltests.traits.AppliesTo
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait
import java.util.*
import java.util.logging.Logger

/**
 * Generates protocol unit tests for the HTTP protocol from smithy models.
 */
class ProtocolTestGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val requestTestBuilder: ProtocolTestRequestGenerator.Builder,
    private val responseTestBuilder: ProtocolTestResponseGenerator.Builder,
    private val errorTestBuilder: ProtocolTestErrorGenerator.Builder,
    // list of test ID's to ignore/skip
    private val testDelta: TestMemberDelta = TestMemberDelta(setOf()),
) {
    private val logger = Logger.getLogger(javaClass.name)

    /**
     * Generates the API HTTP protocol tests defined in the smithy model.
     */
    fun generateProtocolTests() {
        val operationIndex: OperationIndex = OperationIndex.of(ctx.model)
        val topDownIndex: TopDownIndex = TopDownIndex.of(ctx.model)

        val classes = ArrayList<String>()
        for (operation in TreeSet(topDownIndex.getContainedOperations(ctx.service).filterNot(::serverOnly))) {
            // 1. Generate test cases for each request.
            generateRequestProtocolTests(operation, classes)

            // 2. Generate test cases for each response.
            generateResponseProtocolTests(operation, classes)

            // 3. Generate test cases for each error on each operation.
            generateErrorProtocolTests(operationIndex, operation, classes)
        }

        generateProtocolTestsRunner(classes)
    }

    private fun <T : HttpMessageTestCase> isTestCaseAllowedForRunMode(test: T): Boolean = when (testDelta.runMode) {
        TestContainmentMode.EXCLUDE_TESTS -> test.protocol == ctx.protocol && test.id !in testDelta.members
        TestContainmentMode.RUN_TESTS -> test.protocol == ctx.protocol && test.id in testDelta.members
    }

    private fun generateRequestProtocolTests(operation: OperationShape, classes: MutableList<String>) {
        // 1. Generate test cases for each request.
        val requestTests = operation.getTrait<HttpRequestTestsTrait>()
            ?.getTestCasesFor(AppliesTo.CLIENT)
            ?.filter(::isTestCaseAllowedForRunMode)

        if (requestTests?.isEmpty() != false) {
            return
        }
        requestTests.let { testCases ->
            val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
            val testClassName = "${testOperationName}RequestTest"
            val testFilename = "$testClassName.kt"
            classes.add(testClassName)
            ctx.delegator.useFileWriter(testFilename, ctx.settings.pkg.name + ".pt") { writer ->
                logger.fine("Generating request protocol test cases for ${operation.id}")
                requestTestBuilder
                    .ctx(ctx)
                    .writer(writer)
                    .model(ctx.model)
                    .symbolProvider(ctx.symbolProvider)
                    .operation(operation)
                    .service(ctx.service)
                    .testCases(testCases)
                    .build()
                    .renderTestClass(testClassName)
            }
        }
    }

    private fun generateResponseProtocolTests(operation: OperationShape, classes: MutableList<String>) {
        val responseTests = operation.getTrait<HttpResponseTestsTrait>()
            ?.getTestCasesFor(AppliesTo.CLIENT)
            ?.filter(::isTestCaseAllowedForRunMode)

        if (responseTests?.isEmpty() != false) {
            return
        }
        responseTests.let { testCases ->
            val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
            val testClassName = "${testOperationName}ResponseTest"
            val testFilename = "$testClassName.kt"
            classes.add(testClassName)
            ctx.delegator.useFileWriter(testFilename, ctx.settings.pkg.name + ".pt") { writer ->
                logger.fine("Generating response protocol test cases for ${operation.id}")
                responseTestBuilder
                    .ctx(ctx)
                    .writer(writer)
                    .model(ctx.model)
                    .symbolProvider(ctx.symbolProvider)
                    .operation(operation)
                    .service(ctx.service)
                    .testCases(testCases)
                    .build()
                    .renderTestClass(testClassName)
            }
        }
    }

    private fun generateErrorProtocolTests(
        operationIndex: OperationIndex,
        operation: OperationShape,
        classes: MutableList<String>,
    ) {
        for (error in operationIndex.getErrors(operation).filterNot(::serverOnly)) {
            val errorTests = error.getTrait<HttpResponseTestsTrait>()
                ?.getTestCasesFor(AppliesTo.CLIENT)
                ?.filter(::isTestCaseAllowedForRunMode)
            if (errorTests?.isEmpty() != false) {
                return
            }
            errorTests.let { testCases ->
                // use operation name as filename
                val opName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                val testFilename = "${opName}ErrorTest.kt"
                // multiple error (tests) may be associated with a single operation,
                // use the operation name + error name as the class name
                val testClassName = "${opName}${error.defaultName(ctx.service)}Test"
                classes.add(testClassName)
                ctx.delegator.useFileWriter(testFilename, ctx.settings.pkg.name + ".pt") { writer ->
                    logger.fine("Generating error protocol test cases for ${operation.id}")
                    errorTestBuilder
                        .error(error)
                        .ctx(ctx)
                        .writer(writer)
                        .model(ctx.model)
                        .symbolProvider(ctx.symbolProvider)
                        .operation(operation)
                        .service(ctx.service)
                        .testCases(testCases)
                        .build()
                        .renderTestClass(testClassName)
                }
            }
        }
    }

    private fun generateProtocolTestsRunner(classes: List<String>) {
        val testClassName = "Runner"
        val testFilename = "$testClassName.kt"
        ctx.delegator.useFileWriter(testFilename, ctx.settings.pkg.name + ".pt") { writer ->
            logger.fine("Generating protocol test runner")
            writer.openBlock("public fun main() {")
                .write("val results = ArrayList<#T>()", ProtocolTestsUtils.TestResult)
            for (testClass in classes) {
                writer.write("#L(results).runAll()", testClass)
            }
            writer.write("#T(#S, #S, results)", ProtocolTestsUtils.writeResults, ctx.service.id, ctx.protocol)
            writer.closeBlock("}")
        }
    }
}

fun renderTestCasePrelude(writer: KotlinWriter, testId: String, type: String) {
    writer.write("val res = #T(#S, #T.#L)", ProtocolTestsUtils.TestResult, testId, ProtocolTestsUtils.TestType, type)
        .write("results.add(res)")
        .openBlock("try {")
}

fun renderTestCaseEpilogue(writer: KotlinWriter) {
    writer.closeAndOpenBlock("} catch (ex: AssertionError) {")
        .write("res.result = #T.FAILED", ProtocolTestsUtils.Result)
        .write("val sw = java.io.StringWriter()")
        .write("ex.printStackTrace(java.io.PrintWriter(sw))")
        .write("res.log = sw.toString()")
        .closeAndOpenBlock("} catch (ex: Exception) {")
        .write("res.result = #T.ERRORED", ProtocolTestsUtils.Result)
        .write("val sw = java.io.StringWriter()")
        .write("ex.printStackTrace(java.io.PrintWriter(sw))")
        .write("res.log = sw.toString()")
        .closeBlock("}")
}

private fun serverOnly(shape: Shape): Boolean = shape.hasTag("server-only")
