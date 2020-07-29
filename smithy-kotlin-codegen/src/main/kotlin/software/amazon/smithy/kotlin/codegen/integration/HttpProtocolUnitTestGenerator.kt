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

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinDependency
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase

/**
 * Abstract base implementation for protocol test generators to extend in order to generate HttpMessageTestCase
 * specific protocol tests.
 *
 * @param T Specific HttpMessageTestCase the protocol test generator is for.
 */
abstract class HttpProtocolUnitTestGenerator<T : HttpMessageTestCase>
protected constructor(builder: Builder<T>) {

    protected val symbolProvider: SymbolProvider = builder.symbolProvider!!
    protected val model: Model = builder.model!!
    protected val testCases: List<T> = builder.testCases!!
    protected val operation: OperationShape = builder.operation!!
    protected val writer: KotlinWriter = builder.writer!!
    protected val serviceName: String = builder.serviceName!!

    /**
     * Render a test class and unit tests for the specified [testCases]
     */
    fun renderTestClass(testClassName: String) {
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "Test", "")
        writer.dependencies.addAll(KotlinDependency.KOTLIN_TEST.dependencies)
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
        var symbolProvider: SymbolProvider? = null
        var model: Model? = null
        var testCases: List<T>? = null
        var operation: OperationShape? = null
        var writer: KotlinWriter? = null
        var serviceName: String? = null

        fun symbolProvider(provider: SymbolProvider): Builder<T> = apply { this.symbolProvider = provider }
        fun model(model: Model): Builder<T> = apply { this.model = model }
        fun testCases(testCases: List<T>): Builder<T> = apply { this.testCases = testCases }
        fun operation(operation: OperationShape): Builder<T> = apply { this.operation = operation }
        fun writer(writer: KotlinWriter): Builder<T> = apply { this.writer = writer }
        fun serviceName(serviceName: String): Builder<T> = apply { this.serviceName = serviceName }
        abstract fun build(): HttpProtocolUnitTestGenerator<T>
    }
}
