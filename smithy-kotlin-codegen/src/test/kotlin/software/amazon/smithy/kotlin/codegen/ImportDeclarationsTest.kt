/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportDeclarationsTest {
    @Test
    fun `it renders imports`() {

        val decls = ImportDeclarations()

        decls.addImport("com.test", "Foo")
        decls.addImport("foo.bar", "Baz", alias = "Quux")

        val statements = decls.toString()
        val expected = "import com.test.Foo\nimport foo.bar.Baz as Quux"
        assertEquals(expected, statements)
    }

    @Test
    fun `it filters duplicates`() {
        val decls = ImportDeclarations()

        decls.addImport("com.test", "Foo")
        decls.addImport("com.test", "Foo")

        val statements = decls.toString()
        val expected = "import com.test.Foo"
        assertEquals(expected, statements)
    }

    @Test
    fun `it renders without alias when symbol matches`() {
        val decls = ImportDeclarations()

        decls.addImport("com.test", "Foo", "Foo")

        val statements = decls.toString()
        val expected = "import com.test.Foo"
        assertEquals(expected, statements)
    }
}
