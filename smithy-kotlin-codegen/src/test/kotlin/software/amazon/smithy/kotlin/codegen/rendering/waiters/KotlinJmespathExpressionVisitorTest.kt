/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.createSymbolProvider
import software.amazon.smithy.kotlin.codegen.test.stripCodegenPrefix
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinJmespathExpressionVisitorTest {
    val model = loadModelFromResource("waiter-tests.smithy")
    val provider = KotlinCodegenPlugin.createSymbolProvider(model)
    val inputShape = model.expectShape<StructureShape>(ShapeId.from("${TestModelDefault.NAMESPACE}#DescribeFooInput"))
    val inputSymbol = provider.toSymbol(inputShape)
    val outputShape = model.expectShape<StructureShape>(ShapeId.from("${TestModelDefault.NAMESPACE}#DescribeFooOutput"))
    val outputSymbol = provider.toSymbol(outputShape)

    @Test
    fun testRootFieldExpression() {
        testVisit(
            false,
            "name",
            "name",
            "val name = it.name",
        )
    }

    @Test
    fun testSubExpressions() {
        testVisit(
            false,
            "foo.bar.baz",
            "baz",
            "val foo = it.foo",
            "val bar = foo?.bar",
            "val baz = bar?.baz",
        )
    }

    @Test
    fun testInputFieldExpression() {
        testVisit(
            true,
            "input.id",
            "id",
            "val input = it.input",
            "val id = input?.id",
        )
    }

    @Test
    fun testOutputFieldExpression() {
        testVisit(
            true,
            "output.name",
            "name",
            "val output = it.output",
            "val name = output?.name",
        )
    }

    private fun testVisit(includeInput: Boolean, path: String, expectedActualName: String, vararg vals: String) {
        val visitor = KotlinJmespathExpressionVisitor(
            includeInput,
            model,
            provider,
            inputShape,
            inputSymbol,
            outputShape,
            outputSymbol
        )

        val expression = JmespathExpression.parse(path)
        expression.accept(visitor)

        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val actualActualName = visitor.renderActual(writer)

        assertEquals(expectedActualName, actualActualName)

        val codegen = writer.toString().stripCodegenPrefix(TestModelDefault.NAMESPACE)
        assertEquals(vals.joinToString("\n"), codegen)
    }
}
