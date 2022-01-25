/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.utils.CodeWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeWriterTest {

    @Test
    fun itSupportsInlineCodegen() {
        val unit = CodeWriter()

        unit.putFormatter('W', InlineCodeWriterFormatter())

        unit.write("Here is \$W content", { writer: CodeWriter -> writer.write("inline") })

        val actual = unit.toString()

        assertEquals("Here is inline content", actual.trim())
    }

    @Test
    fun itSupportsDiscreteInlineWriters() {
        val unit = CodeWriter()

        unit.putFormatter('W', InlineCodeWriterFormatter())

        val inlineWriter: InlineCodeWriter = { write((1..10).joinToString(",") { "$it" }) }

        unit.write("Here is \$W content", inlineWriter)

        val actual = unit.toString()

        assertEquals("Here is 1,2,3,4,5,6,7,8,9,10 content", actual.trim())
    }

    @Test
    fun itSupportsMultiLineInlineWriters() {
        val unit = CodeWriter()

        unit.putFormatter('W', InlineCodeWriterFormatter())

        val inlineWriter: InlineCodeWriter = { (1..10).forEach { write(it) } }

        unit.write("\$W", inlineWriter)

        val actual = unit.toString()
        val expected = """
            1
            2
            3
            4
            5
            6
            7
            8
            9
            10
        """.trimIndent()
        assertEquals(expected, actual.trim())
    }
}
