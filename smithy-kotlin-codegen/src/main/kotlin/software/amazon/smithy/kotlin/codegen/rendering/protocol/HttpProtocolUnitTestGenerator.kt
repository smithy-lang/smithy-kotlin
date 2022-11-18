/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase

/**
 * Abstract base implementation for protocol test generators to extend in order to generate HttpMessageTestCase
 * specific protocol tests.
 *
 * @param T Specific HttpMessageTestCase the protocol test generator is for.
 */
abstract class HttpProtocolUnitTestGenerator<T : HttpMessageTestCase>
protected constructor(builder: Builder<T>) {

    protected val ctx: ProtocolGenerator.GenerationContext = requireNotNull(builder.ctx) { "protocol generator ctx is required" }
    protected val symbolProvider: SymbolProvider = requireNotNull(builder.symbolProvider) { "symbol provider is required" }
    protected val model: Model = requireNotNull(builder.model) { "model is required" }
    protected val testCases: List<T> = requireNotNull(builder.testCases) { "list of test cases is required" }
    protected val operation: OperationShape = requireNotNull(builder.operation) { "operation shape is required" }
    protected val writer: KotlinWriter = requireNotNull(builder.writer) { "writer is required" }
    protected val serviceShape: ServiceShape = requireNotNull(builder.service) { "service shape is required" }

    protected val idempotentFieldsInModel: Boolean by lazy {
        operation.input.isPresent &&
            model.expectShape(operation.input.get()).members().any { it.hasTrait(IdempotencyTokenTrait.ID.name) }
    }

    /**
     * Render a test class and unit tests for the specified [testCases]
     */
    fun renderTestClass(testClassName: String) {
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "Test")
        writer.dependencies.addAll(KotlinDependency.KOTLIN_TEST_JUNIT5.dependencies)
        writer.dependencies.addAll(KotlinDependency.JUNIT_JUPITER_ENGINE.dependencies)

        writer.write("")
            .openBlock("class $testClassName {")
            .call {
                for (test in testCases) {
                    renderTestFunction(test)
                }
            }
            .closeBlock("}")
    }

    protected open fun openTestFunctionBlock(): String = "{"

    /**
     * Write a single unit test function using the given [writer]
     */
    private fun renderTestFunction(test: T) {
        test.documentation.ifPresent {
            writer.dokka(it)
        }

        writer.write("@Test")
            .openBlock("fun `${test.id}`() ${openTestFunctionBlock()}")
            .call { renderTestBody(test) }
            .closeBlock("}")
    }

    /**
     * Render the body of a unit test
     */
    protected abstract fun renderTestBody(test: T)

    abstract class Builder<T : HttpMessageTestCase> {
        var ctx: ProtocolGenerator.GenerationContext? = null
        var symbolProvider: SymbolProvider? = null
        var model: Model? = null
        var testCases: List<T>? = null
        var operation: OperationShape? = null
        var writer: KotlinWriter? = null
        var service: ServiceShape? = null

        fun ctx(ctx: ProtocolGenerator.GenerationContext): Builder<T> = apply { this.ctx = ctx }
        fun symbolProvider(provider: SymbolProvider): Builder<T> = apply { this.symbolProvider = provider }
        fun model(model: Model): Builder<T> = apply { this.model = model }
        fun testCases(testCases: List<T>): Builder<T> = apply { this.testCases = testCases }
        fun operation(operation: OperationShape): Builder<T> = apply { this.operation = operation }
        fun writer(writer: KotlinWriter): Builder<T> = apply { this.writer = writer }
        fun service(service: ServiceShape): Builder<T> = apply { this.service = service }
        abstract fun build(): HttpProtocolUnitTestGenerator<T>
    }
}
