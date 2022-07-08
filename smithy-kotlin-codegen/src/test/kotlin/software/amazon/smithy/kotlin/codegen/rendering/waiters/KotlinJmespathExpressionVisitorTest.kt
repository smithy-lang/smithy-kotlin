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
        assertVisit(
            path = "foo && bar",
            expectedActualName = "and",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.truthiness
                
                val foo = it?.foo
                val fooTruthiness = truthiness(foo)
                val bar = it?.bar
                val and = if (fooTruthiness) bar else foo
            """.trimIndent(),
        )
    }

    @Test
    fun testComparatorExpression_Equality() {
        assertVisit(
            path = "foo == bar",
            expectedActualName = "comparison",
            expectedCodegen = """
                val foo = it?.foo
                val bar = it?.bar
                val comparison = if (foo == null || bar == null) null else foo.compareTo(bar) == 0
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
                val comparison = if (string == null || bar == null) null else string.compareTo(bar.toString()) == 0
            """.trimIndent(),
        )

        assertVisit(
            path = "foo == `\"bar\"`",
            expectedActualName = "comparison",
            expectedCodegen = """
                val foo = it?.foo
                val string = "bar"
                val comparison = if (foo == null || string == null) null else foo.toString().compareTo(string) == 0
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
                val comparison = if (string == null || string2 == null) null else string.compareTo(string2) != 0
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
                val comparison = if (foo == null || bar == null) null else foo.compareTo(bar) <= 0
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
                    val comparison = if (bar == null || baz == null) null else bar.compareTo(baz) == 0
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
        assertVisit(
            path = "!foo",
            expectedActualName = "notFoo",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.truthiness
                
                val foo = it?.foo
                val fooTruthiness = truthiness(foo)
                val notFoo = !fooTruthiness
            """.trimIndent(),
        )
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
    fun testOrExpression() {
        assertVisit(
            path = "foo || bar",
            expectedActualName = "or",
            expectedCodegen = """
                import aws.smithy.kotlin.runtime.util.truthiness
                
                val foo = it?.foo
                val fooTruthiness = truthiness(foo)
                val bar = it?.bar
                val or = if (fooTruthiness) foo else bar
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
