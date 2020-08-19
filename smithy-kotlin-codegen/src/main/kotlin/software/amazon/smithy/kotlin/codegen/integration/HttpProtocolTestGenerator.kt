/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen.integration

import java.util.*
import java.util.logging.Logger
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase
import software.amazon.smithy.protocoltests.traits.HttpRequestTestsTrait
import software.amazon.smithy.protocoltests.traits.HttpResponseTestsTrait

/**
 * Generates protocol unit tests for the HTTP protocol from smithy models.
 */
class HttpProtocolTestGenerator(
    private val ctx: ProtocolGenerator.GenerationContext,
    private val requestTestBuilder: HttpProtocolUnitTestRequestGenerator.Builder,
    private val responseTestBuilder: HttpProtocolUnitTestResponseGenerator.Builder,
    // list of test ID's to ignore/skip
    private val testsToIgnore: Set<String> = setOf()
) {
    private val LOGGER = Logger.getLogger(javaClass.name)

    /**
     * Generates the API HTTP protocol tests defined in the smithy model.
     */
    fun generateProtocolTests() {
        val operationIndex: OperationIndex = ctx.model.getKnowledge(OperationIndex::class.java)
        val topDownIndex: TopDownIndex = ctx.model.getKnowledge(TopDownIndex::class.java)
        val serviceSymbol = ctx.symbolProvider.toSymbol(ctx.service)

        for (operation in TreeSet(topDownIndex.getContainedOperations(ctx.service).filterNot(::serverOnly))) {

            // 1. Generate test cases for each request.
            operation.getTrait(HttpRequestTestsTrait::class.java)
                .ifPresent { trait: HttpRequestTestsTrait ->
                    val testCases = filterProtocolTestCases(trait.testCases)
                    if (testCases.isEmpty()) {
                        return@ifPresent
                    }

                    val testClassName = "${operation.id.name.capitalize()}RequestTest"
                    val testFilename = "$testClassName.kt"
                    ctx.delegator.useTestFileWriter(testFilename, ctx.settings.moduleName) { writer ->
                        LOGGER.fine("Generating request protocol test cases for ${operation.id}")

                        // import package.models.*
                        writer.addImport("${ctx.settings.moduleName}.model", "*", "")

                        requestTestBuilder
                            .writer(writer)
                            .model(ctx.model)
                            .symbolProvider(ctx.symbolProvider)
                            .operation(operation)
                            .serviceName(serviceSymbol.name)
                            .testCases(testCases)
                            .build()
                            .renderTestClass(testClassName)
                    }
                }

            // 2. Generate test cases for each response.
            operation.getTrait(HttpResponseTestsTrait::class.java)
                .ifPresent { trait: HttpResponseTestsTrait ->
                    val testCases = filterProtocolTestCases(trait.testCases)
                    if (testCases.isEmpty()) {
                        return@ifPresent
                    }

                    val testClassName = "${operation.id.name.capitalize()}ResponseTest"
                    val testFilename = "$testClassName.kt"
                    ctx.delegator.useTestFileWriter(testFilename, ctx.settings.moduleName) { writer ->
                        LOGGER.fine("Generating response protocol test cases for ${operation.id}")

                        writer.addImport("${ctx.settings.moduleName}.model", "*", "")
                        responseTestBuilder
                            .writer(writer)
                            .model(ctx.model)
                            .symbolProvider(ctx.symbolProvider)
                            .operation(operation)
                            .serviceName(serviceSymbol.name)
                            .testCases(testCases)
                            .build()
                            .renderTestClass(testClassName)
                    }
                }

            // 3. Generate test cases for each error on each operation.
            for (error in operationIndex.getErrors(operation).filterNot(::serverOnly)) {
                error.getTrait(HttpResponseTestsTrait::class.java)
                    .ifPresent { trait: HttpResponseTestsTrait ->
                        val testCases = filterProtocolTestCases(trait.testCases)
                        if (testCases.isEmpty()) {
                            return@ifPresent
                        }
                        // TODO - generate response error tests
                    }
            }
        }
    }

    private fun <T : HttpMessageTestCase> filterProtocolTestCases(testCases: List<T>): List<T> = testCases.filter {
        it.protocol == ctx.protocol && it.id !in testsToIgnore
    }
}

private fun serverOnly(shape: Shape): Boolean = shape.hasTag("server-only")
