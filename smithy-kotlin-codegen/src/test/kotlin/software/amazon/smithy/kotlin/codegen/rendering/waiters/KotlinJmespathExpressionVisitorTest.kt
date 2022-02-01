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
        assertUnimplemented(path = "foo && bar")
    }

    @Test
    fun testComparatorExpression_Equality() {
        assertVisit(
            path = "foo == bar",
            expectedActualName = "comparison",
            expectedCodegen = """
                val foo = it?.foo
                val bar = it?.bar
                val comparison = foo == bar
            """.trimIndent(),
        )
    }

    @Test
    fun testComparatorExpression_EqualityWithString() {
        assertVisit(
            path = "`\"foo\"` == bar",
            expectedActualName = "comparison",
            expectedCodegen = """
                val string = "foo"
                val bar = it?.bar
                val comparison = string == bar?.toString()
            """.trimIndent(),
        )

        assertVisit(
            path = "foo == `\"bar\"`",
            expectedActualName = "comparison",
            expectedCodegen = """
                val foo = it?.foo
                val string = "bar"
                val comparison = foo?.toString() == string
            """.trimIndent(),
        )
    }

    @Test
    fun testComparatorExpression_EqualityWithTwoStrings() {
        assertVisit(
            path = "`\"foo\"` != `\"bar\"`",
            expectedActualName = "comparison",
            expectedCodegen = """
                val string = "foo"
                val string2 = "bar"
                val comparison = string != string2
            """.trimIndent(),

        )
    }

    @Test
    fun testComparatorExpression_Inequality() {
        assertVisit(
            path = "foo <= bar",
            expectedActualName = "comparison",
            expectedCodegen = """
                val foo = it?.foo
                val bar = it?.bar
                val comparison = if (foo == null || bar == null) null else foo <= bar
            """.trimIndent(),
        )
    }

    @Test
    fun testCurrentNodeInFlattenExpression() {
        assertVisit(
            path = "foo[]",
            expectedActualName = "fooOrEmpty",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.flattenIfPossible
                
                val foo = it?.foo
                val fooOrEmpty = foo?.flattenIfPossible() ?: listOf()
            """.trimIndent(),
        )
    }

    @Test
    fun testExpressionTypeExpression() {
        assertUnimplemented(path = "&foo")
    }

    @Test
    fun testFieldExpression() {
        assertVisit(
            path = "name",
            expectedActualName = "name",
            expectedCodegen = "val name = it?.name",
        )
    }

    @Test
    fun testFilterProjection() {
        assertVisit(
            path = "foo[?bar == baz]",
            expectedActualName = "fooFiltered",
            expectedCodegen = """
                val foo = it?.foo
                val fooFiltered = (foo ?: listOf()).filter {
                    val bar = it?.bar
                    val baz = it?.baz
                    val comparison = bar == baz
                    comparison == true
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testFunctionExpression_Contains() {
        assertVisit(
            path = "contains(foo, bar)",
            expectedActualName = "contains",
            expectedCodegen = """
                val foo = it?.foo
                val bar = it?.bar
                val contains = foo?.contains(bar) ?: false
            """.trimIndent(),
        )
    }

    @Test
    fun testFunctionExpression_Length() {
        assertVisit(
            path = "length(foo)",
            expectedActualName = "length",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.length
                
                val foo = it?.foo
                val length = foo?.length ?: 0
            """.trimIndent(),
        )
    }

    @Test
    fun testIndexExpression() {
        assertUnimplemented(path = "foo[0]")
    }

    @Test
    fun testLiteralExpressions() {
        assertVisit(path = "`\"foo\"`", expectedActualName = "string", expectedCodegen = "val string = \"foo\"")
        assertVisit(path = "`3.14`", expectedActualName = "number", expectedCodegen = "val number = 3.14")
        assertVisit(path = "`false`", expectedActualName = "bool", expectedCodegen = "val bool = false")
        assertVisit(path = "`null`", expectedActualName = "null", expectedCodegen = "")
        assertUnimplemented(path = "`[]`")
        assertUnimplemented(path = "`{}`")
    }

    @Test
    fun testMultiSelectHashExpression() {
        assertUnimplemented(path = "foo.{bar: baz}")
    }

    @Test
    fun testMultiSelectListExpression() {
        assertVisit(
            path = "foo[][bar, baz]",
            expectedActualName = "projection",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.flattenIfPossible
                
                val foo = it?.foo
                val fooOrEmpty = foo?.flattenIfPossible() ?: listOf()
                val projection = fooOrEmpty.flatMap {
                    val multiSelect = listOfNotNull(
                        run {
                            val bar = it?.bar
                            bar
                        },
                        run {
                            val baz = it?.baz
                            baz
                        },
                    )
                    multiSelect
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testNotExpression() {
        assertUnimplemented(path = "!foo")
    }

    @Test
    fun testObjectProjection() {
        assertVisit(
            path = "foo.*.bar",
            expectedActualName = "projection",
            expectedCodegen = """
                val foo = it?.foo
                val fooValues = foo?.values ?: listOf()
                val projection = fooValues.flatMap {
                    val bar = it?.bar
                    listOfNotNull(bar)
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testProjectionExpression() {
        assertVisit(
            path = "foo[].bar",
            expectedActualName = "projection",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.flattenIfPossible
                
                val foo = it?.foo
                val fooOrEmpty = foo?.flattenIfPossible() ?: listOf()
                val projection = fooOrEmpty.flatMap {
                    val bar = it?.bar
                    listOfNotNull(bar)
                }
            """.trimIndent(),
        )
    }

    @Test
    fun testOrExpression() {
        assertUnimplemented(path = "foo || bar")
    }

    @Test
    fun testSliceExpression() {
        assertUnimplemented(path = "foo[0:1]")
    }

    @Test
    fun testSubExpressions() {
        assertVisit(
            path = "foo.bar.baz",
            expectedActualName = "baz",
            expectedCodegen = """
                val foo = it?.foo
                val bar = foo?.bar
                val baz = bar?.baz
            """.trimIndent(),
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

    private fun assertVisit(path: String, expectedActualName: String, expectedCodegen: String) {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val visitor = KotlinJmespathExpressionVisitor(writer)

        val expression = JmespathExpression.parse(path)
        val actualResultName = expression.accept(visitor)

        assertEquals(expectedActualName, actualResultName)

        val actualCodegen = writer.toString().stripCodegenPrefix(TestModelDefault.NAMESPACE)
        assertEquals(expectedCodegen, actualCodegen)
    }

    private fun visit(path: String): String {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val visitor = KotlinJmespathExpressionVisitor(writer)

        val expression = JmespathExpression.parse(path)
        return expression.accept(visitor)
    }
}
