/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.protocoltests.traits.AppliesTo
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait
import java.util.*
import java.util.logging.Logger

private const val SERDE_BENCHMARK_TAG = "serde-benchmark"

enum class TestContainmentMode {
    RUN_TESTS,
    EXCLUDE_TESTS,
}

/**
 * Specifies tests to add or subtract to the complete set.
 */
data class TestMemberDelta(val members: Set<String>, val runMode: TestContainmentMode = TestContainmentMode.EXCLUDE_TESTS)

/**
 * Generates protocol unit tests for the HTTP protocol from smithy models.
 */
class HttpProtocolTestGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val requestTestBuilder: HttpProtocolUnitTestRequestGenerator.Builder,
    private val responseTestBuilder: HttpProtocolUnitTestResponseGenerator.Builder,
    private val errorTestBuilder: HttpProtocolUnitTestErrorGenerator.Builder,
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

        for (operation in TreeSet(topDownIndex.getContainedOperations(ctx.service).filterNot(::serverOnly))) {
            // 1. Generate test cases for each request.
            val requestTests = operation.getTrait<HttpRequestTestsTrait>()
                ?.getTestCasesFor(AppliesTo.CLIENT)
                ?.filter(::isTestCaseAllowedForRunMode)

            requestTests?.let { testCases ->
                val (benchmarkCases, regularCases) = testCases.partition { SERDE_BENCHMARK_TAG in it.tags }

                // Generate regular protocol tests
                if (regularCases.isNotEmpty()) {
                    val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                    val testClassName = "${testOperationName}RequestTest"
                    val testFilename = "$testClassName.kt"
                    ctx.delegator.useTestFileWriter(testFilename, ctx.settings.pkg.name) { writer ->
                        logger.fine("Generating request protocol test cases for ${operation.id}")
                        writer.addImport("${ctx.settings.pkg.name}.model", "*")
                        requestTestBuilder
                            .ctx(ctx)
                            .writer(writer)
                            .model(ctx.model)
                            .symbolProvider(ctx.symbolProvider)
                            .operation(operation)
                            .service(ctx.service)
                            .testCases(regularCases)
                            .build()
                            .renderTestClass(testClassName)
                    }
                }

                // Generate serde benchmark classes
                if (benchmarkCases.isNotEmpty()) {
                    val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                    val benchmarkClassName = "${testOperationName}SerializationBenchmark"
                    val benchmarkFilename = "$benchmarkClassName.kt"
                    ctx.delegator.useTestFileWriter(benchmarkFilename, ctx.settings.pkg.name) { writer ->
                        logger.fine("Generating serialization benchmark for ${operation.id}")
                        writer.addImport("${ctx.settings.pkg.name}.model", "*")
                        HttpProtocolSerdeBenchmarkGenerator(
                            ctx, writer, ctx.model, ctx.symbolProvider, operation, ctx.service,
                        ).renderRequestBenchmarkClass(benchmarkClassName, benchmarkCases)
                    }
                }
            }

            // 2. Generate test cases for each response.
            val responseTests = operation.getTrait<HttpResponseTestsTrait>()
                ?.getTestCasesFor(AppliesTo.CLIENT)
                ?.filter(::isTestCaseAllowedForRunMode)

            responseTests?.let { testCases ->
                val (benchmarkCases, regularCases) = testCases.partition { SERDE_BENCHMARK_TAG in it.tags }

                // Generate regular protocol tests
                if (regularCases.isNotEmpty()) {
                    val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                    val testClassName = "${testOperationName}ResponseTest"
                    val testFilename = "$testClassName.kt"
                    ctx.delegator.useTestFileWriter(testFilename, ctx.settings.pkg.name) { writer ->
                        logger.fine("Generating response protocol test cases for ${operation.id}")
                        writer.addImport("${ctx.settings.pkg.name}.model", "*")
                        responseTestBuilder
                            .ctx(ctx)
                            .writer(writer)
                            .model(ctx.model)
                            .symbolProvider(ctx.symbolProvider)
                            .operation(operation)
                            .service(ctx.service)
                            .testCases(regularCases)
                            .build()
                            .renderTestClass(testClassName)
                    }
                }

                // Generate serde benchmark classes
                if (benchmarkCases.isNotEmpty()) {
                    val testOperationName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                    val benchmarkClassName = "${testOperationName}DeserializationBenchmark"
                    val benchmarkFilename = "$benchmarkClassName.kt"
                    ctx.delegator.useTestFileWriter(benchmarkFilename, ctx.settings.pkg.name) { writer ->
                        logger.fine("Generating deserialization benchmark for ${operation.id}")
                        writer.addImport("${ctx.settings.pkg.name}.model", "*")
                        HttpProtocolSerdeBenchmarkGenerator(
                            ctx, writer, ctx.model, ctx.symbolProvider, operation, ctx.service,
                        ).renderResponseBenchmarkClass(benchmarkClassName, benchmarkCases)
                    }
                }
            }

            // 3. Generate test cases for each error on each operation.
            for (error in operationIndex.getErrors(operation).filterNot(::serverOnly)) {
                val errorTests = error.getTrait<HttpResponseTestsTrait>()
                    ?.getTestCasesFor(AppliesTo.CLIENT)
                    ?.filter(::isTestCaseAllowedForRunMode)

                errorTests?.let { testCases ->
                    // use operation name as filename
                    val opName = operation.id.name.replaceFirstChar { c -> c.uppercaseChar() }
                    val testFilename = "${opName}ErrorTest.kt"
                    // multiple error (tests) may be associated with a single operation,
                    // use the operation name + error name as the class name
                    val testClassName = "${opName}${error.defaultName(ctx.service)}Test"
                    ctx.delegator.useTestFileWriter(testFilename, ctx.settings.pkg.name) { writer ->
                        logger.fine("Generating error protocol test cases for ${operation.id}")

                        writer.addImport("${ctx.settings.pkg.name}.model", "*")
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
    }

    private fun <T : HttpMessageTestCase> isTestCaseAllowedForRunMode(test: T): Boolean = when (testDelta.runMode) {
        TestContainmentMode.EXCLUDE_TESTS -> test.protocol == ctx.protocol && test.id !in testDelta.members
        TestContainmentMode.RUN_TESTS -> test.protocol == ctx.protocol && test.id in testDelta.members
    }
}

private fun serverOnly(shape: Shape): Boolean = shape.hasTag("server-only")
