/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.waiters

import io.kotest.matchers.string.shouldEndWith
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.jmespath.JmespathExpression
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.stripCodegenPrefix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class KotlinJmespathExpressionVisitorTest {
    @Test
    fun testAndExpression() {
        assertUnimplemented("foo && bar")
    }

    @Test
    fun testComparatorExpression_Equality() {
        assertVisit(
            "foo == bar",
            "comparison",
            "val foo = it?.foo",
            "val bar = it?.bar",
            "val comparison = foo == bar",
        )
    }

    @Test
    fun testComparatorExpression_EqualityWithString() {
        assertVisit(
            "`\"foo\"` == bar",
            "comparison",
            "val string = \"foo\"",
            "val bar = it?.bar",
            "val comparison = string == bar?.toString()",
        )

        assertVisit(
            "foo == `\"bar\"`",
            "comparison",
            "val foo = it?.foo",
            "val string = \"bar\"",
            "val comparison = foo?.toString() == string",
        )
    }

    @Test
    fun testComparatorExpression_EqualityWithTwoStrings() {
        assertVisit(
            "`\"foo\"` != `\"bar\"`",
            "comparison",
            "val string = \"foo\"",
            "val string2 = \"bar\"",
            "val comparison = string != string2",
        )
    }

    @Test
    fun testComparatorExpression_Inequality() {
        assertVisit(
            "foo <= bar",
            "comparison",
            "val foo = it?.foo",
            "val bar = it?.bar",
            "val comparison = if (foo == null || bar == null) null else foo <= bar",
        )
    }

    @Test
    fun testCurrentNodeInFlattenExpression() {
        assertVisit(
            "foo[]",
            "fooOrEmpty",
            "val foo = it?.foo",
            "val fooOrEmpty = foo ?: listOf()",
        )
    }

    @Test
    fun testExpressionTypeExpression() {
        assertUnimplemented("&foo")
    }

    @Test
    fun testFieldExpression() {
        assertVisit(
            "name",
            "name",
            "val name = it?.name",
        )
    }

    @Test
    fun testFilterProjection() {
        assertVisit(
            "foo[?bar == baz]",
            "fooFiltered",
            "val foo = it?.foo",
            "val fooFiltered = (foo ?: listOf()).filter {",
            "    val bar = it?.bar",
            "    val baz = it?.baz",
            "    val comparison = bar == baz",
            "    comparison",
            "}",
        )
    }

    @Test
    fun testFunctionExpression_Contains() {
        assertVisit(
            "contains(foo, bar)",
            "contains",
            "val foo = it?.foo",
            "val bar = it?.bar",
            "val contains = foo?.contains(bar) ?: false",
        )
    }

    @Test
    fun testFunctionExpression_Length() {
        assertVisit(
            "length(foo)",
            "length",
            "import aws.smithy.kotlin.runtime.util.length",
            "",
            "val foo = it?.foo",
            "val length = foo?.length ?: 0",
        )
    }

    @Test
    fun testIndexExpression() {
        assertUnimplemented("foo[0]")
    }

    @Test
    fun testLiteralExpressions() {
        assertVisit("`\"a string\"`", "string", "val string = \"a string\"")
        assertVisit("`3.14`", "number", "val number = 3.14")
        assertVisit("`false`", "bool", "val bool = false")
        assertVisit("`null`", "null")
        assertUnimplemented("`[]`")
        assertUnimplemented("`{}`")
    }

    @Test
    fun testMultiSelectHashExpression() {
        assertUnimplemented("foo.{bar: baz}")
    }

    @Test
    fun testMultiSelectListExpression() {
        assertVisit(
            "foo[][bar, baz]",
            "projection",
            "val foo = it?.foo",
            "val fooOrEmpty = foo ?: listOf()",
            "val projection = fooOrEmpty.flatMap {",
            "    val multiSelect = listOfNotNull(",
            "        run {",
            "            val bar = it?.bar",
            "            bar",
            "        },",
            "        run {",
            "            val baz = it?.baz",
            "            baz",
            "        },",
            "    )",
            "    multiSelect",
            "}",
        )
    }

    @Test
    fun testNotExpression() {
        assertUnimplemented("!foo")
    }

    @Test
    fun testObjectProjection() {
        assertVisit(
            "foo.*.bar",
            "projection",
            "val foo = it?.foo",
            "val fooValues = foo?.values ?: listOf()",
            "val projection = fooValues.flatMap {",
            "    val bar = it?.bar",
            "    listOfNotNull(bar)",
            "}",
        )
    }

    @Test
    fun testProjectionExpression() {
        assertVisit(
            "foo[].bar",
            "projection",
            "val foo = it?.foo",
            "val fooOrEmpty = foo ?: listOf()",
            "val projection = fooOrEmpty.flatMap {",
            "    val bar = it?.bar",
            "    listOfNotNull(bar)",
            "}",
        )
    }

    @Test
    fun testOrExpression() {
        assertUnimplemented("foo || bar")
    }

    @Test
    fun testSliceExpression() {
        assertUnimplemented("foo[0:1]")
    }

    @Test
    fun testSubExpressions() {
        assertVisit(
            "foo.bar.baz",
            "baz",
            "val foo = it?.foo",
            "val bar = foo?.bar",
            "val baz = bar?.baz",
        )
    }

    private fun assertUnimplemented(path: String) {
        try {
            visit(path)
        } catch (e: CodegenException) {
            e.message shouldEndWith "is unsupported"
            return
        }
        fail("Expected a CodegenException")
    }

    private fun assertVisit(path: String, expectedActualName: String, vararg vals: String) {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val visitor = KotlinJmespathExpressionVisitor(writer)

        val expression = JmespathExpression.parse(path)
        val actualResultName = expression.accept(visitor)

        assertEquals(expectedActualName, actualResultName)

        val codegen = writer.toString().stripCodegenPrefix(TestModelDefault.NAMESPACE)
        assertEquals(vals.joinToString("\n"), codegen)
    }

    private fun visit(path: String): String {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val visitor = KotlinJmespathExpressionVisitor(writer)

        val expression = JmespathExpression.parse(path)
        return expression.accept(visitor)
    }
}
